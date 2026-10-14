import type { Settings as LayoutSettings } from '@ant-design/pro-components';
import type { RequestConfig, RunTimeLayoutConfig } from '@umijs/max';
import { history, Link } from '@umijs/max';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import React from 'react';

// dayjs 的相对时间插件（"3 分钟前"这类文案）全局注册一次
dayjs.extend(relativeTime);

import {
  AvatarDropdown,
  ErrorBoundary,
  Footer,
  OfflineBanner,
} from '@/components';
import { ErrorCode } from '@/services/sable/errorCode';
import { currentUser as queryCurrentUser } from '@/services/sable/user';
import defaultSettings from '../config/defaultSettings';
import { errorConfig } from './requestErrorConfig';

const loginPath = '/user/login';

/**
 * 应用启动时执行一次，结果挂在 `initialState` 上供全局读取。
 *
 * @see https://umijs.org/docs/api/runtime-config#getinitialstate
 */
export async function getInitialState(): Promise<{
  settings?: Partial<LayoutSettings>;
  currentUser?: SableAPI.CurrentUser;
  fetchUserInfo?: () => Promise<SableAPI.CurrentUser | undefined>;
}> {
  const fetchUserInfo = async (): Promise<SableAPI.CurrentUser | undefined> => {
    try {
      // skipErrorHandler：未登录时拿不到用户信息是**预期内的正常路径**，
      // 不该弹出"请求失败"的红条来吓人
      const res = await queryCurrentUser({ skipErrorHandler: true });
      if (res?.code === ErrorCode.SUCCESS && res.data) {
        return res.data;
      }
      return undefined;
    } catch {
      return undefined;
    }
  };

  // 这里直接请求 /me，不做"先判断有没有令牌"的分支 ——
  // 刷新页面后内存里的 accessToken 必然为空（它刻意只存内存），
  // 而本地若有 refreshToken，响应拦截器会自动续期并重放这次请求。
  // 也就是说：这个看似会失败的调用，正是"刷新页面不掉登录"的实现方式。
  const currentUser = await fetchUserInfo();

  return {
    fetchUserInfo,
    currentUser,
    settings: defaultSettings as Partial<LayoutSettings>,
  };
}

// ProLayout 支持的配置项见 https://procomponents.ant.design/components/layout
export const layout: RunTimeLayoutConfig = ({ initialState }) => {
  return {
    menuItemRender: (item, dom) => {
      if (item.path) {
        return (
          <Link to={item.path} prefetch>
            {dom}
          </Link>
        );
      }
      return dom;
    },
    avatarProps: {
      src: initialState?.currentUser?.avatarUrl,
      title:
        initialState?.currentUser?.displayName ||
        initialState?.currentUser?.username ||
        '未登录',
      render: (_props, avatarChildren) => (
        <AvatarDropdown>{avatarChildren}</AvatarDropdown>
      ),
    },
    footerRender: () => <Footer />,

    /**
     * 路由守卫 —— 每次路由切换都会执行。
     *
     * <p><b>这里同时管两个方向</b>：未登录闯内页要拦回去；已登录还停留在登录页
     * 也要送走。只做前一半的话，用户登录成功后再点浏览器"后退"回到登录页，
     * 会看到一个空的登录表单，像是掉线了。</p>
     */
    onPageChange: () => {
      const { location } = history;
      const { currentUser } = initialState ?? {};

      if (!currentUser && location.pathname !== loginPath) {
        const redirect = encodeURIComponent(
          location.pathname + location.search + location.hash,
        );
        history.replace(`${loginPath}?redirect=${redirect}`);
        return;
      }

      if (currentUser && location.pathname === loginPath) {
        const redirect = new URLSearchParams(location.search).get('redirect');
        // 没有 redirect 时统一去工作台
        history.replace(redirect ? decodeURIComponent(redirect) : '/welcome');
      }
    },

    ErrorBoundary,
    menuHeaderRender: undefined,
    // 模板自带的背景装饰图是指向阿里云 CDN 的外链，合规产品不该引用外部资源
    bgLayoutImgList: undefined,
    ...initialState?.settings,
  };
};

/**
 * request 的全局配置。
 *
 * <p>`baseURL` 留空，路径统一写 `/api/...`：开发环境由 `config/proxy.ts` 转发到后端
 * 并剥掉前缀，生产环境交给 Nginx 同域代理。前端代码两边完全一致。</p>
 */
export const request: RequestConfig = {
  baseURL: '',
  timeout: 20000,
  ...errorConfig,
};

export function rootContainer(container: React.ReactNode) {
  return (
    <>
      <OfflineBanner />
      <ErrorBoundary>{container}</ErrorBoundary>
    </>
  );
}
