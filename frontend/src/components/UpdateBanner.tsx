import React, { useState, useEffect, useRef } from "react";

export const UpdateBanner: React.FC = () => {
  const [updateAvailable, setUpdateAvailable] = useState(false);
  const currentScriptRef = useRef<string | null>(null);

  useEffect(() => {
    // Record current bundle script source
    const scripts = Array.from(document.querySelectorAll("script[src]"));
    const mainScript = scripts.find((s) => (s as HTMLScriptElement).src.includes("/assets/index-"));
    if (mainScript) {
      currentScriptRef.current = (mainScript as HTMLScriptElement).src;
    }

    const checkForUpdate = async () => {
      if (!currentScriptRef.current) {
        const currentScripts = Array.from(document.querySelectorAll("script[src]"));
        const scriptFound = currentScripts.find((s) => (s as HTMLScriptElement).src.includes("/assets/index-"));
        if (scriptFound) {
          currentScriptRef.current = (scriptFound as HTMLScriptElement).src;
        } else {
          return;
        }
      }

      try {
        const response = await fetch(`/?_updateCheck=${Date.now()}`, {
          cache: "no-store",
          headers: { "Cache-Control": "no-cache" },
        });
        if (!response.ok) return;
        const html = await response.text();
        const parser = new DOMParser();
        const doc = parser.parseFromString(html, "text/html");
        const newScripts = Array.from(doc.querySelectorAll("script[src]"));
        const newMainScript = newScripts.find((s) => (s as HTMLScriptElement).src.includes("/assets/index-"));
        if (newMainScript) {
          const newSrc = (newMainScript as HTMLScriptElement).src;
          if (newSrc && newSrc !== currentScriptRef.current) {
            setUpdateAvailable(true);
          }
        }
      } catch {
        // Silently ignore network failures
      }
    };

    // Check periodically every 45s and whenever the user returns to the tab
    const interval = setInterval(checkForUpdate, 45000);
    const handleVisibility = () => {
      if (document.visibilityState === "visible") {
        checkForUpdate();
      }
    };
    document.addEventListener("visibilitychange", handleVisibility);

    return () => {
      clearInterval(interval);
      document.removeEventListener("visibilitychange", handleVisibility);
    };
  }, []);

  if (!updateAvailable) return null;

  return (
    <div className="update-available-toast" role="alert">
      <div className="update-toast-content">
        <span className="update-badge-dot" />
        <span>A new version of the NSS application is available.</span>
      </div>
      <button
        type="button"
        className="btn-update-reload"
        onClick={() => window.location.reload()}
      >
        Refresh Application
      </button>
    </div>
  );
};
