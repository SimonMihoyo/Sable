package me.kirara.sable.system.service;

import java.time.Instant;

/**
 * 令牌吊销服务 — 维护「已失效令牌」清单。
 *
 * <p>本系统使用无状态 JWT，令牌一旦签发便自带有效期，服务端无法单方面「收回」。
 * 要让登出真正生效，只能在服务端保留一份吊销记录，由认证入口逐次比对。</p>
 *
 * <p><b>为什么用 jti 而不是 (user_id, iat)：</b>{@code iat} 只有秒级精度，
 * 同一秒内签发的两枚令牌无法区分，会导致「登出一枚、连带吊销另一枚」的误伤。</p>
 *
 * <p><b>已知代价：</b>每个携带令牌的请求都会多一次查询。当前实现直连数据库，
 * 单机 MVP 阶段可接受；后续若成为热点，可在本接口下换成本地缓存或 Redis 而不影响调用方。</p>
 */
public interface TokenRevocationService {

    /** 吊销原因：主动登出。 */
    String REASON_LOGOUT = "LOGOUT";

    /** 吊销原因：管理员强制下线。 */
    String REASON_FORCE_OFFLINE = "FORCE_OFFLINE";

    /** 吊销原因：修改密码后旧令牌作废。 */
    String REASON_PASSWORD_CHANGED = "PASSWORD_CHANGED";

    /**
     * 吊销一枚令牌。
     *
     * <p>幂等：同一 {@code jti} 重复吊销不报错。已过期或缺失 jti 的令牌直接忽略
     * ——它们本来就无法通过校验，记入表里只是徒增数据。</p>
     *
     * @param jti       令牌唯一标识，为空则不处理
     * @param userId    令牌持有者，可为空
     * @param tokenType access / refresh
     * @param expiresAt 令牌自身过期时间，为空或已过期则不处理
     * @param reason    吊销原因，见本接口常量
     */
    void revoke(String jti, Long userId, String tokenType, Instant expiresAt, String reason);

    /**
     * 判断令牌是否已被吊销。
     *
     * @param jti 令牌唯一标识，为空时返回 {@code false}
     * @return 已吊销返回 {@code true}
     */
    boolean isRevoked(String jti);

    /**
     * 清理已自然过期的吊销记录。
     *
     * @return 删除的行数
     */
    int purgeExpired();
}
