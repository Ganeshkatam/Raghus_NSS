import React, { useState } from "react";
import { NavLink } from "react-router-dom";
import { Topbar } from "./Topbar";
import { Sidebar } from "./Sidebar";
import { useAuth } from "../context/AuthContext";
import { getMobileNavigationForRole, NavIconKey } from "../config/navigation";

const renderBottomIcon = (iconKey: NavIconKey) => {
  switch (iconKey) {
    case "overview":
      return (
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"></path>
        </svg>
      );
    case "events":
      return (
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
          <line x1="16" y1="2" x2="16" y2="6"></line>
          <line x1="8" y1="2" x2="8" y2="6"></line>
          <line x1="3" y1="10" x2="21" y2="10"></line>
        </svg>
      );
    case "attendance":
      return (
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M9 11l3 3L22 4"></path>
          <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"></path>
        </svg>
      );
    case "hours":
      return (
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <circle cx="12" cy="12" r="10"></circle>
          <polyline points="12 6 12 12 16 14"></polyline>
        </svg>
      );
    case "people":
      return (
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
          <circle cx="9" cy="7" r="4"></circle>
          <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
          <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
        </svg>
      );
    case "units":
      return (
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"></path>
          <polyline points="9 22 9 12 15 12 15 22"></polyline>
        </svg>
      );
    case "reports":
      return (
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <line x1="18" y1="20" x2="18" y2="10"></line>
          <line x1="12" y1="20" x2="12" y2="4"></line>
          <line x1="6" y1="20" x2="6" y2="14"></line>
        </svg>
      );
    case "admin":
      return (
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
          <circle cx="12" cy="7" r="4"></circle>
        </svg>
      );
    default:
      return (
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <circle cx="12" cy="12" r="10"></circle>
        </svg>
      );
  }
};

export const AppShell: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const { primaryRole } = useAuth();
  const mobileNavItems = getMobileNavigationForRole(primaryRole);

  return (
    <div className="app-shell">
      <Topbar
        onToggleMobileMenu={() => setMobileMenuOpen(!mobileMenuOpen)}
        isMobileMenuOpen={mobileMenuOpen}
      />

      <div className="app-shell-body">
        <Sidebar
          isOpen={mobileMenuOpen}
          onClose={() => setMobileMenuOpen(false)}
        />

        {mobileMenuOpen && (
          <div
            className="mobile-backdrop"
            onClick={() => setMobileMenuOpen(false)}
            aria-hidden="true"
          />
        )}

        <main className="app-content" id="main-content">
          {children}
        </main>
      </div>

      {/* Mobile Bottom Navigation for high-frequency role-tailored actions */}
      <nav className="mobile-bottom-nav" aria-label="Mobile Navigation">
        {mobileNavItems.map((item) => (
          <NavLink
            key={item.id}
            to={item.path}
            className={({ isActive }) => `bottom-nav-item ${isActive ? "active" : ""}`}
          >
            {renderBottomIcon(item.iconKey)}
            <span>{item.label}</span>
          </NavLink>
        ))}

        <button
          type="button"
          className="bottom-nav-item bottom-nav-menu"
          onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
          aria-label="Toggle full menu"
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <line x1="3" y1="12" x2="21" y2="12"></line>
            <line x1="3" y1="6" x2="21" y2="6"></line>
            <line x1="3" y1="18" x2="21" y2="18"></line>
          </svg>
          <span>Menu</span>
        </button>
      </nav>
    </div>
  );
};
