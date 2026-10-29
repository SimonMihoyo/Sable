package me.kirara.sable.config;

import me.kirara.sable.discovery.crypto.DataSourceCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 数据源口令加密的装配 —— 把配置里的密钥交给 {@link DataSourceCipher}。
 *
 * <p><b>为什么密钥放在启动模块装配：</b>加解密本身是 discovery 模块的事，但"密钥从哪来"
 * 属于部署配置，只该在启动模块出现。库模块拿不到 {@code spring-boot-configuration-processor}
 * 也没什么可绑定的 —— 它只需要一个构造好的 {@code DataSourceCipher}。</p>
 *
 * <p><b>为什么缺密钥时直接启动失败：</b>没有密钥就写不了 {@code password_enc}（该列 NOT NULL），
 * 数据源新增会全部 500。与其让服务带着一个"看起来正常、一用就坏"的配置跑起来，
 * 不如在启动时把配置问题暴露在日志里。生产必须通过环境变量
 * {@code SABLE_DISCOVERY_CRYPTO_KEY} 注入，dev profile 有一把仅供本地的默认值。</p>
 */
@Configuration
public class DiscoveryCryptoConfig {

    /**
     * 数据源口令加解密器。
     *
     * @param base64Key Base64 编码的 32 字节（AES-256）密钥，绑定 {@code sable.discovery.crypto-key}
     * @return 加解密器；密钥缺失或非法时构造阶段即抛异常，容器启动失败
     */
    @Bean
    public DataSourceCipher dataSourceCipher(
            @Value("${sable.discovery.crypto-key:}") String base64Key) {
        return DataSourceCipher.fromBase64(base64Key);
    }
}
