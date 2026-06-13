import React from "react";
import { BrowserRouter } from "react-router-dom";
import { ConfigProvider } from "antd";
import zhCN from "antd/locale/zh_CN";
import AppRoutes from "./routes";

const App: React.FC = () => (
  <ConfigProvider
    locale={zhCN}
    theme={{
      token: {
        colorPrimary: "#1677ff",
        borderRadius: 4,
      },
    }}
  >
    <BrowserRouter>
      <AppRoutes />
    </BrowserRouter>
  </ConfigProvider>
);

export default App;
