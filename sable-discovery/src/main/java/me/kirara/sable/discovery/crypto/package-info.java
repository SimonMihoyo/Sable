/**
 * 数据源口令的加解密工具。
 *
 * <p>只做密码学，不认识数据库也不依赖 Spring —— 密钥由启动模块的
 * {@code DiscoveryCryptoConfig} 装配进来，业务代码通过构造器注入
 * {@link me.kirara.sable.discovery.crypto.DataSourceCipher} 使用。</p>
 */
package me.kirara.sable.discovery.crypto;
