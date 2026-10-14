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
import { App, Button, Popconfirm, Tag } from 'antd';
import React, { useRef } from 'react';
import {
  createTenant,
  listTenants,
  removeTenant,
  updateTenant,
  updateTenantStatus,
} from '@/services/sable/tenant';
import type { CreateTenantBody } from '@/services/sable/tenant';

/**
 * 套餐类型。
 *
 * ⚠️ 取值来自后端 `Tenant.planType` 的注释：`BASIC` / `PROFESSIONAL` / `ENTERPRISE`。
 * 当前后端只把它当字符串存，没有配额逻辑跟着它走。
 */
const PLAN_OPTIONS = [
  { label: '基础版', value: 'BASIC' },
  { label: '专业版', value: 'PROFESSIONAL' },
  { label: '旗舰版', value: 'ENTERPRISE' },
];

/**
 * 租户状态。
 *
 * ⚠️ 不是 `ACTIVE/DISABLED` —— 后端 `Tenant.status` 的取值域是
 * `ACTIVE` / `SUSPENDED` / `EXPIRED`。有效期另由 `expiredAt` 表达，不靠改 status。
 */
const STATUS_TEXT: Record<string, string> = {
  ACTIVE: '正常',
  SUSPENDED: '已暂停',
  EXPIRED: '已过期',
};

function statusTag(status?: string) {
  switch (status) {
    case 'ACTIVE':
      return <Tag color="green">{STATUS_TEXT.ACTIVE}</Tag>;
    case 'SUSPENDED':
      return <Tag color="orange">{STATUS_TEXT.SUSPENDED}</Tag>;
    case 'EXPIRED':
      return <Tag color="red">{STATUS_TEXT.EXPIRED}</Tag>;
    default:
      return <Tag>{status ?? '-'}</Tag>;
  }
}

/**
 * 租户管理。
 *
 * <p>这些接口操作的是**平台上的租户清单**（`public.tenant` 表），因此不需要租户上下文 ——
 * 与用户 / 角色管理（都按登录用户的租户路由）正相反。</p>
 */
const TenantPage: React.FC = () => {
  const actionRef = useRef<ActionType | undefined>(undefined);
  const { message, modal } = App.useApp();
  // 显示控制，不是安全边界 —— 后端尚未做接口级鉴权（见 src/access.ts 的说明）
  const access = useAccess();
  const canManage = access.canManageSystem;

  const columns: ProColumns<SableAPI.Tenant>[] = [
    { title: 'ID', dataIndex: 'id', width: 72, search: false },
    { title: '名称', dataIndex: 'name' },
    { title: '编码', dataIndex: 'code', search: false, copyable: true },
    {
      title: '套餐',
      dataIndex: 'planType',
      search: false,
      render: (_, record) => <Tag>{record.planType ?? '-'}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      valueType: 'select',
      valueEnum: {
        ACTIVE: { text: '正常' },
        SUSPENDED: { text: '已暂停' },
        EXPIRED: { text: '已过期' },
      },
      render: (_, record) => statusTag(record.status),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      search: false,
      valueType: 'dateTime',
    },
    ...(canManage
      ? ([
          {
            title: '操作',
            valueType: 'option',
            width: 180,
            render: (_, record) => [
              <ModalForm<CreateTenantBody>
                key="edit"
                title="编辑租户"
                trigger={<a>编辑</a>}
                initialValues={record}
                modalProps={{ destroyOnClose: true }}
                onFinish={async (values) => {
                  await updateTenant(record.id, {
                    name: values.name,
                    planType: values.planType,
                    contactName: values.contactName,
                    contactEmail: values.contactEmail,
                    contactPhone: values.contactPhone,
                  });
                  message.success('已保存');
                  actionRef.current?.reload();
                  return true;
                }}
              >
                <ProFormText
                  name="name"
                  label="名称"
                  rules={[{ required: true, message: '请输入租户名称' }]}
                />
                {/* 编码创建后不可改：它是租户的对外标识，后端更新接口也不接受它 */}
                <ProFormText name="code" label="编码" disabled />
                <ProFormSelect
                  name="planType"
                  label="套餐"
                  options={PLAN_OPTIONS}
                />
                <ProFormText name="contactName" label="联系人" />
                <ProFormText name="contactEmail" label="联系邮箱" />
                <ProFormText name="contactPhone" label="联系电话" />
              </ModalForm>,

              <a
                key="toggle"
                onClick={() =>
                  modal.confirm({
                    title:
                      record.status === 'ACTIVE'
                        ? '确认暂停该租户？'
                        : '确认恢复该租户？',
                    content:
                      record.status === 'ACTIVE'
                        ? '暂停后该租户下的用户将无法登录。'
                        : undefined,
                    onOk: async () => {
                      // 只在这两态之间来回切：EXPIRED 是到期态，由 expiredAt 决定，
                      // 不该被手工"恢复"成正常
                      await updateTenantStatus(
                        record.id,
                        record.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE',
                      );
                      message.success('已更新状态');
                      actionRef.current?.reload();
                    },
                  })
                }
              >
                {record.status === 'ACTIVE' ? '暂停' : '恢复'}
              </a>,

              <Popconfirm
                key="delete"
                title="确认删除该租户？"
                description="后端是软删除：tenant schema 与其中的数据不会被清理。"
                onConfirm={async () => {
                  await removeTenant(record.id);
                  message.success('已删除');
                  actionRef.current?.reload();
                }}
              >
                <a>删除</a>
              </Popconfirm>,
            ],
          },
        ] as ProColumns<SableAPI.Tenant>[])
      : []),
  ];

  return (
    <PageContainer>
      <ProTable<SableAPI.Tenant>
        headerTitle="租户列表"
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        scroll={{ x: 'max-content' }}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
        request={async (params) => {
          const res = await listTenants({
            page: params.current,
            size: params.pageSize,
            // 后端只有一个 keyword 参数，按名称模糊匹配
            keyword: params.name as string | undefined,
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
                <ModalForm<CreateTenantBody>
                  key="create"
                  title="新建租户"
                  trigger={
                    <Button type="primary" icon={<PlusOutlined />}>
                      新建租户
                    </Button>
                  }
                  modalProps={{ destroyOnClose: true }}
                  onFinish={async (values) => {
                    // 这个接口不只是插一行元数据：后端还要建 tenant_{id} schema
                    // 并跑一遍租户级迁移（12 张业务表），耗时明显长于普通写入
                    await createTenant(values);
                    message.success('租户已创建，正在初始化其数据库结构');
                    actionRef.current?.reload();
                    return true;
                  }}
                >
                  <ProFormText
                    name="name"
                    label="名称"
                    rules={[{ required: true, message: '请输入租户名称' }]}
                  />
                  <ProFormText
                    name="code"
                    label="编码"
                    tooltip="唯一标识，创建后不可修改"
                    rules={[{ required: true, message: '请输入租户编码' }]}
                  />
                  <ProFormSelect
                    name="planType"
                    label="套餐"
                    options={PLAN_OPTIONS}
                    initialValue="BASIC"
                  />
                  <ProFormText name="contactName" label="联系人" />
                  <ProFormText name="contactEmail" label="联系邮箱" />
                  <ProFormText name="contactPhone" label="联系电话" />
                </ModalForm>,
              ]
            : []
        }
      />
    </PageContainer>
  );
};

export default TenantPage;
