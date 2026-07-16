import { useEffect, useRef } from "react";
import {
  isTicketSseCompatible,
  workstationRuntimeConfig,
} from "../config/runtime";
import {
  createTicketEventSource,
  parseTicketChangedEvent,
} from "../services/ticketEvents";

interface UseTicketEventsOptions {
  ticketId?: string;
  onRefresh: () => void;
}

/** 将短时间内的 SSE 提醒合并为一次现有接口刷新，避免事件风暴重复请求。 */
export function useTicketEvents({ ticketId, onRefresh }: UseTicketEventsOptions): void {
  const refreshRef = useRef(onRefresh);

  useEffect(() => {
    refreshRef.current = onRefresh;
  }, [onRefresh]);

  useEffect(() => {
    if (!isTicketSseCompatible()) return undefined;

    let refreshTimer: number | undefined;
    let eventSource: EventSource;
    const scheduleRefresh = () => {
      if (refreshTimer) {
        window.clearTimeout(refreshTimer);
      }
      refreshTimer = window.setTimeout(() => {
        refreshTimer = undefined;
        refreshRef.current();
      }, workstationRuntimeConfig.ticketSseDebounceMs);
    };

    try {
      eventSource = createTicketEventSource();
    } catch {
      return undefined;
    }

    eventSource.addEventListener("stream.ready", scheduleRefresh);
    eventSource.addEventListener("ticket.changed", (event) => {
      const changed = parseTicketChangedEvent((event as MessageEvent<string>).data);
      if (!changed || (ticketId && changed.ticketId !== ticketId)) {
        return;
      }
      scheduleRefresh();
    });
    // 不主动关闭错误连接，交给原生 EventSource 自动重连；现有手动刷新仍是降级路径。
    eventSource.onerror = () => undefined;

    return () => {
      if (refreshTimer) {
        window.clearTimeout(refreshTimer);
      }
      eventSource.close();
    };
  }, [ticketId]);
}
