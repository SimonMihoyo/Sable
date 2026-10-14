import { createStyles } from 'antd-style';
import React from 'react';

const useStyles = createStyles(({ token, css }) => ({
  footer: css`
    padding: 16px 24px;
    text-align: center;
    color: ${token.colorTextDescription};
    font-size: ${token.fontSizeSM}px;
    line-height: ${token.lineHeight};
    background: transparent;
  `,
}));

/**
 * 页脚。
 *
 * 模板原版用来展示 Ant Design Pro 的版本与仓库链接（还会去 package.json 里
 * 解析 repository 地址）。对一个合规产品来说那些都不该出现 —— 页脚只需要标明
 * 系统身份与版权年份。
 */
const Footer: React.FC = () => {
  const { styles } = useStyles();
  const year = new Date().getFullYear();

  return (
    <div className={styles.footer}>数据合规管理系统 &copy; {year}</div>
  );
};

export default Footer;
