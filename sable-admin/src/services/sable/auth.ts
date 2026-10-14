/**
 * 认证接口 —— 对应后端 `AuthController`（`/auth`）。
 *
 * <p>请求路径统一带 `/api` 前缀，由 `config/proxy.ts` 在开发环境剥掉、生产由 Nginx 处理。
 * 后端本身的路径是裸的（`/auth/login`）。</p>
 */
import { request } from '@umijs/max';

const BASE = '/api/auth';

/**
 * 登录。`POST /auth/login`
 *
 * <p>失败时后端返回的是 **HTTP 200 + 非 0 code**（业务错误）：
 * - `40001` 用户名或密码错误（用户不存在与密码错共用此码，后端刻意不区分以免枚举账号）；
 * - `40006` 账号已锁定（连续失败超限）；
 * - `40004` 账号开启了 MFA —— 当前后端 TOTP 未实现，会直接拒绝而不是返回 `mfaRequired`。</p>
 */
export async function login(
  body: SableAPI.LoginParams,
  options?: Record<string, any>,
) {
  return request<SableAPI.R<SableAPI.LoginResult>>(`${BASE}/login`, {
    method: 'POST',
    data: body,
    ...(options ?? {}),
  });
}

/**
 * 用刷新令牌换取新的一对令牌。`POST /auth/refresh`
 *
 * <p>每次调用都会签发**一对全新令牌**：返回的 `refreshToken` 与传入的不是同一个，
 * 因此调用方应整体覆盖本地存储（见 `utils/token.ts` 的 `saveTokens`）。</p>
 *
 * <p>⚠️ 但**旧的那个不会被吊销**（后端 `refresh()` 只校验、不吊销传入的刷新令牌），
 * 它在自己的有效期内仍可用。刷新失败的真正原因是：刷新令牌过期、已被登出吊销，
 * 或账号被停用/锁定。</p>
 *
 * <p>此接口在白名单内，因此**不需要**带 `Authorization` 头 —— 事实上调用它的时候
 * 访问令牌多半已经过期了。</p>
 */
export async function refresh(
  refreshToken: string,
  options?: Record<string, any>,
) {
  return request<SableAPI.R<SableAPI.LoginResult>>(`${BASE}/refresh`, {
    method: 'POST',
    data: { refreshToken },
    ...(options ?? {}),
  });
}

/**
 * 登出。`POST /auth/logout`
 *
 * <p>访问令牌从 `Authorization` 头取；刷新令牌不在头里，只能放进请求体 ——
 * 不传的话只吊销访问令牌，刷新令牌在有效期内仍可换新令牌（做不到"彻底登出"）。</p>
 *
 * <p>该接口在免认证白名单内：令牌缺失/过期/非法时同样返回成功，不暴露令牌状态。
 * 因此调用方可以先清本地存储、再调它，顺序不影响结果。</p>
 */
export async function logout(
  refreshToken?: string | null,
  options?: Record<string, any>,
) {
  return request<SableAPI.R<void>>(`${BASE}/logout`, {
    method: 'POST',
    data: refreshToken ? { refreshToken } : {},
    ...(options ?? {}),
  });
}

/** 修改密码。`PUT /auth/password` —— 用户身份取自 `Authorization` 头。 */
export async function changePassword(
  body: SableAPI.PasswordChangeParams,
  options?: Record<string, any>,
) {
  return request<SableAPI.R<void>>(`${BASE}/password`, {
    method: 'PUT',
    data: body,
    ...(options ?? {}),
  });
}
