package me.kirara.sable.discovery;

import me.kirara.sable.discovery.crypto.DataSourceCipher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DataSourceCipher} 的单元测试。
 *
 * <p>只碰密码学，不连库 —— 加密是否真能写进 {@code password_enc}（BYTEA）仍需按
 * AGENTS §5.4 打一次真实接口，那一步单测替不了。</p>
 */
class DataSourceCipherTest {

    /** 32 字节测试密钥（不是生产密钥，也不是 dev profile 里那一把）。 */
    private static final byte[] KEY = "unit-test-key-32-bytes-long!!!!!".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("加密后能解回原文")
    void roundTrip() {
        DataSourceCipher cipher = new DataSourceCipher(KEY);
        String plaintext = "P@ssw0rd-中文-123456";

        assertEquals(plaintext, cipher.decrypt(cipher.encrypt(plaintext)));
    }

    @Test
    @DisplayName("密文里看不到明文，且每次加密结果不同（随机 IV）")
    void ciphertextHidesPlaintextAndVaries() {
        DataSourceCipher cipher = new DataSourceCipher(KEY);
        byte[] first = cipher.encrypt("Sable@2026");
        byte[] second = cipher.encrypt("Sable@2026");

        assertFalse(new String(first, StandardCharsets.UTF_8).contains("Sable@2026"));
        assertNotEquals(Arrays.toString(first), Arrays.toString(second), "两次加密不应产生相同密文");
        assertEquals("Sable@2026", cipher.decrypt(first));
        assertEquals("Sable@2026", cipher.decrypt(second));
    }

    @Test
    @DisplayName("换一把密钥解不开（GCM 认证失败）")
    void wrongKeyFails() {
        DataSourceCipher writer = new DataSourceCipher(KEY);
        DataSourceCipher reader = new DataSourceCipher(
                "another-test-key-32-bytes-long!!".getBytes(StandardCharsets.UTF_8));

        byte[] payload = writer.encrypt("Sable@2026");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> reader.decrypt(payload));
        assertEquals("数据源口令解密失败（密文损坏或密钥已更换）", ex.getMessage());
    }

    @Test
    @DisplayName("密文被篡改一个字节就解不开")
    void tamperedCiphertextFails() {
        DataSourceCipher cipher = new DataSourceCipher(KEY);
        byte[] payload = cipher.encrypt("Sable@2026");
        byte[] tampered = payload.clone();
        tampered[tampered.length - 1] ^= 0x01;

        assertThrows(IllegalStateException.class, () -> cipher.decrypt(tampered));
    }

    @Test
    @DisplayName("密钥长度不是 32 字节直接拒绝")
    void rejectsWrongKeyLength() {
        assertThrows(IllegalArgumentException.class, () -> new DataSourceCipher(new byte[16]));
        assertThrows(IllegalArgumentException.class, () -> new DataSourceCipher(null));
    }

    @Test
    @DisplayName("Base64 密钥解析：合法值可用，空值与坏值给出可读报错")
    void base64KeyHandling() {
        DataSourceCipher cipher = DataSourceCipher.fromBase64("c2FibGUtZGV2LWRpc2NvdmVyeS1rZXktMzItYnl0ZXM=");
        assertEquals("Sable@2026", cipher.decrypt(cipher.encrypt("Sable@2026")));

        IllegalArgumentException blank = assertThrows(IllegalArgumentException.class,
                () -> DataSourceCipher.fromBase64("  "));
        assertTrue(blank.getMessage().contains("SABLE_DISCOVERY_CRYPTO_KEY"));

        assertThrows(IllegalArgumentException.class, () -> DataSourceCipher.fromBase64("not-base64!!"));
        assertThrows(IllegalArgumentException.class, () -> DataSourceCipher.fromBase64("c2hvcnQ="));
    }

    @Test
    @DisplayName("加解密不修改入参")
    void doesNotMutateInput() {
        DataSourceCipher cipher = new DataSourceCipher(KEY);
        byte[] payload = cipher.encrypt("Sable@2026");
        byte[] before = payload.clone();

        cipher.decrypt(payload);

        assertArrayEquals(before, payload);
    }
}
