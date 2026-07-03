export interface RuntimeChatConfig {
  apiBaseUrl: string
  userId: string
  channel: string
  pollingIntervalMs: number
  sessionStorageKey: string
}

type WindowChatConfig = Partial<RuntimeChatConfig>

declare global {
  interface Window {
    __SMARTCS_CHAT_CONFIG__?: WindowChatConfig
  }
}

const params = new URLSearchParams(window.location.search)
const windowConfig = window.__SMARTCS_CHAT_CONFIG__ || {}

function queryText(...keys: string[]): string | null {
  for (const key of keys) {
    const value = params.get(key)
    if (value && value.trim()) return value.trim()
  }
  return null
}

function envText(value: unknown): string | null {
  return typeof value === 'string' && value.trim() ? value.trim() : null
}

function numberOr(value: unknown, fallback: number): number {
  const numeric = Number(value)
  return Number.isFinite(numeric) && numeric > 0 ? numeric : fallback
}

const userId = queryText('userId', 'uid')
  || windowConfig.userId
  || envText(import.meta.env.VITE_USER_ID)
  || 'u1001'
const channel = queryText('channel')
  || windowConfig.channel
  || envText(import.meta.env.VITE_CHANNEL)
  || 'h5'
const defaultSessionStorageKey = channel === 'h5'
  ? 'smartcs_session_id'
  : `smartcs_session_id_${channel}_${userId}`

export const runtimeChatConfig: RuntimeChatConfig = {
  apiBaseUrl: queryText('apiBaseUrl')
    || windowConfig.apiBaseUrl
    || envText(import.meta.env.VITE_API_BASE_URL)
    || 'http://localhost:8080',
  userId,
  channel,
  pollingIntervalMs: numberOr(
    queryText('pollingIntervalMs')
      || windowConfig.pollingIntervalMs
      || import.meta.env.VITE_CHAT_POLLING_INTERVAL_MS,
    3000,
  ),
  sessionStorageKey: queryText('sessionKey', 'sessionStorageKey')
    || windowConfig.sessionStorageKey
    || envText(import.meta.env.VITE_SESSION_STORAGE_KEY)
    || defaultSessionStorageKey,
}
