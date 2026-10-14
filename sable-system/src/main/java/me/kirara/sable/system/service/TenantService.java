package me.kirara.sable.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import me.kirara.sable.system.entity.Tenant;

/** Tenant 业务服务。 */
public interface TenantService extends IService<Tenant> {

    /**
     * 建租户：落一行元数据，并确保它的数据库 schema 与业务表就绪。
     *
     * <p>与直接 {@code save(tenant)} 的区别就在后半句 —— 只写元数据会得到一个
     * "存在但一查业务表就报 relation 不存在"的租户。schema 建不起来时会把刚插入的行删掉再抛错，
     * 不留半成品。</p>
     *
     * @param tenant 待建的租户，{@code id} 由数据库生成后回填
     * @return 同一个对象，携带生成的 id
     */
    Tenant create(Tenant tenant);
}
