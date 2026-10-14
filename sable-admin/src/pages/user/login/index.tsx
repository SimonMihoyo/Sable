import { LockOutlined, SafetyCertificateOutlined, UserOutlined } from '@ant-design/icons';
import { LoginForm, ProFormText } from '@ant-design/pro-components';
import { Helmet, history, useModel } from '@umijs/max';
import { Alert, App } from 'antd';
import { createStyles } from 'antd-style';
import React, { startTransition, useState } from 'react';
import { Footer } from '@/components';
import { login } from '@/services/sable/auth';
import { ErrorCode } from '@/services/sable/errorCode';
import { saveTokens } from '@/utils/token';
import Settings from '../../../../config/defaultSettings';

/**
 * 校验登录后的跳转地址，防开放重定向。
 *
 * <p>`redirect` 参数来自 URL，攻击者可以构造 `?redirect=https://evil.com` 把用户
 * 在登录成功后送去钓鱼站 —— 而这时用户刚输入过密码，警惕性最低。所以只接受
 * **同源**的相对路径。</p>
 *
 * <p>`//evil.com` 这种协议相对写法也要拦住：`startsWith('/')` 会放行，但它其实指向外站。</p>
 */
const getSafeRedirectUrl = (redirect: string | null): string => {
  if (!redirect?.startsWith('/')) {
    return '/welcome';
  }
  if (redirect.startsWith('//')) {
    return '/welcome';
  }
  try {
    const parsed = new URL(redirect, window.location.origin);
    if (parsed.origin !== window.location.origin) {
      return '/welcome';
    }
    return `${parsed.pathname}${parsed.search}${parsed.hash}`;
  } catch {
    return '/welcome';
  }
};

/**
 * 把后端错误码翻译成用户看得懂的话。
 *
 * <p>登录是唯一需要**按错误码分支**的接口（别处统一由 `requestErrorConfig` 弹提示），
 * 所以这里用 `skipErrorHandler` 把错误拦下来自己处理，见 {@link Login}。</p>
 */
function describeLoginError(code: number | undefined, fallback?: string): string {
  switch (code) {
    case ErrorCode.AUTH_FAILED:
      // 后端刻意不区分"用户不存在"与"密码错误"，避免账号枚举，前端也不该猜
      return '用户名或密码错误';
    case ErrorCode.ACCOUNT_LOCKED:
      return '账号已被锁定，请稍后再试或联系管理员';
    case ErrorCode.MFA_REQUIRED:
    case ErrorCode.MFA_INVALID:
      // 后端 TOTP 尚未实现，开启 MFA 的账号会走到这里
      return '该账号已开启多因素认证，当前版本暂不支持登录';
    case ErrorCode.PARAM_INVALID:
      return '请填写用户名和密码';
    case ErrorCode.TENANT_CONTEXT_MISSING:
      return '登录会话不完整，请联系管理员检查账号的租户配置';
    default:
      return fallback || '登录失败，请重试';
  }
}

/** 从各类错误对象里取出后端业务码。业务码可能来自 BizError.info，也可能来自真实 HTTP 响应体。 */
function extractErrorCode(error: any): number | undefined {
  return error?.info?.code ?? error?.response?.data?.code;
}

const useStyles = createStyles(({ token }) => {
  return {
    container: {
      display: 'flex',
      flexDirection: 'column',
      height: '100vh',
      overflow: 'auto',
      // 用本地渐变而不是外链背景图：合规产品不该在登录页引用第三方 CDN 资源
      background: `linear-gradient(135deg, ${token.colorBgLayout} 0%, ${token.colorPrimaryBg} 100%)`,
    },
    content: {
      flex: '1',
      padding: '32px 0',
    },
    /** 副标题里那句"非授权访问将被记录"不是装饰，是合规系统的必要告知。 */
    notice: {
      marginTop: 8,
      color: token.colorTextTertiary,
      fontSize: token.fontSizeSM,
      textAlign: 'center',
    },
  };
});

const Login: React.FC = () => {
  const [errorMessage, setErrorMessage] = useState<string>('');
  const [submitting, setSubmitting] = useState<boolean>(false);
  const { initialState, setInitialState } = useModel('@@initialState');
  const { styles } = useStyles();
  const { message } = App.useApp();

  const fetchUserInfo = async () => {
    const userInfo = await initialState?.fetchUserInfo?.();
    if (userInfo) {
      startTransition(() => {
        setInitialState((s) => ({ ...s, currentUser: userInfo }));
      });
    }
  };

  const handleSubmit = async (values: SableAPI.LoginParams) => {
    setErrorMessage('');
    setSubmitting(true);
    try {
      // skipErrorHandler：登录失败要按错误码给出不同提示，不能让全局处理器
      // 统一弹一句"请求失败"了事
      const res = await login(
        { username: values.username.trim(), password: values.password },
        { skipErrorHandler: true },
      );

      if (res?.code !== ErrorCode.SUCCESS || !res.data) {
        setErrorMessage(describeLoginError(res?.code, res?.message));
        return;
      }

      // 令牌落地后才能请求 /me —— 请求拦截器要拿它拼 Authorization 头
      saveTokens(res.data.accessToken, res.data.refreshToken);

      // 后端对开启 MFA 的账号直接报错（见 describeLoginError），这一支当前不会走到；
      // 留着是为了 TOTP 落地时不至于静默跳过二次验证。
      if (res.data.mfaRequired) {
        setErrorMessage('该账号需要多因素认证，当前版本暂不支持');
        return;
      }

      await fetchUserInfo();

      message.success('登录成功');
      const redirect = new URL(window.location.href).searchParams.get('redirect');
      // 用 history.replace 而不是 window.location.href：后者会整页重载，
      // 刚拉到的 initialState（含用户信息）会白拿一次
      history.replace(getSafeRedirectUrl(redirect));
    } catch (error) {
      setErrorMessage(describeLoginError(extractErrorCode(error)));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className={styles.container}>
      <Helmet>
        <title>
          登录{Settings.title && ` - ${Settings.title}`}
        </title>
      </Helmet>
      <div className={styles.content}>
        <LoginForm
          contentStyle={{
            minWidth: 280,
            maxWidth: '75vw',
          }}
          logo={<SafetyCertificateOutlined style={{ fontSize: 44 }} />}
          title={Settings.title}
          subTitle="数据合规与风险管控平台"
          submitter={{
            searchConfig: { submitText: '登 录' },
            submitButtonProps: { loading: submitting, size: 'large' },
          }}
          onFinish={async (values) => {
            await handleSubmit(values as SableAPI.LoginParams);
          }}
        >
          {errorMessage && (
            <Alert
              style={{ marginBottom: 24 }}
              message={errorMessage}
              type="error"
              showIcon
            />
          )}

          <ProFormText
            name="username"
            fieldProps={{
              size: 'large',
              prefix: <UserOutlined />,
              autoComplete: 'username',
            }}
            placeholder="用户名"
            rules={[{ required: true, message: '请输入用户名' }]}
          />
          <ProFormText.Password
            name="password"
            fieldProps={{
              size: 'large',
              prefix: <LockOutlined />,
              autoComplete: 'current-password',
            }}
            placeholder="密码"
            rules={[{ required: true, message: '请输入密码' }]}
          />

          <div className={styles.notice}>
            本系统仅限授权人员使用，所有登录与操作行为均会被记录
          </div>
        </LoginForm>
      </div>
      <Footer />
    </div>
  );
};

export default Login;
