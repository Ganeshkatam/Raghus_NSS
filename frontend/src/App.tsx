import React from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { AuthProvider, useAuth } from "./context/AuthContext";
import { AppShell } from "./components/AppShell";
import { ErrorBoundary } from "./components/ErrorBoundary";
import { UpdateBanner } from "./components/UpdateBanner";
import { API_BASE_URL } from "./api/client";

import { UserRole } from "./config/navigation";

// Dynamic route-based code splitting
const Login = React.lazy(() => import("./pages/Login").then(m => ({ default: m.Login })));
const Dashboard = React.lazy(() => import("./pages/Dashboard").then(m => ({ default: m.Dashboard })));
const Volunteers = React.lazy(() => import("./pages/Volunteers").then(m => ({ default: m.Volunteers })));
const VolunteerDetail = React.lazy(() => import("./pages/VolunteerDetail").then(m => ({ default: m.VolunteerDetail })));
const Units = React.lazy(() => import("./pages/Units").then(m => ({ default: m.Units })));
const UnitDetail = React.lazy(() => import("./pages/UnitDetail").then(m => ({ default: m.UnitDetail })));
const Events = React.lazy(() => import("./pages/Events").then(m => ({ default: m.Events })));
const EventDetail = React.lazy(() => import("./pages/EventDetail").then(m => ({ default: m.EventDetail })));
const Attendance = React.lazy(() => import("./pages/Attendance").then(m => ({ default: m.Attendance })));
const ServiceHours = React.lazy(() => import("./pages/ServiceHours").then(m => ({ default: m.ServiceHours })));
const Announcements = React.lazy(() => import("./pages/Announcements").then(m => ({ default: m.Announcements })));
const Reports = React.lazy(() => import("./pages/Reports").then(m => ({ default: m.Reports })));
const Admin = React.lazy(() => import("./pages/Admin").then(m => ({ default: m.Admin })));
const ModulePlaceholder = React.lazy(() => import("./pages/ModulePlaceholder").then(m => ({ default: m.ModulePlaceholder })));

const ProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated, loading } = useAuth();

  if (loading) {
    return (
      <div className="page-container">
        <p className="loading-state">Checking session authorization...</p>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return <AppShell>{children}</AppShell>;
};

const RoleRoute: React.FC<{
  allowedRoles: UserRole[];
  children: React.ReactNode;
}> = ({ allowedRoles, children }) => {
  const { primaryRole, loading } = useAuth();

  if (loading) {
    return (
      <div className="page-container">
        <p className="loading-state">Checking session authorization...</p>
      </div>
    );
  }

  if (!allowedRoles.includes(primaryRole)) {
    return <Navigate to="/dashboard" replace />;
  }

  return <>{children}</>;
};

export const AppContent: React.FC = () => {
  // Background keep-alive to keep Render container awake and prevent 503 spin-downs
  React.useEffect(() => {
    const pingBackend = () => {
      fetch(`${API_BASE_URL.replace(/\/api\/v1\/?$/, "")}/actuator/health`, {
        method: "GET",
        cache: "no-store",
      }).catch(() => { });
    };

    // Immediate ping on app startup
    pingBackend();

    // Keep alive every 9 minutes (Render sleeps after 15 minutes of inactivity)
    const interval = setInterval(pingBackend, 9 * 60 * 1000);
    return () => clearInterval(interval);
  }, []);

  return (
    <React.Suspense
      fallback={
        <div className="page-container" style={{ padding: "2rem", textAlign: "center" }}>
          <p className="loading-state">Loading view...</p>
        </div>
      }
    >
      <Routes>
        <Route path="/login" element={<Login />} />

      {/* Overview */}
      <Route
        path="/dashboard"
        element={
          <ProtectedRoute>
            <Dashboard />
          </ProtectedRoute>
        }
      />

      {/* People (Restricted to Officers, Coordinators, Leaders, Admins) */}
      <Route
        path="/volunteers"
        element={
          <ProtectedRoute>
            <RoleRoute allowedRoles={["STUDENT_LEADER", "PROGRAMME_OFFICER", "FACULTY_COORDINATOR", "ADMIN"]}>
              <Volunteers />
            </RoleRoute>
          </ProtectedRoute>
        }
      />
      <Route
        path="/volunteers/:id"
        element={
          <ProtectedRoute>
            <RoleRoute allowedRoles={["STUDENT_LEADER", "PROGRAMME_OFFICER", "FACULTY_COORDINATOR", "ADMIN"]}>
              <VolunteerDetail />
            </RoleRoute>
          </ProtectedRoute>
        }
      />

      {/* Organisation (Restricted to Officers, Coordinators, Admins) */}
      <Route
        path="/units"
        element={
          <ProtectedRoute>
            <RoleRoute allowedRoles={["PROGRAMME_OFFICER", "FACULTY_COORDINATOR", "ADMIN"]}>
              <Units />
            </RoleRoute>
          </ProtectedRoute>
        }
      />
      <Route
        path="/units/:id"
        element={
          <ProtectedRoute>
            <RoleRoute allowedRoles={["PROGRAMME_OFFICER", "FACULTY_COORDINATOR", "ADMIN"]}>
              <UnitDetail />
            </RoleRoute>
          </ProtectedRoute>
        }
      />

      {/* Programmes */}
      <Route
        path="/events"
        element={
          <ProtectedRoute>
            <Events />
          </ProtectedRoute>
        }
      />
      <Route
        path="/events/:id"
        element={
          <ProtectedRoute>
            <EventDetail />
          </ProtectedRoute>
        }
      />
      <Route
        path="/camps"
        element={
          <ProtectedRoute>
            <ModulePlaceholder
              title="Special Camps"
              category="Programmes"
              description="7-day rural immersion camps, village adoption activities, and special residential initiatives."
              phase="Future Scope"
              features={[
                "Rural village camp allocation and student leadership lists",
                "Daily camp schedule and multi-session service hours logging",
                "Community impact assessment and project reporting"
              ]}
            />
          </ProtectedRoute>
        }
      />

      {/* Participation */}
      <Route
        path="/attendance"
        element={
          <ProtectedRoute>
            <Attendance />
          </ProtectedRoute>
        }
      />
      <Route
        path="/service-hours"
        element={
          <ProtectedRoute>
            <ServiceHours />
          </ProtectedRoute>
        }
      />

      {/* Recognition */}
      <Route
        path="/achievements"
        element={
          <ProtectedRoute>
            <ModulePlaceholder
              title="Achievements & Recognition"
              category="Recognition"
              description="Best volunteer awards, leadership citations, and institutional honors."
              phase="Future Scope"
              features={[
                "Annual Best Volunteer and Special Contribution awards",
                "Institutional honors review and nomination workflows",
                "Public recognition wall and student portfolio showcases"
              ]}
            />
          </ProtectedRoute>
        }
      />
      <Route
        path="/certificates"
        element={
          <ProtectedRoute>
            <ModulePlaceholder
              title="Institutional Certificates"
              category="Recognition"
              description="Digitally verified completion certificates, activity credentials, and verifiable downloads."
              phase="Future Scope"
              features={[
                "Official NSS Participation and Special Camp completion certificates",
                "Cryptographic verification codes and PDF credential generation",
                "University academic transcript integration"
              ]}
            />
          </ProtectedRoute>
        }
      />

      {/* Communication */}
      <Route
        path="/announcements"
        element={
          <ProtectedRoute>
            <Announcements />
          </ProtectedRoute>
        }
      />
      <Route
        path="/notifications"
        element={
          <ProtectedRoute>
            <Announcements />
          </ProtectedRoute>
        }
      />

      {/* Documents */}
      <Route
        path="/activity-reports"
        element={
          <ProtectedRoute>
            <ModulePlaceholder
              title="Activity Reports"
              category="Documents"
              description="Standardized post-programme documentation, photos, and compliance submissions."
              phase="Future Scope"
              features={[
                "Structured activity reports linking directly to event and attendance records",
                "Photo gallery and geo-tagged community service documentation",
                "University NSS Cell submission exports"
              ]}
            />
          </ProtectedRoute>
        }
      />
      <Route
        path="/documents"
        element={
          <ProtectedRoute>
            <ModulePlaceholder
              title="Document Repository"
              category="Documents"
              description="Government circulars, NSS operational manuals, and institutional forms."
              phase="Future Scope"
              features={[
                "Categorized repository for Ministry of Youth Affairs circulars and guidelines",
                "Enrollment forms, medical fitness templates, and parent consent forms",
                "Version-controlled institutional archive"
              ]}
            />
          </ProtectedRoute>
        }
      />

      {/* Reports & Analytics (Restricted to Officers, Coordinators, Admins) */}
      <Route
        path="/reports"
        element={
          <ProtectedRoute>
            <RoleRoute allowedRoles={["PROGRAMME_OFFICER", "FACULTY_COORDINATOR", "ADMIN"]}>
              <Reports />
            </RoleRoute>
          </ProtectedRoute>
        }
      />

      {/* Administration (Restricted to Admins) */}
      <Route
        path="/admin"
        element={
          <ProtectedRoute>
            <RoleRoute allowedRoles={["ADMIN"]}>
              <Admin />
            </RoleRoute>
          </ProtectedRoute>
        }
      />

        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </React.Suspense>
  );
}
export const App: React.FC = () => {
  return (
    <ErrorBoundary>
      <BrowserRouter>
        <AuthProvider>
          <UpdateBanner />
          <AppContent />
        </AuthProvider>
      </BrowserRouter>
    </ErrorBoundary>
  );
};