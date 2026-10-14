/**
 * 用户与权限接口 —— 对应后端 `UserController`（`/system/users`）。
 *
 * ⚠️ 这些接口多数**需要租户上下文**：后端按访问令牌里的 `tid` 决定往哪个 schema 写。
 * 令牌没带租户时返回 `40104`（缺少租户上下文），这不是"没权限"，而是登录会话本身
 * 不完整 —— 前端收到它应当直接回登录页，而不是提示用户"无权限"。
 *
 * 另外，用户与角色是**租户内**的资源：同一个人在不同租户可以是不同角色。
 */
import { request } from '@umijs/max';

const BASE = '/api/system/users';

/** 创建用户的入参，对应后端 `UserCreateRequest`。 */
export type CreateUserBody = {
  username: string;
  password: string;
  email?: string;
  phone?: string;
  displayName?: string;
  /** 后端会写进 `tenant_user.department`，但当前**只存不读**（数据范围权限尚未实现）。 */
  department?: string;
  roleIds?: number[];
};

/** 更新用户的入参，对应后端 `UserUpdateRequest`。用户名与密码不在此接口修改。 */
export type UpdateUserBody = {
  email?: string;
  phone?: string;
  displayName?: string;
  department?: string;
};

/**
 * 当前登录用户。`GET /system/users/me`
 *
 * <p>这是登录后**第二个必须调的接口** —— 登录响应里没有用户信息（见 `typings.d.ts`
 * 对 `LoginResult` 的说明）。返回体里带着 `roles` 与 `permissions`，前端的菜单与
 * 路由守卫都依赖它。</p>
 */
export async function currentUser(options?: Record<string, any>) {
  return request<SableAPI.R<SableAPI.CurrentUser>>(`${BASE}/me`, {
    method: 'GET',
    ...(options ?? {}),
  });
}

/**
 * 用户列表。`GET /system/users`
 *
 * <p>后端只支持 `page`/`size`/`keyword`/`status`，**没有任何排序参数**（`sort` 是被
 * 文档承诺过但代码里不存在的），所以前端不要传排序字段，会静默失效。</p>
 */
export async function listUsers(
  params: SableAPI.PageQuery & { status?: string },
  options?: Record<string, any>,
) {
  return request<SableAPI.R<SableAPI.PageResult<SableAPI.SysUser>>>(BASE, {
    method: 'GET',
    params: { ...params },
    ...(options ?? {}),
  });
}

/** 创建用户。`POST /system/users` —— 租户取自访问令牌。 */
export async function createUser(
  body: CreateUserBody,
  options?: Record<string, any>,
) {
  return request<SableAPI.R<SableAPI.SysUser>>(BASE, {
    method: 'POST',
    data: body,
    ...(options ?? {}),
  });
}

/** 更新用户资料。`PUT /system/users/{id}` */
export async function updateUser(
  id: number,
  body: UpdateUserBody,
  options?: Record<string, any>,
) {
  return request<SableAPI.R<void>>(`${BASE}/${id}`, {
    method: 'PUT',
    data: body,
    ...(options ?? {}),
  });
}

/** 删除用户（软删除）。`DELETE /system/users/{id}` */
export async function removeUser(id: number, options?: Record<string, any>) {
  return request<SableAPI.R<void>>(`${BASE}/${id}`, {
    method: 'DELETE',
    ...(options ?? {}),
  });
}

/**
 * 覆盖式分配角色。`PUT /system/users/{id}/roles`
 *
 * <p>入参是**裸数组**（角色 ID 列表），不是对象 —— 传空数组即收回该用户全部角色。
 * 语义是"最终状态"而不是"增量追加"。</p>
 */
export async function assignRoles(
  id: number,
  roleIds: number[],
  options?: Record<string, any>,
) {
  return request<SableAPI.R<void>>(`${BASE}/${id}/roles`, {
    method: 'PUT',
    data: roleIds,
    ...(options ?? {}),
  });
}

/** 变更用户状态。`PUT /system/users/{id}/status` —— 取值为 `ACTIVE` / `LOCKED` / `DISABLED`。 */
export async function updateUserStatus(
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
