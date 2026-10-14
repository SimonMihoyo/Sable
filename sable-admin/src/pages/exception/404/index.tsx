import { Link } from '@umijs/max';
import { Button, Card, Result } from 'antd';
import React from 'react';

/**
 * 404 —— 路径没有对应的页面（由 `config/routes.ts` 末尾的 `/*` 兜底路由渲染）。
 *
 * <p>文案直接写中文而不是走 `useIntl()`：本项目只有 zh-CN 一份语言包，
 * 多一层 key 只会让"页面上到底显示什么"多绕一道。</p>
 */
const Exception404: React.FC = () => (
  <Card variant="borderless">
    <Result
      status="404"
      title="404"
      subTitle="抱歉，你访问的页面不存在。"
      extra={
        <Link to="/welcome" prefetch>
          <Button type="primary">返回工作台</Button>
        </Link>
      }
    />
  </Card>
);

export default Exception404;
