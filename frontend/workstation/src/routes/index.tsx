import React, { Suspense, lazy } from "react";
import { Spin } from "antd";
import { Routes, Route, Navigate } from "react-router-dom";
import BasicLayout from "../layouts/BasicLayout";

const TicketList = lazy(() => import("../pages/TicketList"));
const TicketDetailPage = lazy(() => import("../pages/TicketDetail"));
const FaqManagement = lazy(() => import("../pages/FaqManagement"));
const NotificationEvents = lazy(() => import("../pages/NotificationEvents"));

const RouteLoading: React.FC = () => (
  <div
    style={{ minHeight: 240, display: "grid", placeItems: "center" }}
    aria-label="页面加载中"
  >
    <Spin size="small" />
  </div>
);

const AppRoutes: React.FC = () => (
  <Routes>
    <Route path="/" element={<BasicLayout />}>
      <Route index element={<Navigate to="/tickets" replace />} />
      <Route
        path="tickets"
        element={(
          <Suspense fallback={<RouteLoading />}>
            <TicketList />
          </Suspense>
        )}
      />
      <Route
        path="tickets/:ticketId"
        element={(
          <Suspense fallback={<RouteLoading />}>
            <TicketDetailPage />
          </Suspense>
        )}
      />
      <Route
        path="knowledge/faq"
        element={(
          <Suspense fallback={<RouteLoading />}>
            <FaqManagement />
          </Suspense>
        )}
      />
      <Route
        path="notifications/events"
        element={(
          <Suspense fallback={<RouteLoading />}>
            <NotificationEvents />
          </Suspense>
        )}
      />
    </Route>
  </Routes>
);

export default AppRoutes;
