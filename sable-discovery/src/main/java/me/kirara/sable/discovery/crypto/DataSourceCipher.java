package me.kirara.sable.discovery.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 数据源口令加解密 —— AES-256-GCM，密钥由启动模块从配置注入。
 *
 * <p><b>为什么在应用侧加密而不是走库里的 pgcrypto：</b>库里虽然有
 * {@code fn_encrypt_data} / {@code fn_decrypt_data}，但用它们就得把密钥拼进 SQL
 * （{@code SELECT fn_encrypt_data(?, '密钥') }），而 SQL 语句会被 MyBatis 的 SQL 日志
 * 原样打出来 —— 口令与密钥同时进日志，是合规产品最不该出现的形态。应用侧加解密
 * 让密钥只存在于进程内存里，SQL 参数只剩密文。</p>
 *
 * <p><b>密文格式</b>：{@code [1 字节版本][12 字节 IV][GCM 密文 + 16 字节认证标签]}。
 * 版本字节是给将来换算法 / 换密钥留的口子：读到不认识的版本时应当明确拒绝，
 * 而不是拿当前密钥硬解出一串乱码。</p>
 *
 * <p>GCM 自带完整性校验，因此密文被篡改（或密钥被换掉）时解密会抛异常，
 * 不会静默返回错误的明文。每次加密都用新的随机 IV —— GCM 下 IV 复用会直接
 * 毁掉机密性，这不是可以省的性能点。</p>
 */
public final class DataSourceCipher {

    /** AES-256 的密钥长度（字节）。 */
    public static final int KEY_BYTES = 32;

    /** 密文格式版本，当前只有 1。 */
    private static final byte FORMAT_VERSION = 1;

    /** GCM 推荐的 IV 长度（96 位）。 */
    private static final int IV_BYTES = 12;

    /** 认证标签长度（128 位）。 */
    private static final int TAG_BITS = 128;

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey key;

    /**
     * 用原始密钥字节构造。
     *
     * @param rawKey 32 字节（AES-256）密钥
     * @throws IllegalArgumentException 长度不是 32 字节
     */
    public DataSourceCipher(byte[] rawKey) {
        if (rawKey == null || rawKey.length != KEY_BYTES) {
            throw new IllegalArgumentException("数据源加密密钥长度必须是 " + KEY_BYTES + " 字节（AES-256）");
        }
        this.key = new SecretKeySpec(rawKey, "AES");
    }

    /**
     * 用 Base64 编码的密钥构造。
     *
     * <p>配置里只放 Base64 文本：环境变量里塞二进制不现实，而且异常信息里
     * 打印出来的也只是一串 Base64，不会把密钥本身原样带进日志。</p>
     *
     * @param base64Key Base64 编码的 32 字节密钥
     * @throws IllegalArgumentException 未配置、不是合法 Base64 或长度不对
     */
    public static DataSourceCipher fromBase64(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalArgumentException(
                    "数据源加密密钥未配置：请设置 sable.discovery.crypto-key"
                            + "（环境变量 SABLE_DISCOVERY_CRYPTO_KEY），值为 Base64 编码的 32 字节密钥");
        }
        byte[] rawKey;
        try {
            rawKey = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("数据源加密密钥不是合法的 Base64 文本", e);
        }
        return new DataSourceCipher(rawKey);
    }

    /**
     * 加密明文口令。
     *
     * @param plaintext 明文，不允许为 {@code null}
     * @return 可直接写入 {@code data_source.password_enc}（BYTEA）的密文
     */
    public byte[] encrypt(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("待加密的口令不能为空");
        }
        byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(1 + IV_BYTES + ciphertext.length)
                    .put(FORMAT_VERSION)
                    .put(iv)
                    .put(ciphertext)
                    .array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("数据源口令加密失败", e);
        }
    }

    /**
     * 解密密文。
     *
     * @param payload {@link #encrypt(String)} 产出的密文
     * @return 明文口令
     * @throws IllegalStateException 密文格式非法、版本不认识，或认证标签校验失败
     *                               （密文被改过，或者密钥与加密时不是同一把）
     */
    public String decrypt(byte[] payload) {
        if (payload == null || payload.length <= 1 + IV_BYTES) {
            throw new IllegalStateException("数据源口令密文格式非法");
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        byte version = buffer.get();
        if (version != FORMAT_VERSION) {
            throw new IllegalStateException("不支持的数据源口令密文版本: " + version);
        }
        byte[] iv = new byte[IV_BYTES];
        buffer.get(iv);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // 不把密文或密钥写进异常消息：它会被 GlobalExceptionHandler 记进日志
            throw new IllegalStateException("数据源口令解密失败（密文损坏或密钥已更换）", e);
        }
    }
}
