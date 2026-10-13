package me.kirara.sable.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import me.kirara.sable.system.entity.TokenRevocation;
import me.kirara.sable.system.mapper.TokenRevocationMapper;
import me.kirara.sable.system.service.TokenRevocationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 令牌吊销服务实现 — 记录落在 {@code public.token_revocation}。
 *
 * <p>时间换算：令牌给的是 {@link Instant}，库列是不带时区的 {@code TIMESTAMP}，
 * 统一按 JVM 默认时区转换。写入与读取用同一规则，比较结果自洽。</p>
 */
@Service
public class TokenRevocationServiceImpl implements TokenRevocationService {

    private static final Logger log = LoggerFactory.getLogger(TokenRevocationServiceImpl.class);

    private final TokenRevocationMapper tokenRevocationMapper;

    public TokenRevocationServiceImpl(TokenRevocationMapper tokenRevocationMapper) {
        this.tokenRevocationMapper = tokenRevocationMapper;
    }

    @Override
    public void revoke(String jti, Long userId, String tokenType, Instant expiresAt, String reason) {
        if (jti == null || jti.isBlank()) {
            log.debug("跳过吊销：令牌缺少 jti");
            return;
        }
        if (expiresAt == null || !expiresAt.isAfter(Instant.now())) {
            // 已过期的令牌本来就通不过签名校验，无需占用吊销表
            log.debug("跳过吊销：令牌已过期或缺少 exp jti={}", jti);
            return;
        }
        if (isRevoked(jti)) {
            return;
        }

        TokenRevocation entity = new TokenRevocation();
        entity.setJti(jti);
        entity.setUserId(userId);
        entity.setTokenType(tokenType);
        entity.setExpiresAt(LocalDateTime.ofInstant(expiresAt, ZoneId.systemDefault()));
        entity.setRevokedAt(LocalDateTime.now());
        entity.setReason(reason);

        try {
            tokenRevocationMapper.insert(entity);
            log.info("令牌已吊销 userId={} type={} reason={} jti={}", userId, tokenType, reason, jti);
        } catch (DuplicateKeyException e) {
            // jti 唯一约束兜底：并发重复登出时后到的这次插入失败，结果与幂等一致
            log.debug("令牌已处于吊销状态，忽略重复写入 jti={}", jti);
        }
    }

    @Override
    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        return tokenRevocationMapper.selectCount(Wrappers.<TokenRevocation>lambdaQuery()
                .eq(TokenRevocation::getJti, jti)) > 0;
    }

    @Override
    public int purgeExpired() {
        int deleted = tokenRevocationMapper.delete(Wrappers.<TokenRevocation>lambdaQuery()
                .lt(TokenRevocation::getExpiresAt, LocalDateTime.now()));
        if (deleted > 0) {
            log.info("清理过期令牌吊销记录 {} 条", deleted);
        }
        return deleted;
    }
}
