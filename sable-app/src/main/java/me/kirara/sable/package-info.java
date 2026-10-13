/**
 * Sablé 启动模块 — 装配各业务模块并暴露 Spring Boot 入口。
 *
 * <p>本模块不承载业务逻辑，只负责两件事：把分散在 sable-* 库模块中的 Bean 组装成
 * 一个可运行的应用，以及提供只有运行时才需要的基础设施（Web 过滤器、定时任务、配置类）。</p>
 */
package me.kirara.sable;
