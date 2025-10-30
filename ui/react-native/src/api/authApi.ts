import { apiRequest } from "./http";

export interface AuthUserProfile {
  subject: string;
  email: string;
  displayName: string;
  avatarUrl?: string | null;
}

export interface AuthResponse {
  accessToken: string;
  expiresAt: string | null;
  profile: AuthUserProfile | null;
}

export const loginWithDemo = (username: string, password: string): Promise<AuthResponse> =>
  apiRequest<AuthResponse>("/api/auth/demo", {
    method: "POST",
    body: JSON.stringify({ username, password }),
    credentials: "include"
  });

export const fetchSession = (): Promise<AuthResponse> =>
  apiRequest<AuthResponse>("/api/auth/session", {
    method: "GET",
    credentials: "include"
  });

export const logoutSession = (): Promise<void> =>
  apiRequest<void>("/api/auth/logout", {
    method: "POST",
    credentials: "include"
  });
