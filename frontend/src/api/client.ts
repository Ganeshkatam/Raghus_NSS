// Centralized API Client with JWT authorization and standardized error handling

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "/api/v1";

export interface ApiError {
  code: string;
  message: string;
  details?: Record<string, string>;
}

export function sanitizeErrorMessage(message: string | undefined, statusCode?: number): string {
  if (!message || typeof message !== "string") {
    if (statusCode === 401) return "Invalid email or password. Please verify your credentials.";
    if (statusCode === 403) return "You do not have permission to perform this action.";
    if (statusCode === 404) return "The requested record could not be found.";
    return "An unexpected error occurred. Please try again.";
  }

  const lower = message.toLowerCase();
  const technicalSignatures = [
    "jdbc",
    "resultset",
    "sql",
    "bad value for type",
    "column [",
    "could not extract",
    "psqlexception",
    "sqlexception",
    "hibernate",
    "org.hibernate",
    "org.springframework",
    "internalauthenticationserviceexception",
    "nullpointerexception",
    "dataexception",
    "syntax error",
    "constraint",
    "hikari",
    "connection refused",
    "fatal:",
    "stacktrace",
  ];

  const hasTechnicalLeak = technicalSignatures.some((sig) => lower.includes(sig));

  if (hasTechnicalLeak) {
    if (statusCode === 401) {
      return "Invalid email or password. Please verify your credentials.";
    }
    if (statusCode && statusCode >= 500) {
      return "The NSS service is temporarily unavailable. Please try again shortly.";
    }
    return "Unable to complete request. Please verify the entered details or try again later.";
  }

  return message;
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

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${endpoint}`, {
      ...options,
      headers,
    });
  } catch (_networkErr: unknown) {
    throw {
      code: "NETWORK_ERROR",
      message: "Unable to connect to the NSS server. Please check your internet connection and try again.",
    } as ApiError;
  }

  if (response.status === 204) {
    return {} as T;
  }

  const data = await response.json().catch(() => ({}));

  if (!response.ok) {
    const rawError = data?.error;
    const rawMessage = rawError?.message || data?.message || response.statusText || "Request failed";
    const sanitizedMsg = sanitizeErrorMessage(rawMessage, response.status);

    const error: ApiError = {
      code: rawError?.code || (response.status === 401 ? "AUTHENTICATION_REQUIRED" : "HTTP_ERROR"),
      message: sanitizedMsg,
      details: rawError?.details,
    };
    throw error;
  }

  return data as T;
}
