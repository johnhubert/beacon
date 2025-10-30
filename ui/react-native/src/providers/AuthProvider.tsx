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

import { AuthResponse, AuthUserProfile, fetchSession, loginWithDemo, logoutSession } from "../api/authApi";
import { ApiError, UnauthorizedError } from "../api/http";

const STORAGE_KEY = "@beacon/auth-session";

interface AuthState {
  loading: boolean;
  token: string | null;
  profile: AuthUserProfile | null;
  expiresAt: string | null;
}

interface AuthContextValue {
  state: AuthState;
  loginWithDemo: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

type StoredSession = Omit<AuthState, "loading">;

const initialState: AuthState = {
  loading: false,
  token: null,
  profile: null,
  expiresAt: null
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

type AuthProviderProps = PropsWithChildren<unknown>;

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
    console.warn("[auth] failed to parse stored session", error);
    return null;
  }
};

export const AuthProvider: FC<AuthProviderProps> = ({ children }) => {
  const [state, setState] = useState<AuthState>(initialState);

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

  const clearSession = useCallback(async () => {
    try {
      await logoutSession();
    } catch (error) {
      console.warn("[auth] logout session request failed", error);
    }
    await AsyncStorage.removeItem(STORAGE_KEY);
    setState({
      loading: false,
      token: null,
      profile: null,
      expiresAt: null
    });
  }, []);

  useEffect(() => {
    let mounted = true;

    const bootstrap = async () => {
      console.log("[auth] bootstrap start");
      try {
        const remoteSession = await fetchSession();
        if (mounted && remoteSession) {
          await persistSession(mapAuthResponse(remoteSession));
          console.log("[auth] bootstrap restored session from server");
          return;
        }
      } catch (error) {
        if (!(error instanceof UnauthorizedError)) {
          console.warn("[auth] remote session lookup failed; clearing any persisted session", error);
        }
        await AsyncStorage.removeItem(STORAGE_KEY);
      }

      if (!mounted) {
        return;
      }
      try {
        const stored = await AsyncStorage.getItem(STORAGE_KEY);
        const parsed = stored ? parseStoredSession(stored) : null;
        if (parsed) {
          setState({
            loading: false,
            ...parsed
          });
          console.log("[auth] bootstrap restored session from storage");
        } else {
          setState((prev) => ({ ...prev, loading: false }));
          console.log("[auth] bootstrap no session found");
        }
      } catch (storageError) {
        console.error("[auth] unable to restore session from storage", storageError);
        setState((prev) => ({ ...prev, loading: false }));
      }
    };

    void bootstrap();
    return () => {
      mounted = false;
    };
  }, [persistSession]);

  const authenticateWithDemo = useCallback(
    async (username: string, password: string) => {
      try {
        const response = await loginWithDemo(username, password);
        await persistSession(mapAuthResponse(response));
      } catch (error: unknown) {
        if (error instanceof UnauthorizedError || (error instanceof ApiError && error.status === 401)) {
          throw new Error("Invalid credentials");
        }
        console.error("[auth] unexpected demo login failure", error);
        throw error instanceof Error ? error : new Error("Demo login failed");
      }
    },
    [persistSession]
  );

  const value = useMemo<AuthContextValue>(
    () => ({
      state,
      loginWithDemo: authenticateWithDemo,
      logout: clearSession
    }),
    [state, authenticateWithDemo, clearSession]
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
