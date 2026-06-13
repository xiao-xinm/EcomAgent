import React from "react";
import { Routes, Route, Navigate } from "react-router-dom";
import BasicLayout from "../layouts/BasicLayout";
import TicketList from "../pages/TicketList";
import TicketDetailPage from "../pages/TicketDetail";

const AppRoutes: React.FC = () => (
  <Routes>
    <Route path="/" element={<BasicLayout />}>
      <Route index element={<Navigate to="/tickets" replace />} />
      <Route path="tickets" element={<TicketList />} />
      <Route path="tickets/:ticketId" element={<TicketDetailPage />} />
    </Route>
  </Routes>
);

export default AppRoutes;
