import React, {
  createContext,
  FC,
  PropsWithChildren,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState
} from "react";
import AsyncStorage from "@react-native-async-storage/async-storage";

import {
  fetchAuthOptions,
  loginWithDemo,
  loginWithGoogleToken,
  AuthResponse,
  AuthOptionsResponse,
  AuthUserProfile
} from "../api/authApi";
import { ApiError, UnauthorizedError } from "../api/http";
import { DEV_MODE } from "../utils/config";

const STORAGE_KEY = "@beacon/auth-session";

interface AuthState {
  loading: boolean;
  token: string | null;
  profile: AuthUserProfile | null;
  expiresAt: string | null;
}

interface AuthOptions {
  googleEnabled: boolean;
  webClientId: string | null;
  androidClientId: string | null;
  iosClientId: string | null;
  demoEnabled: boolean;
}

interface AuthContextValue {
  state: AuthState;
  isDevMode: boolean;
  options: AuthOptions;
  loginWithDemo: (username: string, password: string) => Promise<void>;
  loginWithGoogleToken: (idToken: string) => Promise<void>;
  logout: () => Promise<void>;
}

type StoredSession = Omit<AuthState, "loading">;

const initialState: AuthState = {
  loading: true,
  token: null,
  profile: null,
  expiresAt: null
};

const initialOptions: AuthOptions = {
  googleEnabled: false,
  webClientId: null,
  androidClientId: null,
  iosClientId: null,
  demoEnabled: DEV_MODE
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

type AuthProviderProps = PropsWithChildren<unknown>;

const normalizeBoolean = (value: unknown, fallback: boolean): boolean => {
  if (typeof value === "boolean") {
    return value;
  }
  if (typeof value === "string") {
    const trimmed = value.trim().toLowerCase();
    if (trimmed === "true") {
      return true;
    }
    if (trimmed === "false") {
      return false;
    }
  }
  if (typeof value === "number") {
    return value !== 0;
  }
  if (value instanceof Boolean) {
    return value.valueOf();
  }
  return fallback;
};

const mapAuthResponse = (response: AuthResponse): StoredSession => ({
  token: response?.accessToken ?? null,
  profile: response?.profile ?? null,
  expiresAt: response?.expiresAt ?? null
});

const parseStoredSession = (raw: string): StoredSession | null => {
  try {
    const parsed = JSON.parse(raw) as Partial<StoredSession>;
    if (typeof parsed !== "object" || parsed === null) {
      return null;
    }
    return {
      token: typeof parsed.token === "string" ? parsed.token : null,
      profile: (parsed.profile ?? null) as AuthUserProfile | null,
      expiresAt: typeof parsed.expiresAt === "string" ? parsed.expiresAt : null
    };
  } catch (error) {
    console.warn("Failed to parse stored auth session", error);
    return null;
  }
};

export const AuthProvider: FC<AuthProviderProps> = ({ children }) => {
  const [state, setState] = useState<AuthState>(initialState);
  const [options, setOptions] = useState<AuthOptions>(initialOptions);

  useEffect(() => {
    /**
     * Attempt to restore a previously persisted session so users stay logged in between launches.
     */
    const bootstrap = async () => {
      try {
        const stored = await AsyncStorage.getItem(STORAGE_KEY);
        if (!stored) {
          setState((prev) => ({ ...prev, loading: false }));
          return;
        }
        const parsed = parseStoredSession(stored);
        if (parsed?.expiresAt && Date.parse(parsed.expiresAt) < Date.now()) {
          await AsyncStorage.removeItem(STORAGE_KEY);
          setState((prev) => ({ ...prev, loading: false }));
          return;
        }
        setState({
          loading: false,
          token: parsed?.token ?? null,
          profile: parsed?.profile ?? null,
          expiresAt: parsed?.expiresAt ?? null
        });
      } catch (error) {
        console.warn("Failed to bootstrap auth state", error);
        setState((prev) => ({ ...prev, loading: false }));
      }
    };

    bootstrap();
  }, []);

  useEffect(() => {
    let mounted = true;
    /**
     * Load authentication configuration (e.g., Google client IDs) from the backend so
     * the UI can determine which sign-in options to present.
     */
    const loadOptions = async () => {
      try {
        const response: AuthOptionsResponse = await fetchAuthOptions();
        if (!mounted) {
          return;
        }
        const googleEnabled = normalizeBoolean(response?.googleEnabled, false);
        const demoEnabled = normalizeBoolean(response?.demoEnabled, DEV_MODE);
        console.log("[auth] options loaded", {
          googleEnabled,
          demoEnabled,
          rawGoogle: response?.googleEnabled,
          rawDemo: response?.demoEnabled
        });
        setOptions({
          googleEnabled,
          webClientId: googleEnabled ? response?.googleWebClientId ?? null : null,
          androidClientId: googleEnabled ? response?.googleAndroidClientId ?? null : null,
          iosClientId: googleEnabled ? response?.googleIosClientId ?? null : null,
          demoEnabled
        });
      } catch (error) {
        console.warn("Unable to load auth options; falling back to defaults", error);
        if (mounted) {
          setOptions((prev) => ({ ...prev }));
        }
      }
    };

    loadOptions();
    return () => {
      mounted = false;
    };
  }, []);

  /**
   * Persist the current authenticated session both in memory and AsyncStorage so it
   * survives app restarts.
   */
  const persistSession = useCallback(async (session: StoredSession) => {
    const payload: StoredSession = {
      token: session.token,
      profile: session.profile,
      expiresAt: session.expiresAt
    };
    await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
    setState({
      loading: false,
      ...payload
    });
  }, []);

  /**
   * Clear any active session information, effectively logging the user out everywhere.
   */
  const clearSession = useCallback(async () => {
    await AsyncStorage.removeItem(STORAGE_KEY);
    setState({
      loading: false,
      token: null,
      profile: null,
      expiresAt: null
    });
  }, []);

  /**
   * Perform demo-credential authentication and normalize common failure paths.
   */
  const authenticateWithDemo = useCallback(async (username: string, password: string) => {
    try {
      const response = await loginWithDemo(username, password);
      await persistSession(mapAuthResponse(response));
    } catch (error: unknown) {
      if (error instanceof UnauthorizedError || (error instanceof ApiError && error.status === 401)) {
        throw new Error("Invalid credentials");
      }
      throw error;
    }
  }, [persistSession]);

  /**
   * Exchange a Google ID token for a Beacon access token, guarding against known error codes.
   */
  const authenticateWithGoogleToken = useCallback(
    async (idToken: string) => {
      try {
        const response = await loginWithGoogleToken(idToken);
        await persistSession(mapAuthResponse(response));
      } catch (error: unknown) {
        if (error instanceof UnauthorizedError || (error instanceof ApiError && error.status === 401)) {
          throw new Error("Google sign-in failed");
        }
        throw error;
      }
    },
    [persistSession]
  );

  const sanitizedOptions = useMemo<AuthOptions>(
    () => ({
      googleEnabled: normalizeBoolean(options.googleEnabled, false),
      webClientId: options.webClientId,
      androidClientId: options.androidClientId,
      iosClientId: options.iosClientId,
      demoEnabled: normalizeBoolean(options.demoEnabled, DEV_MODE)
    }),
    [options]
  );

  const value = useMemo<AuthContextValue>(
    () => ({
      state,
      isDevMode: sanitizedOptions.demoEnabled,
      options: sanitizedOptions,
      loginWithDemo: authenticateWithDemo,
      loginWithGoogleToken: authenticateWithGoogleToken,
      logout: clearSession
    }),
    [state, sanitizedOptions, authenticateWithDemo, authenticateWithGoogleToken, clearSession]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = (): AuthContextValue => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
};
