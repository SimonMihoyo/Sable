/**
 * 组件的统一出口。
 *
 * <p>只导出**当前页面真正用到**的组件。模板自带了一批演示组件（文章列表、头像组、
 * 标签选择、语言切换等），它们只服务于已被删除的演示页 —— 留在出口里，
 * 后来的人会以为某处在用，再去顺着找就是一条死路。</p>
 */

export { default as ErrorBoundary } from './ErrorBoundary';
export { default as Footer } from './Footer';
export { default as OfflineBanner } from './OfflineBanner';
export { AvatarDropdown } from './RightContent/AvatarDropdown';
