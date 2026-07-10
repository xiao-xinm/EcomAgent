/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_WORKSTATION_API_BASE_URL: string;
  readonly VITE_WORKSTATION_OPERATOR_ID: string;
  readonly VITE_WORKSTATION_ROLES: string;
  readonly VITE_WORKSTATION_AUTH_TOKEN: string;
  readonly VITE_WORKSTATION_AUTH_TOKEN_STORAGE_KEY: string;
  readonly VITE_WORKSTATION_LOGIN_URL: string;
  readonly VITE_WORKSTATION_LOGIN_REDIRECT_ENABLED: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
