import type { ProLayoutProps } from '@ant-design/pro-components';

/**
 * @name ProLayout 的默认设置
 * @description 与 config/config.ts 的 `layout` 段合并后生效。
 *              这里只放"视觉与布局"的默认值；菜单来自 config/routes.ts，
 *              权限来自 src/access.ts，运行时用户信息来自 src/app.tsx。
 */
const Settings: ProLayoutProps & {
  logo?: string;
} = {
  navTheme: 'light',
  colorPrimary: '#1677ff',
  // mix：顶部通栏 + 左侧菜单。中后台用它比纯 side 更容易容纳后续的
  // 全局入口（租户切换、告警铃铛、全局搜索）
  layout: 'mix',
  contentWidth: 'Fluid',
  fixedHeader: false,
  fixSiderbar: true,
  colorWeak: false,
  title: '数据合规管理系统',
  // 刻意不设 logo：模板默认指向阿里云 CDN 的一张示例图，
  // 合规产品的页面不该引用外部站点的资源。待自有视觉资源确定后再补。
  iconfontUrl: '',
  token: {
    // 通过 token 覆盖 ProLayout 样式，见
    // https://procomponents.ant.design/components/layout#%E9%80%9A%E8%BF%87-token-%E4%BF%AE%E6%94%B9%E6%A0%B7%E5%BC%8F
  },
};

export default Settings;
