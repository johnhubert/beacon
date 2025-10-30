import { API_BASE_URL } from "../utils/config";

export interface ApiRequestOptions extends Omit<RequestInit, "headers"> {
  token?: string;
  headers?: Record<string, string>;
}

export class ApiError extends Error {
  public readonly status: number;

  public readonly url?: string;

  public readonly details?: unknown;

  constructor(message: string, status: number, url?: string, details?: unknown) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.url = url;
    this.details = details;
  }
}

export class UnauthorizedError extends ApiError {
  constructor(message = "Unauthorized", url?: string, details?: unknown) {
    super(message, 401, url, details);
    this.name = "UnauthorizedError";
  }
}

const buildUrl = (path: string): string => {
  if (!API_BASE_URL || path.startsWith("http")) {
    return path;
  }
  return `${API_BASE_URL}${path}`;
};

export const apiRequest = async <TResponse = unknown>(
  path: string,
  options: ApiRequestOptions = {}
): Promise<TResponse> => {
  // Centralized wrapper so we consistently inject auth headers, parse JSON, and surface diagnostics.
  const { token, headers, ...rest } = options;
  const url = buildUrl(path);

  const finalHeaders: Record<string, string> = {
    Accept: "application/json",
    ...(headers ?? {})
  };

  if (rest.body && !finalHeaders["Content-Type"]) {
    finalHeaders["Content-Type"] = "application/json";
  }

  if (token) {
    finalHeaders.Authorization = `Bearer ${token}`;
  }

  let response: Response;
  try {
    response = await fetch(url, {
      ...rest,
      headers: finalHeaders
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : "Network request failed";
    console.error("[api] network request failed", { url, message });
    throw new ApiError(message, 0, url);
  }

  if (response.status === 204) {
    return null as TResponse;
  }

  const text = await response.text();
  let data: unknown = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch (parseError) {
      console.warn("[api] response is not valid JSON", { url, text });
      data = text;
    }
  }

  if (!response.ok) {
    const parsed = typeof data === "object" && data !== null ? (data as Record<string, unknown>) : undefined;
    const message =
      (parsed?.message as string | undefined) ?? (parsed?.error as string | undefined) ?? response.statusText;
    if (response.status === 401) {
      // Unauthorized is a common control-flow path (e.g., unauthenticated session bootstrap),
      // so skip noisy error logging while still surfacing the typed exception.
      throw new UnauthorizedError(message || "Unauthorized", url, data);
    }
    console.error("[api] request returned error", {
      url,
      status: response.status,
      statusText: response.statusText,
      body: data
    });
    throw new ApiError(message || "Request failed", response.status, url, data);
  }

  return data as TResponse;
};
