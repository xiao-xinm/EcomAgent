import { runtimeChatConfig } from '@/config/runtime'
import {
  buildLoginRedirectUrl,
  normalizeAuthToken,
  type AuthFailureReason,
} from './helpers'

type MaybePromise<T> = T | Promise<T>

export interface AuthFailureContext {
  reason: AuthFailureReason
  currentUrl: string
}

export interface SmartCsAuthProvider {
  getAccessToken?: () => MaybePromise<string | null | undefined>
  refreshAccessToken?: () => MaybePromise<string | null | undefined>
  redirectToLogin?: (context: AuthFailureContext) => void
}

declare global {
  interface Window {
    __SMARTCS_AUTH__?: SmartCsAuthProvider
  }
}

let memoryAuthToken = normalizeAuthToken(runtimeChatConfig.authToken)

function browserWindow(): Window | null {
  return typeof window === 'undefined' ? null : window
}

function getAuthProvider(): SmartCsAuthProvider | undefined {
  return browserWindow()?.__SMARTCS_AUTH__
}

function readStoredAuthToken(): string | null {
  const win = browserWindow()
  if (!win) return null

  try {
    return normalizeAuthToken(win.sessionStorage.getItem(runtimeChatConfig.authTokenStorageKey))
  } catch {
    return null
  }
}

function writeStoredAuthToken(token: string): void {
  const win = browserWindow()
  if (!win) return

  try {
    win.sessionStorage.setItem(runtimeChatConfig.authTokenStorageKey, token)
  } catch {
    // 浏览器隐私模式或宿主 WebView 禁用 storage 时，只保留内存 Token。
  }
}

function removeStoredAuthToken(): void {
  const win = browserWindow()
  if (!win) return

  try {
    win.sessionStorage.removeItem(runtimeChatConfig.authTokenStorageKey)
  } catch {
    // 忽略 storage 清理失败，避免阻断后续登录跳转。
  }
}

export function setAccessToken(token: string): void {
  memoryAuthToken = token
  writeStoredAuthToken(token)
}

export function clearAccessToken(): void {
  memoryAuthToken = null
  removeStoredAuthToken()
}

export async function getAccessToken(): Promise<string | null> {
  try {
    const providerToken = normalizeAuthToken(await getAuthProvider()?.getAccessToken?.())
    if (providerToken) {
      setAccessToken(providerToken)
      return providerToken
    }
  } catch {
    // 宿主获取 Token 失败时，继续尝试使用页面侧缓存的 Token。
  }

  const storedToken = readStoredAuthToken()
  if (storedToken) {
    memoryAuthToken = storedToken
    return storedToken
  }

  return memoryAuthToken
}

export async function refreshAccessToken(): Promise<string | null> {
  const refresh = getAuthProvider()?.refreshAccessToken
  if (!refresh) return null

  const refreshedToken = normalizeAuthToken(await refresh())
  if (!refreshedToken) {
    clearAccessToken()
    return null
  }

  setAccessToken(refreshedToken)
  return refreshedToken
}

export function redirectToLogin(reason: AuthFailureReason): void {
  if (!runtimeChatConfig.loginRedirectEnabled || !runtimeChatConfig.loginUrl) return

  const win = browserWindow()
  if (!win) return

  const context: AuthFailureContext = {
    reason,
    currentUrl: win.location.href,
  }

  const provider = getAuthProvider()
  if (provider?.redirectToLogin) {
    provider.redirectToLogin(context)
    return
  }

  win.location.assign(
    buildLoginRedirectUrl({
      loginUrl: runtimeChatConfig.loginUrl,
      currentUrl: context.currentUrl,
      reason,
    }),
  )
}

export function isSseAuthCompatible(): boolean {
  // EventSource 不能附加 Authorization Header；严格鉴权时只允许 Cookie 鉴权的 SSE。
  return !runtimeChatConfig.authRequired || runtimeChatConfig.sseAuthMode === 'cookie'
}

if (memoryAuthToken) {
  writeStoredAuthToken(memoryAuthToken)
}
