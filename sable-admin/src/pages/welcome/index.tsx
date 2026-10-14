import { PageContainer, ProCard } from '@ant-design/pro-components';
import { useModel } from '@umijs/max';
import { Alert, Descriptions, Empty, Space, Tag, Typography } from 'antd';
import React from 'react';
import { Permission } from '@/access';

const { Paragraph, Text } = Typography;

/** 权限码 → 中文名。当前后端没有权限码字典，这里只为可读性做一层展示映射。 */
const permissionLabels: Record<string, string> = {
  [Permission.DATA_SOURCE_READ]: '查看数据源',
  [Permission.DATA_SOURCE_WRITE]: '维护数据源',
  [Permission.AUDIT_LOG_READ]: '查看审计日志',
  [Permission.AUDIT_LOG_EXPORT]: '导出审计日志',
  [Permission.ASSESSMENT_READ]: '查看合规评估',
  [Permission.ASSESSMENT_WRITE]: '维护合规评估',
  [Permission.REPORT_READ]: '查看报表',
  [Permission.REPORT_EXPORT]: '导出报表',
  [Permission.SYSTEM_MANAGE]: '系统管理',
};

function greeting(): string {
  const hour = new Date().getHours();
  if (hour < 6) return '凌晨好';
  if (hour < 12) return '上午好';
  if (hour < 14) return '中午好';
  if (hour < 18) return '下午好';
  return '晚上好';
}

/**
 * 工作台。
 *
 * <p>也是**登录后的默认落地页**（路由守卫在没有 `redirect` 时把用户送到这里）。
 * 内容刻意只放"当前身份是什么" —— 各业务模块的统计卡片要等对应 Sprint 的接口就绪，
 * 现在填假数字比留空更容易让人当真。</p>
 */
const Welcome: React.FC = () => {
  const { initialState } = useModel('@@initialState');
  const user = initialState?.currentUser;
  const roles = user?.roles ?? [];
  const permissions = user?.permissions ?? [];

  return (
    <PageContainer title={false}>
      <ProCard>
        <Descriptions column={1} title={`${greeting()}，${user?.displayName || user?.username || '用户'}`}>
          <Descriptions.Item label="账号">
            {user?.username ?? '-'}
          </Descriptions.Item>
          <Descriptions.Item label="姓名">
            {user?.displayName || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="上次登录">
            {user?.lastLoginAt ?? '-'}
          </Descriptions.Item>
        </Descriptions>
      </ProCard>

      <ProCard title="我的角色" style={{ marginTop: 16 }}>
        {roles.length ? (
          <Space wrap>
            {roles.map((role) => (
              <Tag key={role} color="blue">
                {role}
              </Tag>
            ))}
          </Space>
        ) : (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="当前租户下未授予任何角色"
          />
        )}
      </ProCard>

      <ProCard title="我的权限" style={{ marginTop: 16 }}>
        {permissions.length ? (
          <Space wrap>
            {permissions.map((code) => (
              <Tag key={code}>{permissionLabels[code] ?? code}</Tag>
            ))}
          </Space>
        ) : (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="没有配置任何权限"
          />
        )}
        {/* 把"前端没有真的在拦"这件事写在界面上，而不是只藏在代码注释里 ——
            否则下一个接手的人看到菜单按权限藏起来了，很容易以为权限已经做完 */}
        <Alert
          style={{ marginTop: 16 }}
          type="warning"
          showIcon
          message="界面上的权限控制只决定「看不看得见」，并不阻止直接调用接口"
          description={
            <Paragraph style={{ marginBottom: 0 }}>
              后端尚未落地接口级鉴权（没有 <Text code>@PreAuthorize</Text>，权限码字典也未定义），
              实测任何已登录用户都能访问受保护接口。当前的菜单与按钮隐藏只是显示控制。
            </Paragraph>
          }
        />
      </ProCard>
    </PageContainer>
  );
};

export default Welcome;
