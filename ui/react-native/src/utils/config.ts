const normalizeUrl = (value?: string | null): string => {
  // Sanitize trailing slashes so downstream callers can safely concatenate paths.
  if (!value) {
    return "";
  }
  return value.endsWith("/") ? value.slice(0, -1) : value;
};

const env =
  ((globalThis as { process?: { env?: Record<string, string | undefined> } }).process?.env ??
    {}) as Record<string, string | undefined>;

export const API_BASE_URL = normalizeUrl(env.EXPO_PUBLIC_API_BASE_URL ?? "");
export const DEV_MODE = (env.EXPO_PUBLIC_DEV_MODE ?? "true").toLowerCase() !== "false";
export const FEATURED_OFFICIAL_SOURCE_ID =
  env.EXPO_PUBLIC_FEATURED_OFFICIAL_SOURCE_ID ?? "";

export interface AppConfig {
  apiBaseUrl: string;
  devMode: boolean;
  featuredOfficialSourceId: string | null;
}

export const CONFIG: AppConfig = {
  apiBaseUrl: API_BASE_URL,
  devMode: DEV_MODE,
  featuredOfficialSourceId: FEATURED_OFFICIAL_SOURCE_ID || null
};
