import React from "react";
import { Link, useNavigate, useLocation } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

export const Navbar: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const handleLogout = () => {
    logout();
    navigate("/login");
  };

  const isActive = (path: string) => location.pathname === path;

  return (
    <header className="navbar">
      <div className="navbar-container">
        <Link to="/dashboard" className="brand">
          <span className="brand-badge">NSS</span>
          <span className="brand-title">Raghus NSS Platform</span>
        </Link>

        {user && (
          <nav className="nav-links">
            <Link
              to="/dashboard"
              className={`nav-link ${isActive("/dashboard") ? "active" : ""}`}
            >
              Dashboard
            </Link>
            <Link
              to="/volunteers"
              className={`nav-link ${location.pathname.startsWith("/volunteers") ? "active" : ""}`}
            >
              Volunteers
            </Link>
            <Link
              to="/units"
              className={`nav-link ${location.pathname.startsWith("/units") ? "active" : ""}`}
            >
              NSS Units
            </Link>
            <Link
              to="/events"
              className={`nav-link ${location.pathname.startsWith("/events") ? "active" : ""}`}
            >
              Events
            </Link>
          </nav>
        )}

        <div className="nav-user">
          {user ? (
            <div className="user-profile">
              <div className="user-details">
                <span className="user-name">{user.name}</span>
                <span className="role-badge">
                  {user.roles[0] || "USER"}
                </span>
              </div>
              <button onClick={handleLogout} className="btn-logout">
                Sign Out
              </button>
            </div>
          ) : (
            <Link to="/login" className="btn-primary-sm">
              Sign In
            </Link>
          )}
        </div>
      </div>
    </header>
  );
};
