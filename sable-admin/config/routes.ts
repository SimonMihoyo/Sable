/**
 * @name umi 的路由配置
 * @description 只支持 path,component,routes,redirect,wrappers,name,icon 的配置
 * @param path      path 只支持两种占位符配置：第一种是动态参数 :id 的形式，第二种是 * 通配符，通配符只能出现路由字符串的最后。
 * @param component 配置 location 和 path 匹配后用于渲染的 React 组件路径。可以是绝对路径，也可以是相对路径，如果是相对路径，会从 src/pages 开始找起。
 * @param routes    配置子路由，通常在需要为多个路径增加 layout 组件时使用。
 * @param redirect  配置路由跳转
 * @param wrappers  配置路由组件的包装组件，通过包装组件可以为当前的路由组件组合进更多的功能。 比如，可以用于路由级别的权限校验
 * @param name      菜单标题。此处**直接写中文**，不再走 `menu.xxx` 的 i18n 间接层 ——
 *                  项目当前只有中文一种语言，多一层 key 只会让"菜单上显示的是什么"多绕一道。
 *                  将来真要做多语言，把这里换回 key 并补 `src/locales/*\/menu.ts` 即可。
 * @param icon      配置路由的图标，取值参考 https://ant.design/components/icon-cn， 注意去除风格后缀和大小写
 * @doc https://umijs.org/docs/guides/routes
 */

/**
 * 菜单按「业务模块」分组，与后端的 Maven 模块一一对应。
 *
 * 当前只放出 Sprint 1 已具备后端能力的三块（工作台 / 租户 / 用户 / 角色）。
 * 数据资产发现、分类分级、审计、合规评估、告警、报告各属后续 Sprint ——
 * 待对应模块的接口就绪后再在此处追加。**不为还没实现的接口预埋菜单项**：
 * 点进去只会是一堆空表格，比"没有入口"更容易让人误判成故障。
 */
export default [
  {
    path: '/user',
    layout: false,
    routes: [
      {
        name: '登录',
        path: '/user/login',
        component: './user/login',
      },
    ],
  },
  {
    path: '/welcome',
    name: '工作台',
    icon: 'dashboard',
    component: './welcome',
  },
  {
    path: '/system',
    name: '系统管理',
    icon: 'setting',
    routes: [
      {
        path: '/system',
        redirect: '/system/tenants',
      },
      {
        name: '租户管理',
        icon: 'cluster',
        path: '/system/tenants',
        component: './system/tenant',
      },
      {
        name: '用户管理',
        icon: 'user',
        path: '/system/users',
        component: './system/user',
      },
      {
        name: '角色管理',
        icon: 'team',
        path: '/system/roles',
        component: './system/role',
      },
    ],
  },
  {
    path: '/',
    redirect: '/welcome',
  },
  {
    component: './exception/404',
    layout: false,
    path: '/*',
  },
];
