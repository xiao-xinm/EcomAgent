/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
  readonly VITE_USER_ID: string
  readonly VITE_USER_ROLES: string
  readonly VITE_AUTH_TOKEN: string
  readonly VITE_AUTH_TOKEN_STORAGE_KEY: string
  readonly VITE_AUTH_REQUIRED: string
  readonly VITE_LOGIN_URL: string
  readonly VITE_LOGIN_REDIRECT_ENABLED: string
  readonly VITE_CHANNEL: string
  readonly VITE_PORT: string
  readonly VITE_CHAT_POLLING_INTERVAL_MS: string
  readonly VITE_CHAT_SSE_ENABLED: string
  readonly VITE_CHAT_SSE_AUTH_MODE: string
  readonly VITE_SESSION_STORAGE_KEY: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
