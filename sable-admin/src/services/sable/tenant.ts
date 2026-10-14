/**
 * 租户接口 —— 对应后端 `TenantController`（`/system/tenants`）。
 *
 * ⚠️ 注意与"当前登录用户所属租户"区分：这些接口管理的是**平台上的租户清单**，
 * 属运维/平台管理范围，因此它们**不需要租户上下文**（不按 `tid` 路由到某个 schema，
 * 读写的是 `public` 里的 `tenant` 表）。这与 `/system/users`、`/system/roles` 正相反。
 */
import { request } from '@umijs/max';

const BASE = '/api/system/tenants';

/** 创建租户的入参，对应后端 `TenantCreateRequest`。 */
export type CreateTenantBody = {
  /** 租户名称，如「某某集团」。 */
  name: string;
  /** 租户编码。唯一，用于标识与排障，创建后**不可修改**。 */
  code: string;
  /** 套餐类型，后端默认 `BASIC`。 */
  planType?: string;
  contactName?: string;
  contactEmail?: string;
  contactPhone?: string;
};

/**
 * 更新租户的入参，对应后端 `TenantUpdateRequest`。
 *
 * <p>**没有 `code`** —— 后端更新时不接受编码变更（编码是对外标识，改了会牵连排障口径）。</p>
 */
export type UpdateTenantBody = {
  name?: string;
  planType?: string;
  contactName?: string;
  contactEmail?: string;
  contactPhone?: string;
};

/**
 * 租户列表。`GET /system/tenants`
 *
 * <p>只支持 `page`/`size`/`keyword`（按名称模糊匹配）与 `status`，无排序参数。</p>
 */
export async function listTenants(
  params: SableAPI.PageQuery & { status?: string },
  options?: Record<string, any>,
) {
  return request<SableAPI.R<SableAPI.PageResult<SableAPI.Tenant>>>(BASE, {
    method: 'GET',
    params: { ...params },
    ...(options ?? {}),
  });
}

/** 租户详情。`GET /system/tenants/{id}` —— 不存在或已删除时报 `40101`。 */
export async function getTenant(id: number, options?: Record<string, any>) {
  return request<SableAPI.R<SableAPI.Tenant>>(`${BASE}/${id}`, {
    method: 'GET',
    ...(options ?? {}),
  });
}

/**
 * 创建租户。`POST /system/tenants`
 *
 * <p>这个接口不止是插一行元数据：后端还会为它建 `tenant_{id}` schema 并跑一遍
 * 租户级迁移（12 张业务表）。因此**耗时明显长于普通写入**，前端超时不要设得太短。</p>
 */
export async function createTenant(
  body: CreateTenantBody,
  options?: Record<string, any>,
) {
  return request<SableAPI.R<SableAPI.Tenant>>(BASE, {
    method: 'POST',
    data: body,
    ...(options ?? {}),
  });
}

/** 更新租户。`PUT /system/tenants/{id}` */
export async function updateTenant(
  id: number,
  body: UpdateTenantBody,
  options?: Record<string, any>,
) {
  return request<SableAPI.R<void>>(`${BASE}/${id}`, {
    method: 'PUT',
    data: body,
    ...(options ?? {}),
  });
}

/** 变更租户状态。`PUT /system/tenants/{id}/status` —— 取值 `ACTIVE` / `DISABLED`。 */
export async function updateTenantStatus(
  id: number,
  status: string,
  options?: Record<string, any>,
) {
  return request<SableAPI.R<void>>(`${BASE}/${id}/status`, {
    method: 'PUT',
    data: { status },
    ...(options ?? {}),
  });
}

/**
 * 删除租户。`DELETE /system/tenants/{id}`
 *
 * <p>后端是**软删除**（置 `deleted_at`），但并不会回收 `tenant_{id}` schema ——
 * 数据仍在库里。所以这个操作不是"释放空间"，只是让它从清单里消失。</p>
 */
export async function removeTenant(id: number, options?: Record<string, any>) {
  return request<SableAPI.R<void>>(`${BASE}/${id}`, {
    method: 'DELETE',
    ...(options ?? {}),
  });
}
