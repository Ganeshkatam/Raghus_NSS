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
import { ModulePlaceholder } from "./pages/ModulePlaceholder";

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

      {/* People */}
      <Route
        path="/volunteers"
        element={
          <ProtectedRoute>
            <Volunteers />
          </ProtectedRoute>
        }
      />
      <Route
        path="/volunteers/:id"
        element={
          <ProtectedRoute>
            <VolunteerDetail />
          </ProtectedRoute>
        }
      />

      {/* Organisation */}
      <Route
        path="/units"
        element={
          <ProtectedRoute>
            <Units />
          </ProtectedRoute>
        }
      />
      <Route
        path="/units/:id"
        element={
          <ProtectedRoute>
            <UnitDetail />
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
              phase="Phase 7 — Camps & Special Operations"
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
            <ModulePlaceholder
              title="Attendance Operations"
              category="Participation"
              description="QR attendance check-ins, live session monitoring, and audited attendance correction workflows."
              phase="Phase 5 — Attendance & QR Workflow"
              features={[
                "Real-time event attendance sessions with server-authoritative timestamps",
                "Mobile-first volunteer QR code verification scanner",
                "Programme Officer attendance roster with verified attendance counts",
                "Audited correction requests with reason logging and ledger adjustments"
              ]}
            />
          </ProtectedRoute>
        }
      />
      <Route
        path="/service-hours"
        element={
          <ProtectedRoute>
            <ModulePlaceholder
              title="Service Hours Ledger"
              category="Participation"
              description="Institutional verified service-hour ledger, academic-year aggregates, and approval tracking."
              phase="Phase 6 — Service Hours Ledger"
              features={[
                "Immutable institutional ledger entries derived from verified attendance",
                "Academic-year volunteer service hour accumulation and progress towards 120/240hr milestones",
                "Programme Officer adjustment approvals with full audit attribution"
              ]}
            />
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
              phase="Phase 9 — Recognition & Awards"
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
              phase="Phase 9 — Certificates & Credentials"
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
            <ModulePlaceholder
              title="Official Announcements"
              category="Communication"
              description="Institutional broadcasts, circulars, event guidelines, and emergency notices."
              phase="Phase 8 — Operations & Announcements"
              features={[
                "Targeted announcements by unit, academic year, or college-wide audience",
                "Circular attachment repository and urgent broadcast flags",
                "Volunteer read receipts and push notification integration"
              ]}
            />
          </ProtectedRoute>
        }
      />
      <Route
        path="/notifications"
        element={
          <ProtectedRoute>
            <ModulePlaceholder
              title="Notification Center"
              category="Communication"
              description="Actionable alerts, registration reminders, and verification notifications."
              phase="Phase 8 — Notification Engine"
              features={[
                "Event registration and cancellation receipts",
                "Registration deadline reminders and capacity alerts",
                "Attendance check-in confirmations and service-hour audit notifications"
              ]}
            />
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
              phase="Phase 8 — Activity Reports"
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
              phase="Phase 8 — Document Management"
              features={[
                "Categorized repository for Ministry of Youth Affairs circulars and guidelines",
                "Enrollment forms, medical fitness templates, and parent consent forms",
                "Version-controlled institutional archive"
              ]}
            />
          </ProtectedRoute>
        }
      />

      {/* Reports & Analytics */}
      <Route
        path="/reports"
        element={
          <ProtectedRoute>
            <ModulePlaceholder
              title="Institutional Analytics & Exports"
              category="Reports"
              description="Statistical summaries, volunteer distribution, attendance rates, and university report exports."
              phase="Phase 10 — Institutional Reporting"
              features={[
                "Academic-year volunteer enrollment and demographic distribution",
                "NSS unit activity heatmaps and hours-completed compliance summaries",
                "Spreadsheet-compatible CSV and formal PDF report downloads"
              ]}
            />
          </ProtectedRoute>
        }
      />

      {/* Administration */}
      <Route
        path="/admin"
        element={
          <ProtectedRoute>
            <ModulePlaceholder
              title="Institutional Administration"
              category="Administration"
              description="User access control, role assignments, system status, and immutable audit logs."
              phase="Phase 11 — Advanced Administration"
              features={[
                "Staff and student leader role assignment and permission tuning",
                "System health diagnostics, database migration status, and cache controls",
                "Comprehensive audit event log query tool"
              ]}
            />
          </ProtectedRoute>
        }
      />

      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  );
};

export const App: React.FC = () => {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AppContent />
      </AuthProvider>
    </BrowserRouter>
  );
};
