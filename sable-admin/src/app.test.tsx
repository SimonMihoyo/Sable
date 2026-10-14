/**
 * 应用运行时配置测试。
 *
 * <p>重点是**路由守卫的两个方向**：未登录闯内页要拦回登录页（并记住原地址），
 * 已登录还停在登录页要被送走。只做前一半的话，用户登录成功后再点浏览器"后退"
 * 会看到一个空登录表单，像是掉线了。</p>
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';

const h = vi.hoisted(() => ({
  historyReplace: vi.fn(),
  location: { pathname: '/welcome', search: '', hash: '' },
  queryCurrentUser: vi.fn(),
}));

vi.mock('@umijs/max', () => ({
  history: {
    get location() {
      return h.location;
    },
    replace: h.historyReplace,
  },
  Link: ({ children }: any) => children,
}));

vi.mock('@/services/sable/user', () => ({
  currentUser: h.queryCurrentUser,
}));

// 组件树里带着 antd 与不少依赖，测试不关心渲染结果，整体替换掉
vi.mock('@/components', () => ({
  AvatarDropdown: () => null,
  ErrorBoundary: ({ children }: any) => children,
  Footer: () => null,
  OfflineBanner: () => null,
}));

vi.mock('./requestErrorConfig', () => ({ errorConfig: {} }));

vi.mock('../config/defaultSettings', () => ({
  default: { navTheme: 'light' },
}));

function setLocation(pathname: string, search = '', hash = '') {
  h.location.pathname = pathname;
  h.location.search = search;
  h.location.hash = hash;
}

beforeEach(() => {
  vi.clearAllMocks();
  setLocation('/welcome');
});

describe('getInitialState', () => {
  it('拿到用户信息时写入 currentUser', async () => {
    const { getInitialState } = await import('./app');
    h.queryCurrentUser.mockResolvedValue({
      code: 0,
      data: {
        id: 1,
        username: 'alice',
        roles: ['SUPER_ADMIN'],
        permissions: ['system:manage'],
      },
    });

    const state = await getInitialState();

    expect(state.currentUser?.username).toBe('alice');
    expect(state.currentUser?.permissions).toEqual(['system:manage']);
    expect(state.fetchUserInfo).toBeTypeOf('function');
  });

  it('业务码非 0 时 currentUser 为 undefined', async () => {
    const { getInitialState } = await import('./app');
    h.queryCurrentUser.mockResolvedValue({ code: 40003, message: '令牌无效' });

    const state = await getInitialState();

    expect(state.currentUser).toBeUndefined();
  });

  it('请求抛异常时不把启动流程带崩', async () => {
    const { getInitialState } = await import('./app');
    h.queryCurrentUser.mockRejectedValue(new Error('network down'));

    await expect(getInitialState()).resolves.toMatchObject({
      currentUser: undefined,
    });
  });

  /**
   * 即使在登录页也照常请求 `/me`。这不是浪费：刷新页面后内存里的访问令牌必然为空
   * （它刻意只存内存），而本地若有刷新令牌，响应拦截器会自动续期并重放这次请求 ——
   * 这个"看似会失败"的调用正是"刷新页面不掉登录"的实现方式。
   */
  it('在登录页也照常请求 /me（靠拦截器续期实现刷新不掉登录）', async () => {
    const { getInitialState } = await import('./app');
    setLocation('/user/login');
    h.queryCurrentUser.mockResolvedValue({ code: 40003 });

    await getInitialState();

    expect(h.queryCurrentUser).toHaveBeenCalledTimes(1);
  });

  it('携带默认 settings', async () => {
    const { getInitialState } = await import('./app');
    h.queryCurrentUser.mockResolvedValue({ code: 40003 });

    const state = await getInitialState();

    expect(state.settings).toEqual({ navTheme: 'light' });
  });
});

describe('layout.onPageChange —— 路由守卫', () => {
  async function onPageChange(currentUser?: SableAPI.CurrentUser) {
    const { layout } = await import('./app');
    const config = (layout as any)({ initialState: { currentUser } });
    config.onPageChange();
  }

  const alice = {
    id: 1,
    username: 'alice',
    roles: ['SUPER_ADMIN'],
    permissions: ['system:manage'],
  } as SableAPI.CurrentUser;

  it('未登录访问内页 → 回登录页并带上 redirect', async () => {
    setLocation('/system/users', '?page=2', '');
    await onPageChange(undefined);

    expect(h.historyReplace).toHaveBeenCalledWith(
      '/user/login?redirect=%2Fsystem%2Fusers%3Fpage%3D2',
    );
  });

  it('未登录时停留在登录页 → 不跳转（否则会自跳循环）', async () => {
    setLocation('/user/login');
    await onPageChange(undefined);

    expect(h.historyReplace).not.toHaveBeenCalled();
  });

  it('已登录停在登录页 → 送到工作台', async () => {
    setLocation('/user/login');
    await onPageChange(alice);

    expect(h.historyReplace).toHaveBeenCalledWith('/welcome');
  });

  it('已登录停在登录页且带 redirect → 送到原地址', async () => {
    setLocation('/user/login', '?redirect=%2Fsystem%2Froles');
    await onPageChange(alice);

    expect(h.historyReplace).toHaveBeenCalledWith('/system/roles');
  });

  it('已登录访问内页 → 不干预', async () => {
    setLocation('/system/users');
    await onPageChange(alice);

    expect(h.historyReplace).not.toHaveBeenCalled();
  });
});
