import React from "react";
import { NavLink } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

interface SidebarProps {
  isOpen: boolean;
  onClose: () => void;
}

export const Sidebar: React.FC<SidebarProps> = ({ isOpen, onClose }) => {
  const { isAdmin, isCoordinator, isOfficer, isCoordinatorOrOfficer, isStudentLeader, isVolunteer } = useAuth();

  const handleLinkClick = () => {
    if (window.innerWidth <= 1024) {
      onClose();
    }
  };

  // Determine role-aware labels
  const isPlainVolunteer = isVolunteer && !isCoordinatorOrOfficer && !isStudentLeader;
  const overviewTitle = isPlainVolunteer
    ? "My NSS"
    : isStudentLeader
    ? "Unit Overview"
    : isOfficer
    ? "Programme Overview"
    : isCoordinator
    ? "College NSS Overview"
    : "Overview";

  return (
    <aside className={`app-sidebar ${isOpen ? "open" : ""}`} aria-label="Main Navigation">
      <div className="sidebar-scroll">
        <nav className="sidebar-nav">
          {/* Section: Overview */}
          <div className="nav-group">
            <span className="nav-group-title">Overview</span>
            <NavLink
              to="/dashboard"
              className={({ isActive }) => `sidebar-link ${isActive ? "active" : ""}`}
              onClick={handleLinkClick}
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <rect x="3" y="3" width="7" height="7"></rect>
                <rect x="14" y="3" width="7" height="7"></rect>
                <rect x="14" y="14" width="7" height="7"></rect>
                <rect x="3" y="14" width="7" height="7"></rect>
              </svg>
              <span>{overviewTitle}</span>
            </NavLink>
          </div>

          {/* Section: People (Only Officers, Coordinators, Admins, and Student Leaders) */}
          {(isCoordinatorOrOfficer || isStudentLeader) && (
            <div className="nav-group">
              <span className="nav-group-title">People</span>
              <NavLink
                to="/volunteers"
                className={({ isActive }) => `sidebar-link ${isActive ? "active" : ""}`}
                onClick={handleLinkClick}
              >
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                  <circle cx="9" cy="7" r="4"></circle>
                  <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
                  <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
                </svg>
                <span>{isStudentLeader ? "Unit Volunteers" : "Volunteers"}</span>
              </NavLink>
            </div>
          )}

          {/* Section: Organisation (Only Officers, Coordinators, and Admins) */}
          {isCoordinatorOrOfficer && (
            <div className="nav-group">
              <span className="nav-group-title">Organisation</span>
              <NavLink
                to="/units"
                className={({ isActive }) => `sidebar-link ${isActive ? "active" : ""}`}
                onClick={handleLinkClick}
              >
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"></path>
                  <polyline points="9 22 9 12 15 12 15 22"></polyline>
                </svg>
                <span>{isOfficer && !isAdmin ? "Assigned Unit" : "NSS Units"}</span>
              </NavLink>
            </div>
          )}

          {/* Section: Programmes (Available to all) */}
          <div className="nav-group">
            <span className="nav-group-title">Programmes</span>
            <NavLink
              to="/events"
              className={({ isActive }) => `sidebar-link ${isActive ? "active" : ""}`}
              onClick={handleLinkClick}
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
                <line x1="16" y1="2" x2="16" y2="6"></line>
                <line x1="8" y1="2" x2="8" y2="6"></line>
                <line x1="3" y1="10" x2="21" y2="10"></line>
              </svg>
              <span>Events &amp; Activities</span>
            </NavLink>
            <NavLink
              to="/camps"
              className={({ isActive }) => `sidebar-link ${isActive ? "active" : ""}`}
              onClick={handleLinkClick}
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M12 2L2 22h20L12 2z"></path>
              </svg>
              <span>Special Camps</span>
              <span className="sidebar-badge-future">Future</span>
            </NavLink>
          </div>

          {/* Section: Participation (Role-aware items) */}
          <div className="nav-group">
            <span className="nav-group-title">Participation</span>
            <NavLink
              to="/attendance"
              className={({ isActive }) => `sidebar-link ${isActive ? "active" : ""}`}
              onClick={handleLinkClick}
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M9 11l3 3L22 4"></path>
                <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"></path>
              </svg>
              <span>
                {isPlainVolunteer
                  ? "Attendance Check-In"
                  : isStudentLeader
                  ? "Attendance Assistant"
                  : "Attendance Operations"}
              </span>
            </NavLink>
            <NavLink
              to="/service-hours"
              className={({ isActive }) => `sidebar-link ${isActive ? "active" : ""}`}
              onClick={handleLinkClick}
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="12" cy="12" r="10"></circle>
                <polyline points="12 6 12 12 16 14"></polyline>
              </svg>
              <span>
                {isPlainVolunteer
                  ? "My Service Hours"
                  : isCoordinatorOrOfficer
                  ? "Service Hours Review"
                  : "Service Hours"}
              </span>
            </NavLink>
          </div>

          {/* Section: Recognition */}
          <div className="nav-group">
            <span className="nav-group-title">Recognition</span>
            <NavLink
              to="/achievements"
              className={({ isActive }) => `sidebar-link ${isActive ? "active" : ""}`}
              onClick={handleLinkClick}
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="12" cy="8" r="7"></circle>
                <polyline points="8.21 13.89 7 23 12 20 17 23 15.79 13.88"></polyline>
              </svg>
              <span>Achievements</span>
              <span className="sidebar-badge-future">Future</span>
            </NavLink>
            <NavLink
              to="/certificates"
              className={({ isActive }) => `sidebar-link ${isActive ? "active" : ""}`}
              onClick={handleLinkClick}
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <rect x="2" y="3" width="20" height="14" rx="2" ry="2"></rect>
                <line x1="8" y1="21" x2="16" y2="21"></line>
                <line x1="12" y1="17" x2="12" y2="21"></line>
              </svg>
              <span>Certificates</span>
              <span className="sidebar-badge-future">Future</span>
            </NavLink>
          </div>

          {/* Section: Communication */}
          <div className="nav-group">
            <span className="nav-group-title">Communication</span>
            <NavLink
              to="/announcements"
              className={({ isActive }) => `sidebar-link ${isActive ? "active" : ""}`}
              onClick={handleLinkClick}
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path>
              </svg>
              <span>Announcements</span>
            </NavLink>
          </div>

          {/* Section: Documents (Officers, Coordinators, and Admins) */}
          {isCoordinatorOrOfficer && (
            <div className="nav-group">
              <span className="nav-group-title">Documents</span>
              <NavLink
                to="/activity-reports"
                className={({ isActive }) => `sidebar-link ${isActive ? "active" : ""}`}
                onClick={handleLinkClick}
              >
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                  <polyline points="14 2 14 8 20 8"></polyline>
                  <line x1="16" y1="13" x2="8" y2="13"></line>
                  <line x1="16" y1="17" x2="8" y2="17"></line>
                  <polyline points="10 9 9 9 8 9"></polyline>
                </svg>
                <span>Activity Reports</span>
                <span className="sidebar-badge-future">Future</span>
              </NavLink>
              <NavLink
                to="/documents"
                className={({ isActive }) => `sidebar-link ${isActive ? "active" : ""}`}
                onClick={handleLinkClick}
              >
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path>
                </svg>
                <span>Repository</span>
                <span className="sidebar-badge-future">Future</span>
              </NavLink>
            </div>
          )}

          {/* Section: Reports & Analytics (Officers, Coordinators, and Admins) */}
          {isCoordinatorOrOfficer && (
            <div className="nav-group">
              <span className="nav-group-title">Reports &amp; Analytics</span>
              <NavLink
                to="/reports"
                className={({ isActive }) => `sidebar-link ${isActive ? "active" : ""}`}
                onClick={handleLinkClick}
              >
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <line x1="18" y1="20" x2="18" y2="10"></line>
                  <line x1="12" y1="20" x2="12" y2="4"></line>
                  <line x1="6" y1="20" x2="6" y2="14"></line>
                </svg>
                <span>Analytics &amp; Exports</span>
              </NavLink>
            </div>
          )}

          {/* Section: Administration (Administrator Only) */}
          {isAdmin && (
            <div className="nav-group nav-group-admin">
              <span className="nav-group-title">Administration</span>
              <NavLink
                to="/admin"
                className={({ isActive }) => `sidebar-link ${isActive ? "active" : ""}`}
                onClick={handleLinkClick}
              >
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <circle cx="12" cy="12" r="3"></circle>
                  <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"></path>
                </svg>
                <span>Institutional Admin</span>
              </NavLink>
            </div>
          )}
        </nav>
      </div>

      <div className="sidebar-footer">
        <span className="nss-motto">Not Me But You</span>
        <span className="nss-academic-year">Academic Year 2025–26</span>
      </div>
    </aside>
  );
};
