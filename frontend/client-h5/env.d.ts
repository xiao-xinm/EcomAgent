/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
  readonly VITE_USER_ID: string
  readonly VITE_CHANNEL: string
  readonly VITE_PORT: string
  readonly VITE_CHAT_POLLING_INTERVAL_MS: string
  readonly VITE_CHAT_SSE_ENABLED: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
