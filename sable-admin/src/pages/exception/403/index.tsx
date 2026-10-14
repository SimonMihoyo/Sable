import { Link } from '@umijs/max';
import { Button, Card, Result } from 'antd';

/**
 * 403 —— 已登录但没有权限。
 *
 * <p>注意后端当前的接口级鉴权尚未落地（任何登录用户都能调到受保护接口），
 * 所以这个页面暂时只有前端菜单/按钮级控制会走到。等后端补上校验后，
 * 它才会真正承接"被拒绝"的场景。</p>
 */
export default () => (
  <Card variant="borderless">
    <Result
      status="403"
      title="403"
      subTitle="抱歉，你没有访问该页面的权限。"
      extra={
        <Link to="/welcome" prefetch>
          <Button type="primary">返回工作台</Button>
        </Link>
      }
    />
  </Card>
);
