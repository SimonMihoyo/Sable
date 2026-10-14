package me.kirara.sable.system.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import me.kirara.sable.system.entity.Tenant;
import me.kirara.sable.system.mapper.TenantMapper;
import me.kirara.sable.system.service.TenantSchemaProvisioner;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("建租户：元数据与 schema 的编排")
class TenantServiceImplTest {

    private TenantMapper tenantMapper;
    private TenantSchemaProvisioner provisioner;
    private ObjectProvider<TenantSchemaProvisioner> provisionerProvider;
    private TenantServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // 纯单测里 MyBatis-Plus 拿不到 lambda 缓存，需手工注册表信息
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Tenant.class);

        tenantMapper = mock(TenantMapper.class);
        provisioner = mock(TenantSchemaProvisioner.class);
        provisionerProvider = mock(ObjectProvider.class);

        service = new TenantServiceImpl(provisionerProvider);
        ReflectionTestUtils.setField(service, "baseMapper", tenantMapper);
    }

    /** 模拟数据库生成主键：插入后把 ID 回填到实体上。 */
    private void stubInsertReturningId(long id) {
        when(tenantMapper.insert(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant saved = invocation.getArgument(0);
            saved.setId(id);
            return 1;
        });
    }

    @Test
    @DisplayName("成功：先落元数据，再按生成的 ID 供给 schema")
    void provisionsAfterInsert() {
        stubInsertReturningId(9L);
        when(provisionerProvider.getIfAvailable()).thenReturn(provisioner);

        Tenant tenant = new Tenant();
        tenant.setCode("acme");
        Tenant created = service.create(tenant);

        assertSame(tenant, created);
        assertEquals(9L, created.getId());
        verify(tenantMapper).insert(tenant);
        // 供给用的是**数据库生成后**的 ID —— schema 名 tenant_9 依赖它
        verify(provisioner).provision(9L);
    }

    @Test
    @DisplayName("供给失败：删掉刚插入的租户行再抛错，不留“元数据在、业务表永远没有”的半成品")
    void rollsBackRowWhenProvisioningFails() {
        stubInsertReturningId(9L);
        when(provisionerProvider.getIfAvailable()).thenReturn(provisioner);
        IllegalStateException failure = new IllegalStateException("建 schema 失败");
        org.mockito.Mockito.doThrow(failure).when(provisioner).provision(9L);

        Tenant tenant = new Tenant();
        tenant.setCode("acme");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> service.create(tenant));
        assertSame(failure, thrown);
        verify(tenantMapper).deleteById(9L);
        // 补偿是物理删除（本项目未启用 @TableLogic），否则 code 的唯一约束会被软删行一直占着
    }

    @Test
    @DisplayName("没有存储侧实现时只落元数据并保留行 —— 供只加载库模块的测试使用")
    void keepsRowWhenProvisionerAbsent() {
        stubInsertReturningId(9L);
        when(provisionerProvider.getIfAvailable()).thenReturn(null);

        service.create(new Tenant());

        verify(tenantMapper).insert(any(Tenant.class));
        verify(tenantMapper, never()).deleteById(any(java.io.Serializable.class));
    }
}
