import React from "react";
import { Layout, Menu, Typography } from "antd";
import {
  DesktopOutlined,
  UnorderedListOutlined,
} from "@ant-design/icons";
import { Outlet, useNavigate, useLocation } from "react-router-dom";

const { Header, Sider, Content } = Layout;
const { Title } = Typography;

const menuItems = [
  {
    key: "/tickets",
    icon: <UnorderedListOutlined />,
    label: "工单列表",
  },
];

const BasicLayout: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();

  const selectedKey = location.pathname.startsWith("/tickets")
    ? "/tickets"
    : "/tickets";

  return (
    <Layout style={{ minHeight: "100vh" }}>
      <Sider collapsible theme="dark" width={200}>
        <div
          style={{
            height: 48,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            gap: 8,
          }}
        >
          <DesktopOutlined style={{ color: "#fff", fontSize: 18 }} />
          <Title level={5} style={{ color: "#fff", margin: 0, fontSize: 14 }}>
            SmartCS 工作台
          </Title>
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selectedKey]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Header
          style={{
            background: "#fff",
            padding: "0 24px",
            display: "flex",
            alignItems: "center",
            borderBottom: "1px solid #f0f0f0",
          }}
        >
          <Title level={4} style={{ margin: 0 }}>
            人工坐席工作台
          </Title>
        </Header>
        <Content style={{ margin: 16, background: "#fff", borderRadius: 4 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
};

export default BasicLayout;
