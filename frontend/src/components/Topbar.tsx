import React, { useState, useRef, useEffect } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { apiRequest } from "../api/client";

interface TopbarProps {
  onToggleMobileMenu: () => void;
  isMobileMenuOpen: boolean;
}

export const Topbar: React.FC<TopbarProps> = ({ onToggleMobileMenu, isMobileMenuOpen }) => {
  const { user, roleDisplayName, logout, isOfficer, isVolunteer } = useAuth();
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

  const popoverRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();

  useEffect(() => {
    if (!user) return;
    const fetchUnread = () => {
      apiRequest<{ unreadCount: number }>("/notifications/unread-count")
        .then((res) => {
          if (res && typeof res.unreadCount === "number") {
            setUnreadCount(res.unreadCount);
          }
        })
        .catch(() => {});
    };
    fetchUnread();
    const timer = setInterval(fetchUnread, 30000);
    return () => clearInterval(timer);
  }, [user]);

  useEffect(() => {
    if (!user) return;
    if (isVolunteer) {
      apiRequest<{ activeUnitName: string | null; activeUnitNumber: string | null }>("/volunteers/me")
        .then((vol) => {
          if (vol && (vol.activeUnitNumber || vol.activeUnitName)) {
            setAssignedUnitText(vol.activeUnitNumber ? `Unit ${vol.activeUnitNumber}` : vol.activeUnitName);
          }
        })
        .catch(() => {});
    } else if (isOfficer) {
      apiRequest<any[]>("/units")
        .then((units) => {
          if (Array.isArray(units) && units.length > 0) {
            const myUnit = units.find((u) => u.officerEmail === user.email || u.officerId === user.userId);
            if (myUnit) {
              setAssignedUnitText(`Unit ${myUnit.unitNumber}`);
            }
          }
        })
        .catch(() => {});
    }
  }, [user, isVolunteer, isOfficer]);

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

    if (newPassword.length < 6) {
      setPasswordError("New password must be at least 6 characters.");
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
      }, 1500);
    } catch (err: any) {
      setPasswordError(err.message || "Failed to update password.");
    } finally {
      setChangingPassword(false);
    }
  };

  return (
    <header className="topbar">
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
            <span className="brand-college">Raghu Engineering College</span>
            <span className="brand-sub">National Service Scheme</span>
          </div>
        </Link>
      </div>

      <div className="topbar-right">
        <div className="topbar-search">
          <input
            type="search"
            placeholder="Search NSS (volunteers, events, units)..."
            className="search-input"
            aria-label="Search NSS"
          />
        </div>

        {assignedUnitText && (
          <span
            className="badge badge-primary"
            style={{
              padding: "0.25rem 0.6rem",
              fontSize: "0.75rem",
              fontWeight: 700,
              letterSpacing: "0.02em",
              display: "inline-flex",
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
            >
              <span className="user-avatar" aria-hidden="true">
                {user.name.charAt(0).toUpperCase()}
              </span>
              <span className="user-pill-name">{user.name}</span>
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
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
                    My Dashboard
                  </Link>
                  <Link
                    to="/service-hours"
                    className="popover-item"
                    role="menuitem"
                    onClick={() => setProfileOpen(false)}
                  >
                    Service Hours
                  </Link>
                  <button
                    type="button"
                    className="popover-item"
                    role="menuitem"
                    onClick={() => {
                      setProfileOpen(false);
                      setPasswordError(null);
                      setPasswordSuccess(null);
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

      {showPasswordModal && (
        <div className="modal-backdrop">
          <div className="modal-card">
            <div className="modal-header">
              <h2>Change Account Password</h2>
              <button
                type="button"
                className="btn-close"
                onClick={() => setShowPasswordModal(false)}
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
                  <input
                    id="currentPasswordInput"
                    type="password"
                    required
                    value={currentPassword}
                    onChange={(e) => setCurrentPassword(e.target.value)}
                    placeholder="Enter current password"
                  />
                </div>
                <div className="form-group">
                  <label htmlFor="newPasswordInput">New Password *</label>
                  <input
                    id="newPasswordInput"
                    type="password"
                    required
                    minLength={6}
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    placeholder="At least 6 characters"
                  />
                </div>
                <div className="form-group">
                  <label htmlFor="confirmPasswordInput">Confirm New Password *</label>
                  <input
                    id="confirmPasswordInput"
                    type="password"
                    required
                    minLength={6}
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    placeholder="Re-enter new password"
                  />
                </div>
                <div className="modal-actions">
                  <button
                    type="button"
                    onClick={() => setShowPasswordModal(false)}
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

