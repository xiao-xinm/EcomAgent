export type AuthFailureReason = "expired" | "forbidden";

export interface LoginRedirectOptions {
  loginUrl: string;
  currentUrl: string;
  reason: AuthFailureReason;
  redirectParam?: string;
  reasonParam?: string;
}

export function normalizeAuthToken(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

export function resolveAuthFailureReason(
  code?: string,
  status?: number,
): AuthFailureReason | null {
  if (code === "1002" || status === 401) return "expired";
  if (code === "1003" || status === 403) return "forbidden";
  return null;
}

export function booleanEnv(value: unknown, fallback: boolean): boolean {
  if (typeof value === "boolean") return value;
  if (typeof value !== "string") return fallback;

  const normalized = value.trim().toLowerCase();
  if (["1", "true", "yes", "on"].includes(normalized)) return true;
  if (["0", "false", "no", "off"].includes(normalized)) return false;
  return fallback;
}

export function buildLoginRedirectUrl({
  loginUrl,
  currentUrl,
  reason,
  redirectParam = "redirect",
  reasonParam = "reason",
}: LoginRedirectOptions): string {
  const url = new URL(loginUrl, currentUrl);
  url.searchParams.set(redirectParam, currentUrl);
  url.searchParams.set(reasonParam, reason);
  return url.toString();
}
