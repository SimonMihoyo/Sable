package me.kirara.sable.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import me.kirara.sable.system.dto.UserCreateRequest;
import me.kirara.sable.system.entity.SysUser;

/** SysUser 业务服务。 */
public interface SysUserService extends IService<SysUser> {

    /**
     * 创建用户。
     *
     * <p><b>密码哈希在服务内完成</b>：调用方传入的是明文请求体，不允许自行拼装实体后调
     * {@code save()} —— 那样迟早会有人把明文直接写进 {@code password_hash} 列。</p>
     *
     * @param request 创建请求（用户名、明文密码必填）
     * @return 已落库的用户实体，{@code id} 已回填，{@code passwordHash} 为 BCrypt 哈希
     */
    SysUser createUser(UserCreateRequest request);
}
