/**
 * @name 代理的配置
 * @see 生产环境代理不生效，因此这里只配开发/联调环境的转发
 * @doc https://umijs.org/docs/guides/proxy
 *
 * ── 为什么前端统一走 /api 前缀 ────────────────────────────────
 * 后端接口本身是**裸路径**（`/auth/login`、`/system/users`，没有 `/api` 也没有 `/api/v1`）。
 * 前端仍统一以 `/api` 发起请求，由这一层把前缀剥掉，好处是：
 *   1. 开发时请求打到 dev server，与页面同源 —— 不依赖后端的 CORS 配置，
 *      也不会有"预检请求失败"这类只在浏览器里才出现的问题；
 *   2. 生产环境由 Nginx 配 `location /api/ { proxy_pass http://后端/; }`，
 *      前端代码与本地完全一致，不需要改 baseURL、更不用重建；
 *   3. 将来后端真要加版本前缀，只改这一处 rewrite。
 * 注意 `pathRewrite` 是必需的：少了它，`/api/auth/login` 会被原样转发成
 * `/api/auth/login`，而后端并没有这个路径，表现为一片 404。
 */
export default {
  dev: {
    '/api/': {
      // 后端默认 8080；本地起在别的端口时改这里
      target: 'http://localhost:8080',
      changeOrigin: true,
      pathRewrite: { '^/api': '' },
    },
  },
};
