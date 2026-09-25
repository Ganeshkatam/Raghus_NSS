import React, { useState, useRef, useEffect } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { apiRequest } from "../api/client";

interface TopbarProps {
  onToggleMobileMenu: () => void;
  isMobileMenuOpen: boolean;
}

export const Topbar: React.FC<TopbarProps> = ({ onToggleMobileMenu, isMobileMenuOpen }) => {
  const { user, token, isAuthenticated, roleDisplayName, logout, isOfficer, isVolunteer, primaryRole, workspaceConfig } = useAuth();
  const [profileOpen, setProfileOpen] = useState(false);
  const [unreadCount, setUnreadCount] = useState<number>(0);
  const [assignedUnitText, setAssignedUnitText] = useState<string | null>(null);

  // Change Password Modal State
  const [showPasswordModal, setShowPasswordModal] = useState(false);
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [passwordSuccess, setPasswordSuccess] = useState<string | null>(null);
  const [changingPassword, setChangingPassword] = useState(false);
  const [showCurrentPassword, setShowCurrentPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [mobileSearchOpen, setMobileSearchOpen] = useState(false);
  const [globalSearch, setGlobalSearch] = useState("");

  const popoverRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();

  const handleGlobalSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (globalSearch.trim()) {
      const searchTarget = primaryRole === "VOLUNTEER" ? "/events" : "/volunteers";
      navigate(`${searchTarget}?search=${encodeURIComponent(globalSearch.trim())}`);
      setMobileSearchOpen(false);
    }
  };

  useEffect(() => {
    if (!isAuthenticated || !token || !user) return;
    let isSubscribed = true;

    const fetchUnread = () => {
      if (typeof document !== "undefined" && document.visibilityState !== "visible") {
        return;
      }
      apiRequest<{ unreadCount: number }>("/notifications/unread-count")
        .then((res) => {
          if (isSubscribed && res && typeof res.unreadCount === "number") {
            setUnreadCount(res.unreadCount);
          }
        })
        .catch(() => {});
    };

    fetchUnread();
    const timer = setInterval(fetchUnread, 60000);

    const handleVisibilityChange = () => {
      if (document.visibilityState === "visible") {
        fetchUnread();
      }
    };
    document.addEventListener("visibilitychange", handleVisibilityChange);

    return () => {
      isSubscribed = false;
      clearInterval(timer);
      document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
  }, [isAuthenticated, token, user]);

  useEffect(() => {
    if (!isAuthenticated || !token || !user) return;
    let isSubscribed = true;
    if (isVolunteer) {
      apiRequest<{ activeUnitName: string | null; activeUnitNumber: string | null }>("/volunteers/me")
        .then((vol) => {
          if (isSubscribed && vol && (vol.activeUnitNumber || vol.activeUnitName)) {
            setAssignedUnitText(vol.activeUnitNumber ? `Unit ${vol.activeUnitNumber}` : vol.activeUnitName);
          }
        })
        .catch(() => {});
    } else if (isOfficer) {
      apiRequest<any[]>("/units")
        .then((units) => {
          if (isSubscribed && Array.isArray(units) && units.length > 0) {
            const myUnit = units.find((u) => u.officerEmail === user.email || u.officerId === user.userId);
            if (myUnit) {
              setAssignedUnitText(`Unit ${myUnit.unitNumber}`);
            }
          }
        })
        .catch(() => {});
    }
    return () => {
      isSubscribed = false;
    };
  }, [isAuthenticated, token, user, isVolunteer, isOfficer]);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (popoverRef.current && !popoverRef.current.contains(event.target as Node)) {
        setProfileOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const handleLogout = () => {
    logout();
    navigate("/login");
  };

  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setPasswordError(null);
    setPasswordSuccess(null);

    const passwordComplexityRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&#^()_+\-=[\]{};':"\\|,.<>/]).{8,}$/;
    if (!passwordComplexityRegex.test(newPassword)) {
      setPasswordError("Password must be at least 8 characters long and contain at least one uppercase letter, one lowercase letter, one number, and one special character.");
      return;
    }
    if (newPassword !== confirmPassword) {
      setPasswordError("New password and confirmation do not match.");
      return;
    }

    setChangingPassword(true);
    try {
      await apiRequest("/auth/change-password", {
        method: "POST",
        body: JSON.stringify({
          currentPassword,
          newPassword,
        }),
      });
      setPasswordSuccess("Password updated successfully.");
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
      setTimeout(() => {
        setShowPasswordModal(false);
        setPasswordSuccess(null);
        setShowCurrentPassword(false);
        setShowNewPassword(false);
        setShowConfirmPassword(false);
      }, 1500);
    } catch (err: any) {
      setPasswordError(err.message || "Failed to update password.");
    } finally {
      setChangingPassword(false);
    }
  };

  return (
    <header className="topbar">
      <div className="topbar-main">
        <div className="topbar-left">
          <button
            type="button"
            className="btn-icon mobile-menu-toggle"
            aria-label={isMobileMenuOpen ? "Close menu" : "Open navigation menu"}
            onClick={onToggleMobileMenu}
          >
            <span className="hamburger-icon">
              <span className="bar"></span>
              <span className="bar"></span>
              <span className="bar"></span>
            </span>
          </button>

          <Link to="/dashboard" className="topbar-brand">
            <div className="brand-crest">NSS</div>
            <div className="brand-text">
              <span className="brand-college brand-college-full">Raghu Engineering College</span>
              <span className="brand-college brand-college-short">REC NSS</span>
              <span className="brand-sub">National Service Scheme</span>
            </div>
          </Link>
        </div>

        <div className="topbar-right">
          <form onSubmit={handleGlobalSearch} className="topbar-search" role="search">
            <input
              type="search"
              placeholder={workspaceConfig.searchScopePlaceholder}
              className="search-input"
              aria-label="Search NSS"
              value={globalSearch}
              onChange={(e) => setGlobalSearch(e.target.value)}
            />
          </form>

          <button
            type="button"
            className="topbar-action-btn topbar-mobile-search-toggle"
            title="Search"
            aria-label="Toggle search"
            aria-expanded={mobileSearchOpen}
            onClick={() => setMobileSearchOpen(!mobileSearchOpen)}
          >
            <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="11" cy="11" r="8" />
              <line x1="21" y1="21" x2="16.65" y2="16.65" />
            </svg>
          </button>

          {assignedUnitText && (
            <span
              className="topbar-unit-badge badge badge-primary"
              style={{
                padding: "0.25rem 0.6rem",
                fontSize: "0.75rem",
                fontWeight: 700,
                letterSpacing: "0.02em",
                alignItems: "center",
              }}
              title="Assigned Unit Scope"
            >
              {assignedUnitText}
            </span>
          )}

        <Link to="/announcements" className="topbar-action-btn" title="Announcements & Notifications" aria-label="Notifications" style={{ position: "relative" }}>
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path>
            <path d="M13.73 21a2 2 0 0 1-3.46 0"></path>
          </svg>
          {unreadCount > 0 ? (
            <span style={{
              position: "absolute",
              top: "-4px",
              right: "-4px",
              backgroundColor: "#ef4444",
              color: "#ffffff",
              borderRadius: "9999px",
              padding: "0.1rem 0.35rem",
              fontSize: "0.65rem",
              fontWeight: 800,
              minWidth: "16px",
              textAlign: "center",
              lineHeight: "1"
            }}>
              {unreadCount > 99 ? "99+" : unreadCount}
            </span>
          ) : (
            <span className="notification-dot" aria-hidden="true"></span>
          )}
        </Link>

        {user && (
          <div className="profile-popover-container" ref={popoverRef}>
            <button
              type="button"
              className="user-pill-btn"
              onClick={() => setProfileOpen(!profileOpen)}
              aria-expanded={profileOpen}
              aria-haspopup="true"
              aria-label={`User menu for ${user.name}`}
            >
              <span className="user-avatar" aria-hidden="true">
                {user.name.charAt(0).toUpperCase()}
              </span>
              <span className="user-pill-name">{user.name}</span>
              <svg className="user-pill-arrow" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <polyline points="6 9 12 15 18 9"></polyline>
              </svg>
            </button>

            {profileOpen && (
              <div className="profile-popover" role="menu">
                <div className="profile-popover-header">
                  <div className="popover-name">{user.name}</div>
                  <div className="popover-email">{user.email}</div>
                  <div className="popover-role-badge">
                    {roleDisplayName} {assignedUnitText ? `(${assignedUnitText})` : ""}
                  </div>
                </div>
                <div className="profile-popover-actions">
                  <Link
                    to="/dashboard"
                    className="popover-item"
                    role="menuitem"
                    onClick={() => setProfileOpen(false)}
                  >
                    {primaryRole === "VOLUNTEER" || primaryRole === "STUDENT_LEADER"
                      ? "My Dashboard"
                      : "Management Dashboard"}
                  </Link>
                  {primaryRole === "VOLUNTEER" && (
                    <Link
                      to="/service-hours"
                      className="popover-item"
                      role="menuitem"
                      onClick={() => setProfileOpen(false)}
                    >
                      My Service Hours
                    </Link>
                  )}
                  {primaryRole === "STUDENT_LEADER" && (
                    <Link
                      to="/service-hours"
                      className="popover-item"
                      role="menuitem"
                      onClick={() => setProfileOpen(false)}
                    >
                      My Activity
                    </Link>
                  )}
                  <button
                    type="button"
                    className="popover-item"
                    role="menuitem"
                    onClick={() => {
                      setProfileOpen(false);
                      setPasswordError(null);
                      setPasswordSuccess(null);
                      setShowCurrentPassword(false);
                      setShowNewPassword(false);
                      setShowConfirmPassword(false);
                      setShowPasswordModal(true);
                    }}
                  >
                    Change Password
                  </button>
                  <button
                    type="button"
                    className="popover-item popover-logout"
                    role="menuitem"
                    onClick={handleLogout}
                  >
                    Sign Out
                  </button>
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>

    {/* Collapsible Mobile Search Row */}
    {mobileSearchOpen && (
      <form onSubmit={handleGlobalSearch} className="topbar-mobile-search-row" role="search">
        <svg className="topbar-mobile-search-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
          <circle cx="11" cy="11" r="8" />
          <line x1="21" y1="21" x2="16.65" y2="16.65" />
        </svg>
        <input
          type="search"
          placeholder={workspaceConfig.searchScopePlaceholder}
          className="topbar-mobile-search-input"
          aria-label="Search NSS"
          value={globalSearch}
          onChange={(e) => setGlobalSearch(e.target.value)}
          autoFocus
        />
        <button
          type="button"
          className="topbar-mobile-search-close"
          onClick={() => setMobileSearchOpen(false)}
          aria-label="Close search"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
        </button>
      </form>
    )}

      {showPasswordModal && (
        <div className="modal-backdrop">
          <div className="modal-card">
            <div className="modal-header">
              <h2>Change Account Password</h2>
              <button
                type="button"
                className="btn-close"
                onClick={() => {
                  setShowPasswordModal(false);
                  setShowCurrentPassword(false);
                  setShowNewPassword(false);
                  setShowConfirmPassword(false);
                }}
              >
                &times;
              </button>
            </div>
            <div className="modal-body">
              {passwordError && (
                <div className="alert alert-error" style={{ marginBottom: "1rem" }}>
                  <strong>Error:</strong> {passwordError}
                </div>
              )}
              {passwordSuccess && (
                <div className="alert alert-success" style={{ marginBottom: "1rem" }}>
                  <strong>Success:</strong> {passwordSuccess}
                </div>
              )}
              <form onSubmit={handleChangePassword} className="form-stack" style={{ padding: 0 }}>
                <div className="form-group">
                  <label htmlFor="currentPasswordInput">Current Password *</label>
                  <div className="password-input-wrapper">
                    <input
                      id="currentPasswordInput"
                      type={showCurrentPassword ? "text" : "password"}
                      required
                      value={currentPassword}
                      onChange={(e) => setCurrentPassword(e.target.value)}
                      placeholder="Enter current password"
                    />
                    <button
                      type="button"
                      className="password-toggle-btn"
                      onClick={() => setShowCurrentPassword((prev) => !prev)}
                      aria-label={showCurrentPassword ? "Hide current password" : "Show current password"}
                      title={showCurrentPassword ? "Hide current password" : "Show current password"}
                      tabIndex={-1}
                    >
                      {showCurrentPassword ? (
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                          <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24" />
                          <line x1="1" y1="1" x2="23" y2="23" />
                        </svg>
                      ) : (
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                          <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                          <circle cx="12" cy="12" r="3" />
                        </svg>
                      )}
                    </button>
                  </div>
                </div>
                <div className="form-group">
                  <label htmlFor="newPasswordInput">New Password *</label>
                  <div className="password-input-wrapper">
                    <input
                      id="newPasswordInput"
                      type={showNewPassword ? "text" : "password"}
                      required
                      minLength={6}
                      value={newPassword}
                      onChange={(e) => setNewPassword(e.target.value)}
                      placeholder="At least 6 characters"
                    />
                    <button
                      type="button"
                      className="password-toggle-btn"
                      onClick={() => setShowNewPassword((prev) => !prev)}
                      aria-label={showNewPassword ? "Hide new password" : "Show new password"}
                      title={showNewPassword ? "Hide new password" : "Show new password"}
                      tabIndex={-1}
                    >
                      {showNewPassword ? (
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                          <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24" />
                          <line x1="1" y1="1" x2="23" y2="23" />
                        </svg>
                      ) : (
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                          <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                          <circle cx="12" cy="12" r="3" />
                        </svg>
                      )}
                    </button>
                  </div>
                </div>
                <div className="form-group">
                  <label htmlFor="confirmPasswordInput">Confirm New Password *</label>
                  <div className="password-input-wrapper">
                    <input
                      id="confirmPasswordInput"
                      type={showConfirmPassword ? "text" : "password"}
                      required
                      minLength={6}
                      value={confirmPassword}
                      onChange={(e) => setConfirmPassword(e.target.value)}
                      placeholder="Re-enter new password"
                    />
                    <button
                      type="button"
                      className="password-toggle-btn"
                      onClick={() => setShowConfirmPassword((prev) => !prev)}
                      aria-label={showConfirmPassword ? "Hide confirmation password" : "Show confirmation password"}
                      title={showConfirmPassword ? "Hide confirmation password" : "Show confirmation password"}
                      tabIndex={-1}
                    >
                      {showConfirmPassword ? (
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                          <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24" />
                          <line x1="1" y1="1" x2="23" y2="23" />
                        </svg>
                      ) : (
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                          <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                          <circle cx="12" cy="12" r="3" />
                        </svg>
                      )}
                    </button>
                  </div>
                </div>
                <div className="modal-actions">
                  <button
                    type="button"
                    onClick={() => {
                      setShowPasswordModal(false);
                      setShowCurrentPassword(false);
                      setShowNewPassword(false);
                      setShowConfirmPassword(false);
                    }}
                    className="btn-secondary"
                    disabled={changingPassword}
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="btn-primary"
                    disabled={changingPassword || !currentPassword || !newPassword || !confirmPassword}
                  >
                    {changingPassword ? "Updating..." : "Update Password"}
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      )}
    </header>
  );
};

