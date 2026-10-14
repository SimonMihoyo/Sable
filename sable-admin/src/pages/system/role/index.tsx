import { PlusOutlined } from '@ant-design/icons';
import {
  ModalForm,
  PageContainer,
  ProFormCheckbox,
  ProFormText,
  ProFormTextArea,
  ProTable,
} from '@ant-design/pro-components';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';
import { Alert, App, Button, Popconfirm, Tag } from 'antd';
import React, { useRef } from 'react';
import { Permission } from '@/access';
import {
  createRole,
  listRoles,
  removeRole,
  setRolePermissions,
  updateRole,
} from '@/services/sable/role';
import type { SaveRoleBody } from '@/services/sable/role';

/**
 * 可选权限码。
 *
 * <p>取的是 `src/access.ts` 里那份与后端 schema / dev 种子数据对齐的常量。
 * 后端目前**没有权限码字典**，`role.permissions` 是自由字符串 —— 这里的选项
 * 只是"当前前后端共识的合法取值"，并不构成约束。</p>
 */
const PERMISSION_OPTIONS = [
  { label: '查看数据源', value: Permission.DATA_SOURCE_READ },
  { label: '维护数据源', value: Permission.DATA_SOURCE_WRITE },
  { label: '查看审计日志', value: Permission.AUDIT_LOG_READ },
  { label: '导出审计日志', value: Permission.AUDIT_LOG_EXPORT },
  { label: '查看合规评估', value: Permission.ASSESSMENT_READ },
  { label: '维护合规评估', value: Permission.ASSESSMENT_WRITE },
  { label: '查看报表', value: Permission.REPORT_READ },
  { label: '导出报表', value: Permission.REPORT_EXPORT },
  { label: '系统管理', value: Permission.SYSTEM_MANAGE },
];

/** 把 `role.permissions`（后端是 JSON 字符串）解析成数组。解析失败返回空数组。 */
function parsePermissions(raw?: string): string[] {
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.map(String) : [];
  } catch {
    // 库里存了非法 JSON 时不让整个页面崩掉 —— 展示为空，由用户重新保存修正
    return [];
  }
}

/** 权限码 → 中文名，仅用于列表展示。 */
const permissionLabel = (code: string) =>
  PERMISSION_OPTIONS.find((o) => o.value === code)?.label ?? code;

const RolePage: React.FC = () => {
  const actionRef = useRef<ActionType | undefined>(undefined);
  const { message } = App.useApp();
  const access = useAccess();
  const canManage = access.canManageSystem;

  const columns: ProColumns<SableAPI.Role>[] = [
    { title: 'ID', dataIndex: 'id', width: 72 },
    { title: '标识', dataIndex: 'name', copyable: true },
    { title: '名称', dataIndex: 'displayName' },
    { title: '描述', dataIndex: 'description', ellipsis: true, search: false },
    {
      title: '类型',
      dataIndex: 'isSystem',
      search: false,
      render: (_, record) =>
        record.isSystem ? <Tag color="gold">系统预置</Tag> : <Tag>自定义</Tag>,
    },
    {
      title: '权限',
      dataIndex: 'permissions',
      search: false,
      render: (_, record) => {
        const codes = parsePermissions(record.permissions);
        if (!codes.length) return <span>-</span>;
        return (
          <>
            {codes.slice(0, 3).map((code) => (
              <Tag key={code}>{permissionLabel(code)}</Tag>
            ))}
            {codes.length > 3 && <Tag>+{codes.length - 3}</Tag>}
          </>
        );
      },
    },
    ...(canManage
      ? ([
          {
            title: '操作',
            valueType: 'option',
            width: 220,
            render: (_, record) => [
              <ModalForm<SaveRoleBody>
                key="edit"
                title="编辑角色"
                trigger={<a>编辑</a>}
                initialValues={record}
                modalProps={{ destroyOnClose: true }}
                onFinish={async (values) => {
                  await updateRole(record.id, values);
                  message.success('已保存');
                  actionRef.current?.reload();
                  return true;
                }}
              >
                <ProFormText
                  name="name"
                  label="标识"
                  rules={[{ required: true, message: '请输入角色标识' }]}
                />
                <ProFormText name="displayName" label="名称" />
                <ProFormTextArea name="description" label="描述" />
              </ModalForm>,

              <ModalForm
                key="permissions"
                title={`配置权限 - ${record.displayName || record.name}`}
                trigger={<a>权限</a>}
                initialValues={{ permissions: parsePermissions(record.permissions) }}
                modalProps={{ destroyOnClose: true }}
                onFinish={async (values) => {
                  await setRolePermissions(
                    record.id,
                    (values.permissions ?? []) as string[],
                  );
                  message.success('权限已更新');
                  actionRef.current?.reload();
                  return true;
                }}
              >
                <ProFormCheckbox.Group
                  name="permissions"
                  label="权限项"
                  options={PERMISSION_OPTIONS}
                />
              </ModalForm>,

              // 系统预置角色不给删除入口。后端当前**没有**强制这条（代码里是 TODO），
              // 所以这里只是界面约束
              record.isSystem ? (
                <span key="locked" style={{ color: 'rgba(0,0,0,0.25)' }}>
                  删除
                </span>
              ) : (
                <Popconfirm
                  key="delete"
                  title="确认删除该角色？"
                  description="已授予该角色的用户会随之失去对应权限。"
                  onConfirm={async () => {
                    await removeRole(record.id);
                    message.success('已删除');
                    actionRef.current?.reload();
                  }}
                >
                  <a>删除</a>
                </Popconfirm>
              ),
            ],
          },
        ] as ProColumns<SableAPI.Role>[])
      : []),
  ];

  return (
    <PageContainer>
      <Alert
        style={{ marginBottom: 16 }}
        type="info"
        showIcon
        message="权限配置目前只影响界面展示"
        description="后端尚未实现接口级鉴权，改动角色权限不会真正拦住任何接口调用。"
      />
      <ProTable<SableAPI.Role>
        headerTitle="角色列表"
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        // 这个接口返回裸数组、没有分页参数，所以关掉分页与搜索（客户端过滤由 ProTable 负责）
        search={false}
        pagination={false}
        options={false}
        request={async () => {
          const res = await listRoles();
          return { data: res.data ?? [], success: res.code === 0 };
        }}
        toolBarRender={() =>
          canManage
            ? [
                <ModalForm<SaveRoleBody>
                  key="create"
                  title="新建角色"
                  trigger={
                    <Button type="primary" icon={<PlusOutlined />}>
                      新建角色
                    </Button>
                  }
                  modalProps={{ destroyOnClose: true }}
                  onFinish={async (values) => {
                    // 租户由后端从访问令牌的 tid 取；权限初始为空数组，
                    // 创建后再用"权限"按钮配置
                    await createRole(values);
                    message.success('角色已创建，请继续配置其权限');
                    actionRef.current?.reload();
                    return true;
                  }}
                >
                  <ProFormText
                    name="name"
                    label="标识"
                    tooltip="租户内唯一，如 COMPLIANCE_OFFICER"
                    rules={[{ required: true, message: '请输入角色标识' }]}
                  />
                  <ProFormText name="displayName" label="名称" />
                  <ProFormTextArea name="description" label="描述" />
                </ModalForm>,
              ]
            : []
        }
      />
    </PageContainer>
  );
};

export default RolePage;
