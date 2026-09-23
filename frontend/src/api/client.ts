// Centralized API Client with JWT authorization and standardized error handling

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "/api/v1";

export interface ApiError {
  code: string;
  message: string;
  details?: Record<string, string>;
}

export async function apiRequest<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<T> {
  const token = localStorage.getItem("nss_token");

  const headers: HeadersInit = {
    "Content-Type": "application/json",
    ...(options.headers || {}),
  };

  if (token) {
    (headers as Record<string, string>)["Authorization"] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    ...options,
    headers,
  });

  if (response.status === 204) {
    return {} as T;
  }

  const data = await response.json().catch(() => ({}));

  if (!response.ok) {
    const error: ApiError = data.error || {
      code: "HTTP_ERROR",
      message: response.statusText || "Request failed",
    };
    throw error;
  }

  return data as T;
}
