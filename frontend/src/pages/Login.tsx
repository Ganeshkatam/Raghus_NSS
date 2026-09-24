import React, { useState, useRef, useEffect, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { ApiError } from "../api/client";

export const Login: React.FC = () => {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [errorInfo, setErrorInfo] = useState<ApiError | null>(null);
  const [loading, setLoading] = useState(false);
  const [retryCountdown, setRetryCountdown] = useState<number | null>(null);

  const passwordInputRef = useRef<HTMLInputElement>(null);
  const countdownTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const { login } = useAuth();
  const navigate = useNavigate();

  const clearCountdown = () => {
    if (countdownTimerRef.current) {
      clearInterval(countdownTimerRef.current);
      countdownTimerRef.current = null;
    }
    setRetryCountdown(null);
  };

  const performLogin = useCallback(
    async (emailToUse: string, passwordToUse: string) => {
      clearCountdown();
      setErrorInfo(null);
      setLoading(true);

      try {
        await login(emailToUse, passwordToUse);
        navigate("/dashboard");
      } catch (err: unknown) {
        const apiErr = err as ApiError;
        setErrorInfo(apiErr);

        // Act based on the specific error type
        if (apiErr.category === "AUTH_INVALID") {
          // Action: Clear password and focus password field for quick re-entry
          setPassword("");
          setTimeout(() => {
            passwordInputRef.current?.focus();
          }, 50);
        } else if (apiErr.category === "SERVER_WARMUP") {
          // Action: Start automatic countdown for free tier spin-up
          setRetryCountdown(6);
        }
      } finally {
        setLoading(false);
      }
    },
    [login, navigate]
  );

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    performLogin(email, password);
  };

  // Automatic countdown runner for server warmup
  useEffect(() => {
    if (retryCountdown === null) return;

    if (retryCountdown <= 0) {
      clearCountdown();
      if (email && password) {
        performLogin(email, password);
      }
      return;
    }

    countdownTimerRef.current = setTimeout(() => {
      setRetryCountdown((prev) => (prev !== null ? prev - 1 : null));
    }, 1000);

    return () => {
      if (countdownTimerRef.current) clearTimeout(countdownTimerRef.current);
    };
  }, [retryCountdown, email, password, performLogin]);

  // Network recovery handler: auto-retry when connection comes back online
  useEffect(() => {
    const handleOnline = () => {
      if (errorInfo?.category === "NETWORK_OFFLINE" && email && password) {
        performLogin(email, password);
      }
    };

    window.addEventListener("online", handleOnline);
    return () => window.removeEventListener("online", handleOnline);
  }, [errorInfo, email, password, performLogin]);

  return (
    <div className="login-wrapper">
      <div className="login-card">
        <div className="login-header">
          <div className="logo-badge">NSS</div>
          <div className="college-name">Raghu Engineering College</div>
          <h2>National Service Scheme</h2>
          <p className="subtitle">Sign in to your NSS institutional account</p>
          <div className="motto-pill">Not Me But You</div>
        </div>

        {errorInfo && (
          <div
            className={`alert ${
              errorInfo.category === "SERVER_WARMUP"
                ? "alert-warning"
                : errorInfo.category === "NETWORK_OFFLINE"
                ? "alert-warning"
                : "alert-error"
            }`}
            style={{
              padding: "0.85rem 1rem",
              borderRadius: "8px",
              marginBottom: "1.25rem",
              fontSize: "0.88rem",
              lineHeight: 1.4,
            }}
          >
            <div style={{ fontWeight: 600, marginBottom: "0.25rem" }}>
              {errorInfo.category === "SERVER_WARMUP"
                ? "Backend Starting Up"
                : errorInfo.category === "NETWORK_OFFLINE"
                ? "Connection Issue"
                : errorInfo.category === "AUTH_INVALID"
                ? "Authentication Failed"
                : "Unable to Sign In"}
            </div>
            <div>{errorInfo.message}</div>

            {errorInfo.category === "SERVER_WARMUP" && (
              <div style={{ marginTop: "0.6rem", display: "flex", alignItems: "center", gap: "0.5rem" }}>
                <span style={{ fontSize: "0.8rem", color: "var(--text-secondary, #64748b)" }}>
                  {retryCountdown !== null && retryCountdown > 0
                    ? `Auto-retrying in ${retryCountdown}s...`
                    : "Retrying now..."}
                </span>
                <button
                  type="button"
                  onClick={() => performLogin(email, password)}
                  disabled={loading}
                  style={{
                    marginLeft: "auto",
                    padding: "0.25rem 0.65rem",
                    fontSize: "0.78rem",
                    borderRadius: "4px",
                    border: "1px solid currentColor",
                    background: "transparent",
                    cursor: "pointer",
                    fontWeight: 600,
                  }}
                >
                  Retry Now
                </button>
              </div>
            )}

            {errorInfo.category === "NETWORK_OFFLINE" && (
              <div style={{ marginTop: "0.5rem" }}>
                <button
                  type="button"
                  onClick={() => performLogin(email, password)}
                  disabled={loading}
                  style={{
                    padding: "0.25rem 0.65rem",
                    fontSize: "0.78rem",
                    borderRadius: "4px",
                    border: "1px solid currentColor",
                    background: "transparent",
                    cursor: "pointer",
                    fontWeight: 600,
                  }}
                >
                  Check Connection & Retry
                </button>
              </div>
            )}
          </div>
        )}

        <form onSubmit={handleSubmit} className="form-stack">
          <div className="form-group">
            <label htmlFor="email">Email Address</label>
            <input
              id="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="e.g. admin@raghunss.edu"
              required
              disabled={loading}
            />
          </div>

          <div className="form-group">
            <label htmlFor="password">Password</label>
            <input
              ref={passwordInputRef}
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••••••"
              required
              disabled={loading}
            />
          </div>

          <button type="submit" className="btn-primary" disabled={loading}>
            {loading ? "Authenticating..." : "Sign In"}
          </button>
        </form>
      </div>
    </div>
  );
};
