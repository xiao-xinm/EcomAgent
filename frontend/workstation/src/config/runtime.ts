interface WorkstationRuntimeConfig {
  apiBaseUrl: string;
  ticketSseEnabled: boolean;
  authRequired: boolean;
  ticketSseDebounceMs: number;
}

type WindowWorkstationConfig = Partial<WorkstationRuntimeConfig>;

declare global {
  interface Window {
    __SMARTCS_WORKSTATION_CONFIG__?: WindowWorkstationConfig;
  }
}

function windowConfig(): WindowWorkstationConfig {
  return typeof window === "undefined"
    ? {}
    : window.__SMARTCS_WORKSTATION_CONFIG__ || {};
}

function booleanValue(value: unknown, fallback: boolean): boolean {
  if (typeof value === "boolean") return value;
  if (typeof value !== "string") return fallback;
  const normalized = value.trim().toLowerCase();
  if (["1", "true", "yes", "on"].includes(normalized)) return true;
  if (["0", "false", "no", "off"].includes(normalized)) return false;
  return fallback;
}

function positiveNumber(value: unknown, fallback: number): number {
  const numeric = Number(value);
  return Number.isFinite(numeric) && numeric > 0 ? numeric : fallback;
}

const injectedConfig = windowConfig();

export const workstationRuntimeConfig: WorkstationRuntimeConfig = {
  apiBaseUrl:
    injectedConfig.apiBaseUrl
    || import.meta.env.VITE_WORKSTATION_API_BASE_URL
    || "http://localhost:8083",
  ticketSseEnabled: booleanValue(
    injectedConfig.ticketSseEnabled
      ?? import.meta.env.VITE_WORKSTATION_TICKET_SSE_ENABLED,
    false,
  ),
  authRequired: booleanValue(
    injectedConfig.authRequired
      ?? import.meta.env.VITE_WORKSTATION_AUTH_REQUIRED,
    false,
  ),
  ticketSseDebounceMs: positiveNumber(
    injectedConfig.ticketSseDebounceMs
      ?? import.meta.env.VITE_WORKSTATION_TICKET_SSE_DEBOUNCE_MS,
    250,
  ),
};

export function isTicketSseCompatible(
  config: Pick<WorkstationRuntimeConfig, "ticketSseEnabled" | "authRequired"> = workstationRuntimeConfig,
  eventSourceSupported = typeof EventSource !== "undefined",
): boolean {
  // 原生 EventSource 不能附加 Bearer Header，严格鉴权开启时保留现有刷新方式。
  return config.ticketSseEnabled && !config.authRequired && eventSourceSupported;
}
