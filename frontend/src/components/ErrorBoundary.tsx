import React, { Component, ErrorInfo, ReactNode } from "react";

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  errorMessage: string;
}

export class ErrorBoundary extends Component<Props, State> {
  public state: State = {
    hasError: false,
    errorMessage: "",
  };

  public static getDerivedStateFromError(error: Error): State {
    return {
      hasError: true,
      errorMessage: error.message || "An unexpected rendering error occurred.",
    };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    // In production, log error metrics without alerting end-users with raw traces
    console.error("UI ErrorBoundary intercepted error:", error, errorInfo);
  }

  private handleReload = () => {
    window.location.reload();
  };

  private handleGoHome = () => {
    this.setState({ hasError: false, errorMessage: "" });
    window.location.href = "/dashboard";
  };

  public render() {
    if (this.state.hasError) {
      return (
        <div
          style={{
            minHeight: "100vh",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            backgroundColor: "var(--bg-app, #0f172a)",
            padding: "1.5rem",
          }}
        >
          <div
            style={{
              maxWidth: "480px",
              width: "100%",
              backgroundColor: "var(--bg-surface, #1e293b)",
              borderRadius: "12px",
              padding: "2rem",
              border: "1px solid var(--border-subtle, #334155)",
              boxShadow: "0 10px 25px -5px rgba(0, 0, 0, 0.3)",
              textAlign: "center",
            }}
          >
            <div
              style={{
                display: "inline-block",
                padding: "0.5rem 1rem",
                borderRadius: "20px",
                backgroundColor: "rgba(239, 68, 68, 0.15)",
                color: "#f87171",
                fontSize: "0.85rem",
                fontWeight: 600,
                marginBottom: "1rem",
              }}
            >
              Application Notice
            </div>

            <h2
              style={{
                color: "var(--text-primary, #f8fafc)",
                fontSize: "1.35rem",
                marginBottom: "0.75rem",
              }}
            >
              Temporary Display Issue
            </h2>

            <p
              style={{
                color: "var(--text-secondary, #94a3b8)",
                fontSize: "0.9rem",
                lineHeight: 1.5,
                marginBottom: "1.5rem",
              }}
            >
              A technical display error was prevented from affecting your account. You can reload the page or return to the main dashboard.
            </p>

            <div
              style={{
                display: "flex",
                gap: "0.75rem",
                justifyContent: "center",
              }}
            >
              <button
                onClick={this.handleReload}
                style={{
                  padding: "0.625rem 1.25rem",
                  borderRadius: "6px",
                  border: "none",
                  backgroundColor: "var(--primary, #2563eb)",
                  color: "#ffffff",
                  fontWeight: 500,
                  cursor: "pointer",
                }}
              >
                Reload Page
              </button>
              <button
                onClick={this.handleGoHome}
                style={{
                  padding: "0.625rem 1.25rem",
                  borderRadius: "6px",
                  border: "1px solid var(--border-subtle, #334155)",
                  backgroundColor: "transparent",
                  color: "var(--text-primary, #f8fafc)",
                  fontWeight: 500,
                  cursor: "pointer",
                }}
              >
                Go to Dashboard
              </button>
            </div>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
