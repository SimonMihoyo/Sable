/**
 * Web 层基础设施 — 请求级过滤器。
 *
 * <p>两个过滤器按 {@code @Order} 分工明确，顺序不可对调：</p>
 * <ol>
 *   <li>{@code JwtAuthenticationFilter}（先）—— 认证与拦截。白名单外的请求必须携带有效
 *       访问令牌，否则 401；通过后把身份与令牌中的租户写入上下文。</li>
 *   <li>{@code TenantContextFilter}（后）—— 仅在令牌未给出租户时，用 {@code X-Tenant-Id}
 *       请求头兜底，保留系统级用户的跨租户操作能力。</li>
 * </ol>
 *
 * <p>过滤器的职责边界是"认证"（你是谁），不是"鉴权"（你能不能做这件事）——
 * 后者按接口、按权限判断，尚未实现。</p>
 */
package me.kirara.sable.web;
