import type { RequestOptions } from '@@/plugin-request/request';
import type { RequestConfig } from '@umijs/max';
import { history, request } from '@umijs/max';
import { message } from 'antd';
import { refresh as refreshApi } from './services/sable/auth';
import { ErrorCode } from './services/sable/errorCode';
import {
  clearTokens,
  getAccessToken,
  getRefreshToken,
  saveTokens,
} from './utils/token';

const LOGIN_PATH = '/user/login';

/**
 * 令牌失效时清会话并回登录页，带上 `redirect` 以便登录后跳回原处。
 *
 * <p>用 {@code history.replace} 而不是 {@code window.location.href}：后者会整页刷新、
 * 丢掉应用程序状态。已经在登录页时直接返回，避免把"登录页本身"记成 redirect。</p>
 */
function redirectToLogin(): void {
  clearTokens();
  const { pathname, search, hash } = history.location;
  if (pathname === LOGIN_PATH) {
    return;
  }
  const redirect = encodeURIComponent(pathname + search + hash);
  history.replace(`${LOGIN_PATH}?redirect=${redirect}`);
}

/**
 * 正在进行中的刷新请求；没有则为 null。
 *
 * <p><b>为什么要把并发收敛成一次</b>：页面首屏常会并发发起多个请求，令牌过期时它们会
 * 同时收到 401。若各自去刷新，就会并发调用 N 次 `/auth/refresh`，而后端每次都会**签发
 * 一对全新令牌**，于是：N 个响应带着 N 个不同的 `refreshToken` 回来，谁最后落地就存谁 ——
 * 存下来的虽然仍是有效令牌，但"本地这份是不是最新签发的那份"变得不确定，
 * 且白白多签发了 N-1 对。共用同一个 Promise 只是把这件事收敛成"刷新一次、存一份"。</p>
 *
 * <p>⚠️ 更正一处此前的错误认知：后端的刷新**不是"轮换即作废"** ——
 * `AuthServiceImpl.refresh()` 校验传入的刷新令牌（有效期 / 类型 / 用户存在 / 未被吊销 /
 * 账号可用），然后签发新的一对，但**并不会吊销传入的那一个**，旧的刷新令牌在它自己的
 * 有效期内（7 天）继续可用。实测：同一个 `refreshToken` 连刷两次，两次都返回 `code=0`。
 * 所以并发刷新**不会**导致"其余请求因令牌作废而失败"，别据此推断"用户会被踢下线"。
 * 真正会让刷新失败的是：刷新令牌过期、已被登出吊销、账号被停用/锁定。</p>
 */
let refreshing: Promise<boolean> | null = null;

/** 发起（或复用）一次刷新。并发调用只会真正执行一次。 */
function refreshOnce(): Promise<boolean> {
  if (!refreshing) {
    refreshing = doRefresh().finally(() => {
      refreshing = null;
    });
  }
  return refreshing;
}

/** 实际执行刷新；成功则把新令牌写回本地。任何异常都收敛成 false，由调用方决定后果。 */
async function doRefresh(): Promise<boolean> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    return false;
  }
  try {
    // skipErrorHandler：刷新失败统一由调用方处理成"回登录页"，
    // 不该再弹一次业务错误提示
    const res = await refreshApi(refreshToken, { skipErrorHandler: true });
    if (res?.code !== ErrorCode.SUCCESS || !res.data?.accessToken) {
      return false;
    }
    // 后端每次刷新都会签发一对**全新**令牌（返回的 refreshToken 与传入的不是同一个），
    // 所以两个一起覆盖，保证本地存的与最近一次签发的一致
    saveTokens(res.data.accessToken, res.data.refreshToken);
    return true;
  } catch {
    return false;
  }
}

/**
 * 这个响应是否属于"令牌不可用、可以靠刷新救回来"。
 *
 * <p>判据取**HTTP 401**（认证失败由 `JwtAuthenticationFilter` 直接写 401 + `R` 体，
 * 不经过 `GlobalExceptionHandler`），外加 `R.code === 40002`（令牌过期）这一形态。</p>
 *
 * <p>⚠️ **不要**在这里把 `40003`（令牌非法/缺失）排除掉。刷新页面后内存里的 accessToken
 * 必然为空（它刻意只存内存），此时首屏那个 `/me` 会以 `401 + 40003「缺少访问令牌」`被拒 ——
 * 而它恰恰是**最需要走刷新**的一次调用：刷新成功后重放，用户就"刷新页面不掉登录"。
 * 若把 40003 当成"刷新也救不回来"直接登出，每一次刷新页面都会被踢回登录页。</p>
 *
 * <p>真正救不回来的情形由刷新本身决定（刷新令牌过期/被吊销/账号停用），
 * 见 {@link doRefresh} 返回 false 之后的处理。</p>
 */
function isTokenExpired(response: any): boolean {
  if (!response) {
    return false;
  }
  const code = response.data?.code;
  return response.status === 401 || code === ErrorCode.TOKEN_EXPIRED;
}

/** 刷新接口自身的路径 —— 它失败时绝不能再去刷新，否则是死循环。 */
function isRefreshRequest(url?: string): boolean {
  return typeof url === 'string' && url.includes('/auth/refresh');
}

/**
 * 把非 2xx 响应包装成 Error，交给上层的错误处理链（errorHandler 会据此弹提示）。
 *
 * <p>带业务码的一律按**业务错误**上抛，而不是按 HTTP 错误：后端对部分客户端错误用真实
 * 状态码（403 无权限 / 404 路径不存在 / 405 方法不支持 / 415 媒体类型），但响应体仍是
 * 统一的 `R` 结构。若按 HTTP 错误处理，一次鉴权拒绝只会提示"请求失败（HTTP 403）"，
 * 而上面 `errorHandler` 里那条"没有权限执行该操作"的分支永远不会被走到 ——
 * 用户看到「失败」，却不知道是权限问题还是服务出了问题。</p>
 */
function buildHttpError(response: any): Error {
  const status = response?.status;
  const body = response?.data;

  if (body && typeof body.code === 'number' && body.code !== ErrorCode.SUCCESS) {
    const bizError: any = new Error(body.message ?? '请求失败');
    bizError.name = 'BizError';
    bizError.info = { code: body.code, message: body.message, data: body.data };
    // 仍带上原始响应：调用方偶尔需要看状态码（例如按 403 单独埋点）
    bizError.response = response;
    return bizError;
  }

  const message = body?.message ?? `请求失败（HTTP ${status ?? '未知'}）`;
  const error: any = new Error(message);
  error.name = 'HttpError';
  error.response = response;
  return error;
}

export const errorConfig: RequestConfig = {
  errorConfig: {
    /**
     * 业务错误的"抛出"环节。
     *
     * <p>后端约定是：**业务失败走 HTTP 200 + 非 0 `code`**；而 400/403/404/405/415 这类
     * 客户端错误用真实 HTTP 状态码，响应体同样是 `R` 结构（见 `buildHttpError`）。
     * 两种形态都以 `code` 为准 —— 响应体里没有 `code`（例如静态资源）就放行，
     * 不当作业务错误。</p>
     */
    errorThrower: (res) => {
      const body = res as SableAPI.R<unknown> | undefined;
      const code = body?.code;
      if (code !== undefined && code !== ErrorCode.SUCCESS) {
        const error: any = new Error(body?.message ?? '请求失败');
        error.name = 'BizError';
        error.info = { code, message: body?.message, data: body?.data };
        throw error;
      }
    },

    errorHandler: (error: any, opts: any) => {
      if (opts?.skipErrorHandler) {
        // 调用方明确要求自己处理（例如登录页要按错误码分支），原样抛出
        throw error;
      }

      if (error.name === 'BizError') {
        const info: { code?: number; message?: string } = error.info ?? {};
        switch (info.code) {
          case ErrorCode.TENANT_CONTEXT_MISSING:
            // 这不是"没权限"，而是会话本身不完整（令牌里没有租户）。
            // 让用户重新登录比提示"无权限"更接近事实，也真的能解决问题。
            message.error('登录会话不完整，请重新登录');
            redirectToLogin();
            break;
          case ErrorCode.FORBIDDEN:
            message.error('没有权限执行该操作');
            break;
          case ErrorCode.TENANT_NOT_FOUND:
          case ErrorCode.TENANT_DISABLED:
            message.error(info.message ?? '租户不可用');
            break;
          default:
            message.error(info.message ?? '请求失败');
        }
        return;
      }

      if (error.response) {
        // 走到这里说明不是业务码，而是真实 HTTP 错误（如 500）
        message.error(`请求失败（HTTP ${error.response.status}）`);
      } else if (typeof navigator !== 'undefined' && !navigator.onLine) {
        message.error('网络不可用，请检查网络连接后重试');
      } else if (error.request) {
        message.error('服务无响应，请稍后重试');
      } else {
        message.error('请求出错，请重试');
      }
    },
  },

  /** 请求拦截器：为每个请求带上访问令牌。 */
  requestInterceptors: [
    (config: RequestOptions) => {
      const token = getAccessToken();
      if (token) {
        const headers = (config.headers ?? {}) as Record<string, any>;
        // 不覆盖调用方已显式设置的 Authorization
        if (!headers.Authorization) {
          config.headers = {
            ...headers,
            Authorization: `Bearer ${token}`,
          } as any;
        }
      }
      // ⚠️ 放行所有 HTTP 状态码。不放行的话，401 会被 axios 直接判定为失败并 reject，
      // 压根进不到下面的响应拦截器 —— 而"自动续期"正需要在 401 时介入。
      // 放心，非 2xx 不会因此被当成成功：响应拦截器会自己把它们抛回去。
      (config as any).validateStatus = () => true;
      return config;
    },
  ],

  /**
   * 响应拦截器：令牌过期时自动续期并**重放原请求**。
   *
   * <p>用户视角上这是"无感续期"：访问令牌 30 分钟就过期，正在填的表单不该因此被弹回
   * 登录页。重放依赖请求拦截器重新注入 `Authorization` —— 那时内存里已经是新令牌了。</p>
   *
   * <p>{@code _retried} 标记保证每个请求最多重放一次：续期后仍然 401（例如账号刚被停用）
   * 就直接回登录页，不会陷入"刷新→重放→再刷新"的循环。</p>
   */
  responseInterceptors: [
    async (response: any) => {
      const url = response?.config?.url;
      const status = response?.status;

      // 1) 令牌过期：续期后重放原请求
      if (isTokenExpired(response) && !isRefreshRequest(url)) {
        if (response.config?._retried) {
          // 刚换的新令牌仍然被拒 —— 多半是账号被停用/删除，重试也没意义
          redirectToLogin();
          throw buildHttpError(response);
        }
        if (await refreshOnce()) {
          const retryConfig = { ...response.config, _retried: true };
          return request(retryConfig.url, retryConfig);
        }
        // 刷新也救不回来（刷新令牌过期/被吊销/本地根本没有）—— 只能重新登录
        redirectToLogin();
        throw buildHttpError(response);
      }

      // 2) 其余非 2xx：状态码被放行过，这里必须自己抛，
      //    否则调用方会把 500 的响应体当作成功结果
      if (typeof status === 'number' && status >= 400) {
        throw buildHttpError(response);
      }

      return response;
    },
  ],
};
