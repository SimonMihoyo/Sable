import { LogoutOutlined } from '@ant-design/icons';
import { history, useModel } from '@umijs/max';
import type { MenuProps } from 'antd';
import { Spin } from 'antd';
import React, { startTransition } from 'react';
import { logout as logoutApi } from '@/services/sable/auth';
import { clearTokens, getRefreshToken } from '@/utils/token';
import HeaderDropdown from '../HeaderDropdown';

type GlobalHeaderRightProps = {
  children?: React.ReactNode;
};

const menuItems: MenuProps['items'] = [
  {
    key: 'logout',
    icon: <LogoutOutlined />,
    label: '退出登录',
  },
];

/**
 * 退出登录。
 *
 * <p><b>顺序很关键：先请求后端吊销，再清本地。</b>后端的登出是"按提交的令牌定位并吊销"，
 * 若先把内存里的访问令牌清掉，请求头就空了，后端只能吊销刷新令牌 —— 那枚刚用过的
 * 访问令牌会在剩余有效期内继续可用，等于没登出干净。</p>
 *
 * <p>无论后端成功与否，本地会话都必须清掉：后端不可达、令牌已过期，都不该把用户
 * "卡在已登录状态里出不去"。</p>
 */
const handleLogout = async (
  setInitialState: (updater: (s: any) => any) => void,
): Promise<void> => {
  const refreshToken = getRefreshToken();
  try {
    // 带上刷新令牌才算"彻底登出"：不传的话它仍能在有效期内换新的访问令牌
    await logoutApi(refreshToken, { skipErrorHandler: true });
  } catch {
    // 忽略 —— 吊销失败不该阻止本地登出
  }
  startTransition(() => {
    setInitialState((s: any) => ({ ...s, currentUser: undefined }));
  });
  clearTokens();
  history.replace({ pathname: '/user/login' });
};

export const AvatarDropdown: React.FC<GlobalHeaderRightProps> = ({
  children,
}) => {
  const { initialState, setInitialState } = useModel('@@initialState');

  const onMenuClick: MenuProps['onClick'] = (event) => {
    if (event.key === 'logout') {
      void handleLogout(setInitialState as (updater: (s: any) => any) => void);
    }
  };

  if (!initialState?.currentUser) {
    // 没有用户信息时不渲染菜单：点开一个空的下拉框比什么都不显示更让人困惑
    return <Spin size="small" />;
  }

  return (
    <HeaderDropdown
      placement="bottomRight"
      menu={{
        selectedKeys: [],
        onClick: onMenuClick,
        items: menuItems,
      }}
      arrow
    >
      {children}
    </HeaderDropdown>
  );
};
