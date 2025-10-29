const normalizeUrl = (value?: string | null): string => {
  if (!value) {
    return "";
  }
  return value.endsWith("/") ? value.slice(0, -1) : value;
};

const env =
  ((globalThis as { process?: { env?: Record<string, string | undefined> } }).process?.env ??
    {}) as Record<string, string | undefined>;

export const API_BASE_URL: string = normalizeUrl(env.EXPO_PUBLIC_API_BASE_URL ?? "");

export const DEV_MODE: boolean =
  (env.EXPO_PUBLIC_DEV_MODE ?? "true").toLowerCase() !== "false";

export const FEATURED_OFFICIAL_SOURCE_ID: string =
  env.EXPO_PUBLIC_FEATURED_OFFICIAL_SOURCE_ID ?? "";

export interface AppConfig {
  apiBaseUrl: string;
  devMode: boolean;
  featuredOfficialSourceId: string | null;
}

/**
 * Snapshot of public Expo environment variables in a convenient typed shape for UI consumers.
 */
export const CONFIG: AppConfig = {
  apiBaseUrl: API_BASE_URL,
  devMode: DEV_MODE,
  featuredOfficialSourceId: FEATURED_OFFICIAL_SOURCE_ID || null
};
