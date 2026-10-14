/**
 * 令牌存储 —— 会话凭证的唯一存放点。
 *
 * ── 分工与理由 ────────────────────────────────────────────────
 * - **accessToken 只驻留内存**（模块级变量，不落盘）。它的有效期是 30 分钟，
 *   刷新页面即丢失也无妨 —— 靠 refreshToken 换一个新的就是。好处是即便页面被注入
 *   脚本（XSS），攻击者也拿不到这枚"当下就能用的凭证"，能偷到的时间窗只剩当前这一次
 *   页面会话。
 * - **refreshToken 放 localStorage**。它必须跨页面刷新存活，否则用户每刷新一次就要
 *   重新登录。有效期 7 天，是这里真正的长期资产；这也是为什么它值得被单独看待。
 *
 * 把两者放在同一个模块里，是为了让"清空会话"只有一个入口（{@link clearTokens}）——
 * 分散在多个地方清理，必然出现"清了 access 忘了 refresh"的漏网情形，表现为
 * "点了登出，刷新页面又是登录状态"。
 *
 * ── 与后端的关系 ──────────────────────────────────────────────
 * 后端每次 `/auth/refresh` 都会签发**一对全新令牌**（返回的 refreshToken 与传入的
 * 不是同一个）。因此调用方拿到刷新结果后应**整体覆盖**（用 {@link saveTokens}）。
 *
 * ⚠️ 但请注意：**旧的那一个不会因此作废** —— `AuthServiceImpl.refresh()` 并不吊销传入的
 * 刷新令牌，它在自己的有效期内继续可用（实测同一个 refreshToken 连刷两次都成功）。
 * 所以"只更新一半"的后果不是"下次刷新直接失败"，而是本地存的那份不再是最新签发的。
 * 这仍是应当避免的状态，只是别据此推断出"用户会被踢下线"这类不存在的故障。
 */

/** refreshToken 在 localStorage 里的键名。带前缀是为了不和同域其它应用的键撞上。 */
const REFRESH_TOKEN_KEY = 'sable.refreshToken';

let accessToken: string | null = null;

/**
 * 取 localStorage，取不到就返回 null。
 *
 * <p>两种情况会取不到：单测跑在 node 环境（没有 window）；浏览器隐私模式下访问
 * localStorage 会直接抛异常。前者是为了让本模块能被直接单测，后者是为了不让一个
 * 存不了令牌就整个页面白屏。</p>
 */
function storage(): Storage | null {
  if (typeof window === 'undefined') {
    return null;
  }
  try {
    return window.localStorage;
  } catch {
    return null;
  }
}

/** 当前访问令牌；未登录或已清空时为 null。 */
export function getAccessToken(): string | null {
  return accessToken;
}

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

/** 当前刷新令牌；未登录或已清空时为 null。 */
export function getRefreshToken(): string | null {
  return storage()?.getItem(REFRESH_TOKEN_KEY) ?? null;
}

export function setRefreshToken(token: string | null): void {
  const store = storage();
  if (!store) {
    return;
  }
  if (token) {
    store.setItem(REFRESH_TOKEN_KEY, token);
  } else {
    store.removeItem(REFRESH_TOKEN_KEY);
  }
}

/**
 * 保存一对令牌 —— 登录成功与刷新成功后都必须走这里。
 *
 * <p>刷新令牌传 null 时只更新访问令牌、保留原有的刷新令牌：后端在轮换时总是两个都发，
 * 所以正常路径下两个参数都有值；这条兜底是防"某个响应漏了 refreshToken"时，
 * 不至于把还能用的刷新令牌顺手删掉。</p>
 */
export function saveTokens(access: string | null, refresh?: string | null): void {
  accessToken = access;
  if (refresh !== undefined && refresh !== null) {
    setRefreshToken(refresh);
  }
}

/** 清空会话。登出、刷新失败、令牌被吊销 —— 所有"会话结束"的路径都收口到这里。 */
export function clearTokens(): void {
  accessToken = null;
  setRefreshToken(null);
}

/** 是否还存在可用的会话线索（内存里的 access 或本地的 refresh 任一即可）。 */
export function hasSession(): boolean {
  return accessToken !== null || getRefreshToken() !== null;
}
