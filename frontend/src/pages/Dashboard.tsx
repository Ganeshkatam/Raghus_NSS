import React from "react";
import { useAuth } from "../context/AuthContext";
import { VolunteerDashboard } from "../features/dashboard/VolunteerDashboard";
import { ProgrammeOfficerDashboard } from "../features/dashboard/ProgrammeOfficerDashboard";
import { InstitutionalDashboard } from "../features/dashboard/InstitutionalDashboard";

export const Dashboard: React.FC = () => {
  const { isVolunteer, isCoordinatorOrOfficer, isStudentLeader, isOfficer, isAdmin, isCoordinator } = useAuth();

  const isPlainVolunteer = isVolunteer && !isCoordinatorOrOfficer && !isStudentLeader;

  return (
    <div className="page-container">
      {isPlainVolunteer ? (
        <VolunteerDashboard />
      ) : isOfficer && !isAdmin && !isCoordinator ? (
        <ProgrammeOfficerDashboard />
      ) : (
        <InstitutionalDashboard />
      )}
    </div>
  );
};
