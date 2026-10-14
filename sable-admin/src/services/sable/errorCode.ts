/**
 * 后端错误码 —— 与 `sable-common` 的 `ErrorCode` 枚举保持一致。
 *
 * <p>只列**前端需要判分支**的那些：成功、认证类（决定要不要刷新令牌 / 跳登录）、
 * 租户类（决定提示语）、权限类。业务模块的码（数据源、分类、评估等）等对应页面
 * 落地时再补，不必为了"完整"先把用不到的搬过来 —— 那样只会让这份表与后端各自漂移。</p>
 *
 * <p>数值来源是后端枚举，不是 API 文档 —— 文档的错误码表此前漂移严重（把 40004 写成
 * "无权限访问"、把内部错误写成 50001），已在本轮校准，但**以代码为准**。</p>
 */
export const ErrorCode = {
  /** 业务成功。注意成功码是 0，不是 HTTP 的 200 —— 两者分属不同层。 */
  SUCCESS: 0,
  /** 未预期的内部错误（后端兜底码）。 */
  INTERNAL_ERROR: 1,
  /** 参数校验失败。 */
  PARAM_INVALID: 2,
  /** 请求路径未匹配到任何接口（真实 HTTP 404，也带这个 code）。 */
  NOT_FOUND: 404,
  METHOD_NOT_ALLOWED: 405,

  /** 认证失败：用户名或密码错误（登录场景）；或令牌有效但用户已不存在（/me 场景）。 */
  AUTH_FAILED: 40001,
  /** 访问令牌过期 —— 这是**唯一**应该触发"用 refreshToken 换新令牌"的码。 */
  TOKEN_EXPIRED: 40002,
  /** 令牌非法 / 缺少令牌 / 类型不对 / 已被吊销 —— 刷新也救不回来，直接登出。 */
  TOKEN_INVALID: 40003,
  /** 账号已开启 MFA，但后端 TOTP 校验尚未实现（当前会直接拒绝登录）。 */
  MFA_REQUIRED: 40004,
  MFA_INVALID: 40005,
  /** 连续登录失败次数超限，账号被临时锁定。 */
  ACCOUNT_LOCKED: 40006,

  /** 租户不存在（例如访问了一个已被删除的租户）。 */
  TENANT_NOT_FOUND: 40101,
  TENANT_DISABLED: 40102,
  /** 请求缺少租户上下文 —— 属调用姿势问题，提示应偏"系统异常"而非"你没权限"。 */
  TENANT_CONTEXT_MISSING: 40104,

  /** 无权限访问。当前后端尚未做接口级鉴权，这个码实际还不会出现。 */
  FORBIDDEN: 40801,
} as const;

export type ErrorCodeValue = (typeof ErrorCode)[keyof typeof ErrorCode];
