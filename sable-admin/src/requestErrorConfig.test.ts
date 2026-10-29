/**
 * 请求层测试。
 *
 * <p>这里钉住的是三条**只有真接后端才会暴露、且一暴露就是"用户莫名被踢"**的行为：
 * 1. 401 必须能被响应拦截器看到（所以请求拦截器要放行所有状态码）；
 * 2. 令牌不可用时自动续期并重放原请求 —— 包括 `401 + 40003「缺少访问令牌」`，
 *    那是刷新页面后首屏必然遇到的情形，不认它就会每次刷新页面都被踢回登录页；
 * 3. 并发不可用时**只刷新一次**（避免重复签发 N-1 对令牌，也让"存下哪一份"确定）。</p>
 *
 * <p>注意第三条的理由**不是**"令牌轮换会互相作废"：后端刷新并不吊销传入的刷新令牌
 * （实测同一个 refreshToken 连刷两次都成功），见 `src/requestErrorConfig.ts` 的说明。</p>
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';

const h = vi.hoisted(() => ({
  messageError: vi.fn(),
  historyReplace: vi.fn(),
  location: { pathname: '/system/users', search: '', hash: '' },
  requestMock: vi.fn(),
  refreshMock: vi.fn(),
}));

vi.mock('antd', () => ({
  message: { error: h.messageError },
}));

vi.mock('@umijs/max', () => ({
  history: {
    get location() {
      return h.location;
    },
    replace: h.historyReplace,
  },
  request: h.requestMock,
}));

vi.mock('./services/sable/auth', () => ({
  refresh: h.refreshMock,
}));

import { ErrorCode } from './services/sable/errorCode';
import {
  clearTokens,
  getAccessToken,
  getRefreshToken,
  saveTokens,
} from './utils/token';

/**
 * 取一份配置对象。
 *
 * <p><b>刻意不调 `vi.resetModules()`</b>：那样会让 `requestErrorConfig` 拿到一份**新的**
 * `utils/token` 实例，而测试这边写的是旧实例 —— 令牌读写落到两个不同的模块变量上，
 * 用例会以"令牌没生效"的形式失败，看起来像产品 bug 实则不是。</p>
 *
 * <p>不需要重置模块状态：`refreshing` 在 Promise 的 `finally` 里自清，
 * 每个用例结束后都回到 null。</p>
 */
async function loadConfig() {
  const mod = await import('./requestErrorConfig');
  return mod.errorConfig;
}

type Interceptor = (arg: any) => any;

beforeEach(() => {
  vi.clearAllMocks();
  clearTokens();
  h.location.pathname = '/system/users';
  h.location.search = '';
  h.location.hash = '';
});

describe('errorThrower —— 业务码以 0 为成功', () => {
  it('code 为 0 时不抛', async () => {
    const config = await loadConfig();
    expect(() =>
      config.errorConfig?.errorThrower?.({ code: 0, message: 'success' }),
    ).not.toThrow();
  });

  it('code 非 0 时抛 BizError，并带上 code', async () => {
    const config = await loadConfig();
    try {
      config.errorConfig?.errorThrower?.({
        code: ErrorCode.AUTH_FAILED,
        message: '用户名或密码错误',
      });
      expect.unreachable('本应抛出异常');
    } catch (error: any) {
      expect(error.name).toBe('BizError');
      expect(error.info.code).toBe(ErrorCode.AUTH_FAILED);
    }
  });

  /** 响应体里没有 code 的（例如静态资源）不该被当成业务错误。 */
  it('响应体没有 code 字段时放行', async () => {
    const config = await loadConfig();
    expect(() =>
      config.errorConfig?.errorThrower?.({ foo: 'bar' } as any),
    ).not.toThrow();
  });
});

describe('requestInterceptors —— 注入令牌并放行所有状态码', () => {
  function interceptor(config: any): Interceptor {
    return config.requestInterceptors![0] as Interceptor;
  }

  it('有令牌时注入 Authorization', async () => {
    saveTokens('token-abc', 'refresh-abc');
    const config = await loadConfig();
    const result = interceptor(config)({ headers: {} } as any);
    expect(result.headers.Authorization).toBe('Bearer token-abc');
  });

  it('无令牌时不注入', async () => {
    const config = await loadConfig();
    const result = interceptor(config)({ headers: {} } as any);
    expect(result.headers?.Authorization).toBeUndefined();
  });

  it('不覆盖调用方已显式设置的 Authorization', async () => {
    saveTokens('token-abc', 'refresh-abc');
    const config = await loadConfig();
    const result = interceptor(config)({
      headers: { Authorization: 'Bearer explicit' },
    } as any);
    expect(result.headers.Authorization).toBe('Bearer explicit');
  });

  /**
   * 这条最关键：不设 `validateStatus` 的话，axios 会把 401 直接 reject，
   * 响应拦截器根本看不到它，自动续期就永远不生效。
   */
  it('始终放行所有 HTTP 状态码', async () => {
    const config = await loadConfig();
    const result = interceptor(config)({ headers: {} } as any);
    expect(typeof result.validateStatus).toBe('function');
    expect(result.validateStatus(401)).toBe(true);
    expect(result.validateStatus(500)).toBe(true);
  });
});

describe('responseInterceptors —— 令牌过期自动续期并重放', () => {
  async function interceptor() {
    const config = await loadConfig();
    return config.responseInterceptors![0] as Interceptor;
  }

  /** 造一个 axios 风格的响应。 */
  const res = (status: number, body: any = {}, config: any = {}) => ({
    status,
    data: body,
    config: { url: '/api/system/users', ...config },
  });

  it('2xx 且 code 为 0 时原样返回', async () => {
    const response = res(200, { code: 0, data: [] });
    expect(await (await interceptor())(response)).toBe(response);
  });

  it('401 时刷新令牌并重放请求', async () => {
    saveTokens('old-access', 'old-refresh');
    h.refreshMock.mockResolvedValue({
      code: ErrorCode.SUCCESS,
      data: { accessToken: 'new-access', refreshToken: 'new-refresh' },
    });
    // 重放用的是 getResponse: true，所以 request() 交回的是**完整响应**（含 .data）
    const replayed = { status: 200, data: { code: 0, data: [{ id: 1 }] } };
    h.requestMock.mockResolvedValue(replayed);

    const result = await (await interceptor())(res(401, { code: 401 }));

    expect(h.refreshMock).toHaveBeenCalledTimes(1);
    expect(h.requestMock).toHaveBeenCalledTimes(1);
    // 原样交回 axios 响应 —— 让 umi 的 request 去 .data 一层，调用方才会拿到 R 结构。
    // 若这里返回的是已经 .data 过的响应体，调用方会被再 .data 一次，拿到 R 里层的对象，
    // 于是 `res.code === 0` 恒为假（刷新页面后掉登录就是这么来的）。
    expect(result).toBe(replayed);
    // 后端轮换签发：两个令牌都要更新
    expect(getAccessToken()).toBe('new-access');
    expect(getRefreshToken()).toBe('new-refresh');
  });

  /**
   * 重放必须"要回完整响应"并带上 `_retried`。
   *
   * <p>前者见上一条；`_retried` 用来保证每个请求最多重放一次。</p>
   */
  it('重放时请求 getResponse: true 并打上 _retried 标记', async () => {
    saveTokens('old-access', 'old-refresh');
    h.refreshMock.mockResolvedValue({
      code: ErrorCode.SUCCESS,
      data: { accessToken: 'new-access', refreshToken: 'new-refresh' },
    });
    h.requestMock.mockResolvedValue({ status: 200, data: { code: 0 } });

    await (await interceptor())(res(401));

    const opts = h.requestMock.mock.calls[0][1] as any;
    expect(opts.getResponse).toBe(true);
    expect(opts._retried).toBe(true);
  });

  /**
   * 重放前必须摘掉旧的 `Authorization`。
   *
   * <p>不摘就等于拿**刚过期的那枚令牌**重放（请求拦截器只在"没有 Authorization"时才
   * 注入新令牌），结果必然是再 401 → 判定"新令牌也被拒" → 直接登出。用户表现为
   * "令牌没过期时好好的，一过期就被踢下线"。</p>
   */
  it('重放时摘掉旧的 Authorization 头，好让请求拦截器注入新令牌', async () => {
    saveTokens('old-access', 'old-refresh');
    h.refreshMock.mockResolvedValue({
      code: ErrorCode.SUCCESS,
      data: { accessToken: 'new-access', refreshToken: 'new-refresh' },
    });
    h.requestMock.mockResolvedValue({ status: 200, data: { code: 0 } });

    await (await interceptor())(
      res(401, {}, { headers: { Authorization: 'Bearer old-access' } }),
    );

    const opts = h.requestMock.mock.calls[0][1] as any;
    expect(opts.headers?.Authorization).toBeUndefined();
  });

  it('HTTP 200 + code 40002 也走续期（两种形态都要认）', async () => {
    saveTokens('old-access', 'old-refresh');
    h.refreshMock.mockResolvedValue({
      code: ErrorCode.SUCCESS,
      data: { accessToken: 'new-access', refreshToken: 'new-refresh' },
    });
    h.requestMock.mockResolvedValue({ code: 0, data: [] });

    await (await interceptor())(
      res(200, { code: ErrorCode.TOKEN_EXPIRED, message: '令牌已过期' }),
    );

    expect(h.refreshMock).toHaveBeenCalledTimes(1);
  });

  /**
   * 刷新页面后内存里的 accessToken 必为空（它刻意只存内存），首屏那个 `/me` 会被过滤器
   * 以 `401 + 40003「缺少访问令牌」` 拒掉。这一支**必须**走刷新 —— 否则每次刷新页面
   * 都会被踢回登录页，"刷新不掉登录"就成了空话。
   */
  it('401 + code 40003（缺少访问令牌）仍然走刷新，不是直接登出', async () => {
    saveTokens(null, 'old-refresh');
    h.refreshMock.mockResolvedValue({
      code: ErrorCode.SUCCESS,
      data: { accessToken: 'new-access', refreshToken: 'new-refresh' },
    });
    const replayed = {
      status: 200,
      data: { code: 0, data: { username: 'alice' } },
    };
    h.requestMock.mockResolvedValue(replayed);

    const result = await (await interceptor())(
      res(401, { code: ErrorCode.TOKEN_INVALID, message: '缺少访问令牌' }),
    );

    expect(h.refreshMock).toHaveBeenCalledTimes(1);
    // 交回完整响应：调用方（getInitialState 的 /me）要能读到 data.code === 0
    expect(result).toBe(replayed);
    expect(h.historyReplace).not.toHaveBeenCalled();
  });

  /**
   * 并发场景：首屏多个请求同时收到 401，只应真正刷新一次。
   *
   * <p>理由不是"第二次会拿着已作废的令牌失败"（后端刷新不吊销传入的刷新令牌，
   * 实测连刷两次都成功），而是：每次刷新都会签发一对新令牌，N 次并发会让
   * "本地最终存的是哪一个响应"变得不确定，还白签发了 N-1 对。</p>
   */
  it('并发过期时只刷新一次', async () => {
    saveTokens('old-access', 'old-refresh');
    h.refreshMock.mockResolvedValue({
      code: ErrorCode.SUCCESS,
      data: { accessToken: 'new-access', refreshToken: 'new-refresh' },
    });
    h.requestMock.mockResolvedValue({ code: 0, data: [] });

    const call = await interceptor();
    await Promise.all([call(res(401)), call(res(401)), call(res(401))]);

    expect(h.refreshMock).toHaveBeenCalledTimes(1);
    expect(h.requestMock).toHaveBeenCalledTimes(3);
  });

  it('刷新失败时清会话并回登录页，且带 redirect', async () => {
    saveTokens('old-access', 'old-refresh');
    h.refreshMock.mockResolvedValue({
      code: ErrorCode.TOKEN_INVALID,
      message: '刷新令牌无效',
    });

    await expect((await interceptor())(res(401))).rejects.toThrow();

    expect(h.historyReplace).toHaveBeenCalledWith(
      '/user/login?redirect=%2Fsystem%2Fusers',
    );
    expect(getAccessToken()).toBeNull();
    expect(getRefreshToken()).toBeNull();
  });

  it('本地没有刷新令牌时直接回登录页，不发刷新请求', async () => {
    // 仅设置访问令牌，refresh 为空
    saveTokens('old-access', null);

    await expect((await interceptor())(res(401))).rejects.toThrow();

    expect(h.refreshMock).not.toHaveBeenCalled();
    expect(h.historyReplace).toHaveBeenCalled();
  });

  /** 重放后仍 401（例如账号刚被停用）—— 直接回登录页，不再刷新。 */
  it('已重放过（_retried）时不再刷新，直接回登录页', async () => {
    saveTokens('new-access', 'new-refresh');

    await expect(
      (await interceptor())(res(401, {}, { _retried: true })),
    ).rejects.toThrow();

    expect(h.refreshMock).not.toHaveBeenCalled();
    expect(h.historyReplace).toHaveBeenCalled();
  });

  it('非 2xx 且非 401 时抛错，但不跳登录页', async () => {
    await expect((await interceptor())(res(500, {}))).rejects.toThrow();
    expect(h.historyReplace).not.toHaveBeenCalled();
  });

  /**
   * 403 是服务端 `@PreAuthorize` 的拒绝。它的响应体仍是 `R` 结构（`{code: 40801}`），
   * 所以必须按**业务错误**上抛 —— 否则 errorHandler 里那条"没有权限执行该操作"的分支
   * 永远不会被走到，用户只会看到笼统的"请求失败（HTTP 403）"。
   */
  it('403 + code 40801 按业务错误上抛，好让提示走到「无权限」分支', async () => {
    try {
      await (await interceptor())(
        res(403, { code: ErrorCode.FORBIDDEN, message: '无权限访问' }),
      );
      expect.unreachable('本应抛出异常');
    } catch (error: any) {
      expect(error.name).toBe('BizError');
      expect(error.info.code).toBe(ErrorCode.FORBIDDEN);
      // 原始响应仍要留着，调用方偶尔需要看状态码
      expect(error.response.status).toBe(403);
    }
    expect(h.historyReplace).not.toHaveBeenCalled();
  });

  /** 刷新接口自己失败时绝不能再触发刷新，否则是死循环。 */
  it('刷新接口自身的 401 不被当作令牌过期', async () => {
    saveTokens('old-access', 'old-refresh');

    await expect(
      (await interceptor())(res(401, {}, { url: '/api/auth/refresh' })),
    ).rejects.toThrow();

    expect(h.refreshMock).not.toHaveBeenCalled();
  });
});
