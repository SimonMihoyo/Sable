import { PlusOutlined } from '@ant-design/icons';
import {
  ModalForm,
  PageContainer,
  ProFormSelect,
  ProFormText,
  ProTable,
} from '@ant-design/pro-components';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';
import { App, Alert, Button, Popconfirm, Tag } from 'antd';
import React, { useEffect, useRef, useState } from 'react';
import { listRoles } from '@/services/sable/role';
import {
  assignRoles,
  createUser,
  listUsers,
  removeUser,
  updateUser,
  updateUserStatus,
} from '@/services/sable/user';
import type { CreateUserBody } from '@/services/sable/user';

/** 账号状态。`LOCKED` 通常由连续登录失败触发，也可手工设置。 */
const STATUS_ENUM = {
  ACTIVE: { text: '正常' },
  LOCKED: { text: '锁定' },
  DISABLED: { text: '停用' },
};

function statusTag(status?: string) {
  switch (status) {
    case 'ACTIVE':
      return <Tag color="green">正常</Tag>;
    case 'LOCKED':
      return <Tag color="orange">锁定</Tag>;
    case 'DISABLED':
      return <Tag color="red">停用</Tag>;
    default:
      return <Tag>{status ?? '-'}</Tag>;
  }
}

/**
 * 用户管理。
 *
 * <p>用户是**租户内**的资源：后端从访问令牌的 `tid` 决定读写哪个 tenant schema，
 * 前端不需要（也不该）传租户。</p>
 */
const UserPage: React.FC = () => {
  const actionRef = useRef<ActionType | undefined>(undefined);
  const { message, modal } = App.useApp();
  const access = useAccess();
  const canManage = access.canManageSystem;

  const [roleOptions, setRoleOptions] = useState<
    { label: string; value: number }[]
  >([]);

  useEffect(() => {
    if (!canManage) return;
    listRoles()
      .then((res) => {
        setRoleOptions(
          (res.data ?? []).map((r) => ({
            label: r.displayName || r.name,
            value: r.id,
          })),
        );
      })
      .catch(() => {
        // 角色列表拿不到不该让整个用户页打不开，分配角色的下拉留空即可
      });
  }, [canManage]);

  const columns: ProColumns<SableAPI.SysUser>[] = [
    { title: 'ID', dataIndex: 'id', width: 72, search: false },
    { title: '用户名', dataIndex: 'username', copyable: true },
    { title: '姓名', dataIndex: 'displayName', search: false },
    { title: '邮箱', dataIndex: 'email', search: false },
    { title: '手机号', dataIndex: 'phone', search: false },
    {
      title: '状态',
      dataIndex: 'status',
      valueType: 'select',
      valueEnum: STATUS_ENUM,
      render: (_, record) => statusTag(record.status),
    },
    {
      title: 'MFA',
      dataIndex: 'mfaEnabled',
      search: false,
      render: (_, record) =>
        record.mfaEnabled ? <Tag color="blue">已开启</Tag> : <span>-</span>,
    },
    {
      title: '上次登录',
      dataIndex: 'lastLoginAt',
      search: false,
      valueType: 'dateTime',
    },
    ...(canManage
      ? ([
          {
            title: '操作',
            valueType: 'option',
            width: 220,
            render: (_, record) => [
              <ModalForm
                key="edit"
                title="编辑用户"
                trigger={<a>编辑</a>}
                initialValues={record}
                modalProps={{ destroyOnClose: true }}
                onFinish={async (values) => {
                  await updateUser(record.id, {
                    displayName: values.displayName,
                    email: values.email,
                    phone: values.phone,
                    department: values.department,
                  });
                  message.success('已保存');
                  actionRef.current?.reload();
                  return true;
                }}
              >
                <ProFormText name="username" label="用户名" disabled />
                <ProFormText name="displayName" label="姓名" />
                <ProFormText name="email" label="邮箱" />
                <ProFormText name="phone" label="手机号" />
                {/* department 只存不读：数据范围权限（本部门可见）在后端尚未实现 */}
                <ProFormText name="department" label="部门" />
              </ModalForm>,

              <ModalForm
                key="roles"
                title={`分配角色 - ${record.displayName || record.username}`}
                trigger={<a>角色</a>}
                modalProps={{ destroyOnClose: true }}
                onFinish={async (values) => {
                  await assignRoles(record.id, (values.roleIds ?? []) as number[]);
                  message.success('角色已更新');
                  actionRef.current?.reload();
                  return true;
                }}
              >
                <Alert
                  style={{ marginBottom: 16 }}
                  type="warning"
                  showIcon
                  message="此操作是覆盖式的"
                  description="提交后会先清空该用户在当前租户下的全部角色再按勾选结果重建。列表接口未装配角色信息，因此这里无法回显已有角色 —— 不勾选即等于收回。"
                />
                <ProFormSelect
                  name="roleIds"
                  label="角色"
                  mode="multiple"
                  options={roleOptions}
                  placeholder="不选择表示收回全部角色"
                />
              </ModalForm>,

              <a
                key="status"
                onClick={() =>
                  modal.confirm({
                    title:
                      record.status === 'ACTIVE'
                        ? '确认停用该用户？'
                        : '确认启用该用户？',
                    onOk: async () => {
                      await updateUserStatus(
                        record.id,
                        record.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE',
                      );
                      message.success('已更新状态');
                      actionRef.current?.reload();
                    },
                  })
                }
              >
                {record.status === 'ACTIVE' ? '停用' : '启用'}
              </a>,

              <Popconfirm
                key="delete"
                title="确认删除该用户？"
                description="后端是软删除。"
                onConfirm={async () => {
                  await removeUser(record.id);
                  message.success('已删除');
                  actionRef.current?.reload();
                }}
              >
                <a>删除</a>
              </Popconfirm>,
            ],
          },
        ] as ProColumns<SableAPI.SysUser>[])
      : []),
  ];

  return (
    <PageContainer>
      <ProTable<SableAPI.SysUser>
        headerTitle="用户列表"
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        scroll={{ x: 'max-content' }}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
        request={async (params) => {
          const res = await listUsers({
            page: params.current,
            size: params.pageSize,
            keyword: params.username as string | undefined,
            status: params.status as string | undefined,
          });
          return {
            data: res.data?.records ?? [],
            total: res.data?.total ?? 0,
            success: res.code === 0,
          };
        }}
        toolBarRender={() =>
          canManage
            ? [
                <ModalForm<CreateUserBody>
                  key="create"
                  title="新建用户"
                  trigger={
                    <Button type="primary" icon={<PlusOutlined />}>
                      新建用户
                    </Button>
                  }
                  modalProps={{ destroyOnClose: true }}
                  onFinish={async (values) => {
                    // 后端会用一个事务写 sys_user + tenant_user + tenant_user_role 三张表
                    await createUser(values);
                    message.success('用户已创建');
                    actionRef.current?.reload();
                    return true;
                  }}
                >
                  <ProFormText
                    name="username"
                    label="用户名"
                    rules={[{ required: true, message: '请输入用户名' }]}
                  />
                  <ProFormText.Password
                    name="password"
                    label="初始密码"
                    rules={[{ required: true, message: '请输入初始密码' }]}
                  />
                  <ProFormText name="displayName" label="姓名" />
                  <ProFormText name="email" label="邮箱" />
                  <ProFormText name="phone" label="手机号" />
                  <ProFormText name="department" label="部门" />
                  <ProFormSelect
                    name="roleIds"
                    label="初始角色"
                    mode="multiple"
                    options={roleOptions}
                  />
                </ModalForm>,
              ]
            : []
        }
      />
    </PageContainer>
  );
};

export default UserPage;
