/**
 * 角色接口 —— 对应后端 `RoleController`（`/system/roles`）。
 *
 * ⚠️ 角色是**租户内的**资产：`role.tenant_id` 非空，唯一约束建在 `(tenant_id, name)` 上，
 * 不同租户可以有同名角色。创建时租户取自访问令牌的 `tid`，前端不需要（也不该）传。
 *
 * ⚠️ 已知后端缺口（会影响本页面的正确性，实现前请先看 `AGENTS.md`）：
 * - `GET /system/roles` **没有按租户过滤**，会把所有租户的角色一起返回；
 * - `PUT`/`DELETE`/`PUT permissions` **不校验角色归属**，可以跨租户改到别人的角色；
 * - 无权限码字典，`permissions` 是自由字符串。
 * 也就是说下面的界面在当前版本只是"能用"，还不能当作权限边界的实现。
 */
import { request } from '@umijs/max';

const BASE = '/api/system/roles';

/** 创建/更新角色的入参，对应后端 `RoleSaveRequest`。 */
export type SaveRoleBody = {
  /** 角色标识，如 `COMPLIANCE_OFFICER`。租户内唯一。 */
  name: string;
  displayName?: string;
  description?: string;
};

/**
 * 角色列表。`GET /system/roles`
 *
 * <p><b>返回的是裸数组，不是分页结构</b> —— 后端这个接口没有分页参数。
 * 租户角色数量可控，暂不构成问题。</p>
 */
export async function listRoles(options?: Record<string, any>) {
  return request<SableAPI.R<SableAPI.Role[]>>(BASE, {
    method: 'GET',
    ...(options ?? {}),
  });
}

/** 创建角色。`POST /system/roles` —— `tenantId` 由后端从令牌取，`isSystem` 恒为 false。 */
export async function createRole(
  body: SaveRoleBody,
  options?: Record<string, any>,
) {
  return request<SableAPI.R<SableAPI.Role>>(BASE, {
    method: 'POST',
    data: body,
    ...(options ?? {}),
  });
}

/** 更新角色。`PUT /system/roles/{id}` */
export async function updateRole(
  id: number,
  body: SaveRoleBody,
  options?: Record<string, any>,
) {
  return request<SableAPI.R<void>>(`${BASE}/${id}`, {
    method: 'PUT',
    data: body,
    ...(options ?? {}),
  });
}

/**
 * 删除角色。`DELETE /system/roles/{id}`
 *
 * <p>后端**尚未实现**"系统预置角色不可删除"的校验（代码里是 TODO），
 * 所以前端把 `isSystem` 的角色按钮禁掉只是界面约束，挡不住直接调接口。</p>
 */
export async function removeRole(id: number, options?: Record<string, any>) {
  return request<SableAPI.R<void>>(`${BASE}/${id}`, {
    method: 'DELETE',
    ...(options ?? {}),
  });
}

/**
 * 覆盖式设置角色权限。`PUT /system/roles/{id}/permissions`
 *
 * <p>入参是 `{ permissions: string[] }`（不是裸数组，与"分配用户角色"那个接口不同）。
 * 后端会用 `JsonArrays.toJsonArray` 序列化后写进 JSONB 列 —— 直接拼字符串会被库拒绝。</p>
 */
export async function setRolePermissions(
  id: number,
  permissions: string[],
  options?: Record<string, any>,
) {
  return request<SableAPI.R<void>>(`${BASE}/${id}/permissions`, {
    method: 'PUT',
    data: { permissions },
    ...(options ?? {}),
  });
}
