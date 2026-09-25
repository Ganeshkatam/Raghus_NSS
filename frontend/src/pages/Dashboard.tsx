import React from "react";
import { useAuth } from "../context/AuthContext";
import { VolunteerDashboard } from "../features/dashboard/VolunteerDashboard";
import { StudentLeaderDashboard } from "../features/dashboard/StudentLeaderDashboard";
import { ProgrammeOfficerDashboard } from "../features/dashboard/ProgrammeOfficerDashboard";
import { InstitutionalDashboard } from "../features/dashboard/InstitutionalDashboard";
import { AdminDashboard } from "../features/dashboard/AdminDashboard";

export const Dashboard: React.FC = () => {
  const { primaryRole } = useAuth();

  return (
    <div className="page-container">
      {primaryRole === "VOLUNTEER" && <VolunteerDashboard />}
      {primaryRole === "STUDENT_LEADER" && <StudentLeaderDashboard />}
      {primaryRole === "PROGRAMME_OFFICER" && <ProgrammeOfficerDashboard />}
      {primaryRole === "FACULTY_COORDINATOR" && <InstitutionalDashboard />}
      {primaryRole === "ADMIN" && <AdminDashboard />}
    </div>
  );
};
