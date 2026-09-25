// Centralized API Client with JWT authorization, bulletproof error filtering, and action dispatching

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "/api/v1";

export type ErrorCategory =
  | "AUTH_INVALID"
  | "AUTH_EXPIRED"
  | "FORBIDDEN"
  | "NOT_FOUND"
  | "CONFLICT"
  | "VALIDATION"
  | "SERVER_WARMUP"
  | "SERVER_ERROR"
  | "NETWORK_OFFLINE"
  | "UNKNOWN";

export interface ApiError {
  code: string;
  message: string;
  status: number;
  category: ErrorCategory;
  retryable: boolean;
  details?: Record<string, string>;
}

export function sanitizeErrorMessage(message: string | undefined, statusCode?: number): string {
  if (statusCode === 401) {
    return "Invalid email or password. Please verify your credentials.";
  }
  if (statusCode === 403) {
    return "You do not have permission to perform this action.";
  }
  if (statusCode === 404) {
    return "The requested information or record could not be found.";
  }
  if (statusCode === 409) {
    return "A conflicting record already exists with the provided information.";
  }
  if (statusCode === 502 || statusCode === 503 || statusCode === 504 || statusCode === 521 || statusCode === 524) {
    return "The NSS service is currently starting up or temporarily unreachable. Please try again in a moment.";
  }
  if (statusCode && statusCode >= 500) {
    return "The NSS service encountered a temporary error. Please try again shortly.";
  }

  if (!message || typeof message !== "string") {
    return "An unexpected issue occurred. Please try again.";
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
    "typeerror",
    "referenceerror",
    "json.parse",
    "bad gateway",
    "gateway timeout",
    "internal server error",
    "cannot read properties",
    "is not a function",
    "is not defined",
    "[object object]",
    "at line",
    "at eval",
    "eval at",
    "violates foreign key",
    "duplicate key value",
    "relation \"",
    "column \"",
    "driver",
    "org.postgresql",
  ];

  const hasTechnicalLeak = technicalSignatures.some((sig) => lower.includes(sig));

  if (hasTechnicalLeak) {
    if (statusCode && statusCode >= 500) {
      return "The NSS service is temporarily unavailable. Please try again shortly.";
    }
    return "Unable to complete request. Please verify the entered details or try again later.";
  }

  return message;
}

export function classifyError(status: number, endpoint?: string): { category: ErrorCategory; retryable: boolean } {
  if (status === 0 || (typeof navigator !== "undefined" && !navigator.onLine)) {
    return { category: "NETWORK_OFFLINE", retryable: true };
  }
  if (status === 401) {
    if (endpoint?.includes("/auth/login")) {
      return { category: "AUTH_INVALID", retryable: false };
    }
    return { category: "AUTH_EXPIRED", retryable: false };
  }
  if (status === 403) {
    return { category: "FORBIDDEN", retryable: false };
  }
  if (status === 404) {
    return { category: "NOT_FOUND", retryable: false };
  }
  if (status === 409) {
    return { category: "CONFLICT", retryable: false };
  }
  if (status === 400 || status === 422) {
    return { category: "VALIDATION", retryable: false };
  }
  if (status === 502 || status === 503 || status === 504 || status === 521 || status === 524) {
    return { category: "SERVER_WARMUP", retryable: true };
  }
  if (status >= 500) {
    return { category: "SERVER_ERROR", retryable: true };
  }
  return { category: "UNKNOWN", retryable: false };
}

export interface ApiRequestOptions extends RequestInit {
  retries?: number;
  retryDelayMs?: number;
}

export async function apiRequest<T>(
  endpoint: string,
  options: ApiRequestOptions = {}
): Promise<T> {
  const method = (options.method || "GET").toUpperCase();
  const isIdempotent = method === "GET" || method === "HEAD";
  const defaultRetries = isIdempotent ? 3 : 2;
  const { retries = defaultRetries, retryDelayMs = 2000, ...fetchOptions } = options;
  const token = localStorage.getItem("nss_token");

  const headers: HeadersInit = {
    "Content-Type": "application/json",
    ...(fetchOptions.headers || {}),
  };

  if (token) {
    (headers as Record<string, string>)["Authorization"] = `Bearer ${token}`;
  }

  let attempt = 0;
  while (true) {
    let response: Response | null = null;
    let networkError = false;

    try {
      response = await fetch(`${API_BASE_URL}${endpoint}`, {
        ...fetchOptions,
        headers,
      });
    } catch {
      networkError = true;
    }

    const isServerWarmup = response && (response.status === 502 || response.status === 503 || response.status === 504);
    if ((networkError || isServerWarmup) && attempt < retries) {
      attempt++;
      const waitTime = retryDelayMs * attempt;
      await new Promise((res) => setTimeout(res, waitTime));
      continue;
    }

    if (networkError || !response) {
      const err: ApiError = {
        code: "NETWORK_ERROR",
        message: "Unable to connect to the NSS server. Please check your internet connection.",
        status: 0,
        category: "NETWORK_OFFLINE",
        retryable: true,
      };
      throw err;
    }

    if (response.status === 204) {
      return {} as T;
    }

    const data = await response.json().catch(() => ({}));

    if (!response.ok) {
      const rawError = data?.error;
      const rawMessage = rawError?.message || data?.message || response.statusText || "Request failed";
      const sanitizedMsg = sanitizeErrorMessage(rawMessage, response.status);
      const { category, retryable } = classifyError(response.status, endpoint);

      if (category === "AUTH_EXPIRED" && typeof window !== "undefined") {
        window.dispatchEvent(new CustomEvent("nss:auth:expired"));
      }

      const error: ApiError = {
        code: rawError?.code || (response.status === 401 ? "AUTHENTICATION_REQUIRED" : "HTTP_ERROR"),
        message: sanitizedMsg,
        status: response.status,
        category,
        retryable,
        details: rawError?.details,
      };
      throw error;
    }

    return data as T;
  }
}
