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

export interface AuthOptionsResponse {
  googleEnabled: boolean;
  googleWebClientId: string | null;
  googleAndroidClientId: string | null;
  googleIosClientId: string | null;
  demoEnabled: boolean;
}

export const loginWithDemo = (username: string, password: string): Promise<AuthResponse> =>
  apiRequest<AuthResponse>("/api/auth/demo", {
    method: "POST",
    body: JSON.stringify({ username, password })
  });

export const loginWithGoogleToken = (idToken: string): Promise<AuthResponse> =>
  apiRequest<AuthResponse>("/api/auth/google", {
    method: "POST",
    body: JSON.stringify({ idToken })
  });

export const fetchAuthOptions = (): Promise<AuthOptionsResponse> =>
  apiRequest<AuthOptionsResponse>("/api/auth/options", {
    method: "GET"
  });
