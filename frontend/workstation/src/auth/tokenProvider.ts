import {
  booleanEnv,
  buildLoginRedirectUrl,
  normalizeAuthToken,
  type AuthFailureReason,
} from "./helpers";

type MaybePromise<T> = T | Promise<T>;

export interface AuthFailureContext {
  reason: AuthFailureReason;
  currentUrl: string;
}

export interface WorkstationAuthProvider {
  getAccessToken?: () => MaybePromise<string | null | undefined>;
  refreshAccessToken?: () => MaybePromise<string | null | undefined>;
  redirectToLogin?: (context: AuthFailureContext) => void;
}

declare global {
  interface Window {
    __SMARTCS_WORKSTATION_AUTH__?: WorkstationAuthProvider;
  }
}

const authTokenStorageKey =
  import.meta.env.VITE_WORKSTATION_AUTH_TOKEN_STORAGE_KEY
  || "smartcs_workstation_auth_token";
const loginUrl = import.meta.env.VITE_WORKSTATION_LOGIN_URL || "";
const loginRedirectEnabled = booleanEnv(
  import.meta.env.VITE_WORKSTATION_LOGIN_REDIRECT_ENABLED,
  true,
);

let memoryAuthToken = normalizeAuthToken(
  import.meta.env.VITE_WORKSTATION_AUTH_TOKEN,
);

function browserWindow(): Window | null {
  return typeof window === "undefined" ? null : window;
}

function getAuthProvider(): WorkstationAuthProvider | undefined {
  return browserWindow()?.__SMARTCS_WORKSTATION_AUTH__;
}

function readStoredAuthToken(): string | null {
  const win = browserWindow();
  if (!win) return null;

  try {
    return normalizeAuthToken(win.sessionStorage.getItem(authTokenStorageKey));
  } catch {
    return null;
  }
}

function writeStoredAuthToken(token: string): void {
  const win = browserWindow();
  if (!win) return;

  try {
    win.sessionStorage.setItem(authTokenStorageKey, token);
  } catch {
    // 内部系统浏览器策略可能禁用 storage，此时保留内存 Token 即可。
  }
}

function removeStoredAuthToken(): void {
  const win = browserWindow();
  if (!win) return;

  try {
    win.sessionStorage.removeItem(authTokenStorageKey);
  } catch {
    // 清理失败不阻断后续登录跳转。
  }
}

export function setAccessToken(token: string): void {
  memoryAuthToken = token;
  writeStoredAuthToken(token);
}

export function clearAccessToken(): void {
  memoryAuthToken = null;
  removeStoredAuthToken();
}

export async function getAccessToken(): Promise<string | null> {
  try {
    const providerToken = normalizeAuthToken(
      await getAuthProvider()?.getAccessToken?.(),
    );
    if (providerToken) {
      setAccessToken(providerToken);
      return providerToken;
    }
  } catch {
    // 宿主取 Token 失败时，继续尝试使用本地缓存。
  }

  const storedToken = readStoredAuthToken();
  if (storedToken) {
    memoryAuthToken = storedToken;
    return storedToken;
  }

  return memoryAuthToken;
}

export async function refreshAccessToken(): Promise<string | null> {
  const refresh = getAuthProvider()?.refreshAccessToken;
  if (!refresh) return null;

  const refreshedToken = normalizeAuthToken(await refresh());
  if (!refreshedToken) {
    clearAccessToken();
    return null;
  }

  setAccessToken(refreshedToken);
  return refreshedToken;
}

export function redirectToLogin(reason: AuthFailureReason): void {
  if (!loginRedirectEnabled || !loginUrl) return;

  const win = browserWindow();
  if (!win) return;

  const context: AuthFailureContext = {
    reason,
    currentUrl: win.location.href,
  };

  const provider = getAuthProvider();
  if (provider?.redirectToLogin) {
    provider.redirectToLogin(context);
    return;
  }

  win.location.assign(
    buildLoginRedirectUrl({
      loginUrl,
      currentUrl: context.currentUrl,
      reason,
    }),
  );
}

if (memoryAuthToken) {
  writeStoredAuthToken(memoryAuthToken);
}
