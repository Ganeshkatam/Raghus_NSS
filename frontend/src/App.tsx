import React from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { AuthProvider, useAuth } from "./context/AuthContext";
import { AppShell } from "./components/AppShell";
import { Login } from "./pages/Login";
import { Dashboard } from "./pages/Dashboard";
import { Volunteers } from "./pages/Volunteers";
import { VolunteerDetail } from "./pages/VolunteerDetail";
import { Units } from "./pages/Units";
import { UnitDetail } from "./pages/UnitDetail";
import { Events } from "./pages/Events";
import { EventDetail } from "./pages/EventDetail";
import { Attendance } from "./pages/Attendance";
import { ServiceHours } from "./pages/ServiceHours";
import { Announcements } from "./pages/Announcements";
import { Reports } from "./pages/Reports";
import { Admin } from "./pages/Admin";
import { ModulePlaceholder } from "./pages/ModulePlaceholder";
import { ErrorBoundary } from "./components/ErrorBoundary";
import { UpdateBanner } from "./components/UpdateBanner";

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

const OfficerRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isCoordinatorOrOfficer, isStudentLeader, loading } = useAuth();

  if (loading) {
    return (
      <div className="page-container">
        <p className="loading-state">Checking session authorization...</p>
      </div>
    );
  }

  if (!isCoordinatorOrOfficer && !isStudentLeader) {
    return <Navigate to="/dashboard" replace />;
  }

  return <>{children}</>;
};

const AdminRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAdmin, loading } = useAuth();

  if (loading) {
    return (
      <div className="page-container">
        <p className="loading-state">Checking session authorization...</p>
      </div>
    );
  }

  if (!isAdmin) {
    return <Navigate to="/dashboard" replace />;
  }

  return <>{children}</>;
};

export const AppContent: React.FC = () => {
  return (
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
            <OfficerRoute>
              <Volunteers />
            </OfficerRoute>
          </ProtectedRoute>
        }
      />
      <Route
        path="/volunteers/:id"
        element={
          <ProtectedRoute>
            <OfficerRoute>
              <VolunteerDetail />
            </OfficerRoute>
          </ProtectedRoute>
        }
      />

      {/* Organisation (Restricted to Officers, Coordinators, Admins) */}
      <Route
        path="/units"
        element={
          <ProtectedRoute>
            <OfficerRoute>
              <Units />
            </OfficerRoute>
          </ProtectedRoute>
        }
      />
      <Route
        path="/units/:id"
        element={
          <ProtectedRoute>
            <OfficerRoute>
              <UnitDetail />
            </OfficerRoute>
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
                "Rural village camp allocation and student leadership rosters",
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
            <OfficerRoute>
              <Reports />
            </OfficerRoute>
          </ProtectedRoute>
        }
      />

      {/* Administration (Restricted to Admins) */}
      <Route
        path="/admin"
        element={
          <ProtectedRoute>
            <AdminRoute>
              <Admin />
            </AdminRoute>
          </ProtectedRoute>
        }
      />

      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  );
};

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
