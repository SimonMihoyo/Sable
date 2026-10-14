import { Link } from '@umijs/max';
import { Button, Card, Result } from 'antd';

/**
 * 500 —— 页面渲染出错。
 *
 * <p>真正兜住渲染异常的是 `src/components/ErrorBoundary`（挂在 `rootContainer` 上）；
 * 这个页面预留给"边界之外"的兜底场景。</p>
 */
export default () => (
  <Card variant="borderless">
    <Result
      status="500"
      title="500"
      subTitle="抱歉，页面出错了。"
      extra={
        <Link to="/welcome" prefetch>
          <Button type="primary">返回工作台</Button>
        </Link>
      }
    />
  </Card>
);
