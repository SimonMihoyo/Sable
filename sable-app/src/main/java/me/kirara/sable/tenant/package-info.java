/**
 * 多租户的存储侧实现 —— 本包只做三件事，彼此职责不重叠。
 *
 * <p>与设计文档最直接的一条差异：文档 §5.2 给的样例是在
 * {@code HandlerInterceptor} 里执行 {@code jdbcTemplate.execute("SET search_path TO ...")}。
 * 那个写法在本项目里是**错的** —— {@code jdbcTemplate} 从连接池里取出的连接，很可能不是
 * MyBatis 随后执行 SQL 用的那一条，于是"设了个寂寞"，还会把池里连接的状态搞脏，
 * 成为串租户的来源。本包改用两条更结实的路子：</p>
 *
 * <ol>
 *   <li>{@link me.kirara.sable.tenant.TenantRoutingDataSource} —— 按
 *       {@link me.kirara.sable.common.TenantContext} 把连接**换成租户专属的连接池**，
 *       池里每条连接的 {@code currentSchema} 在建池时就写死了。隔离由连接本身保证，
 *       不存在"忘记复位"这种可能。</li>
 *   <li>{@link me.kirara.sable.tenant.TenantRequiredInterceptor} —— 兜底。
 *       语句属于租户范围却没有租户上下文时直接抛错，绝不静默放行。</li>
 * </ol>
 *
 * <p>供给（建 schema / 跑 {db/tenant} 迁移）由
 * {@link me.kirara.sable.tenant.TenantSchemaProvisionerImpl} 负责，
 * 装配在 {@link me.kirara.sable.config.TenantConfig}。</p>
 */
package me.kirara.sable.tenant;
