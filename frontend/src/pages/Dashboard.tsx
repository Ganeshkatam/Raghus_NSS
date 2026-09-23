import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { apiRequest } from "../api/client";

interface UnitSummary {
  unitId: number;
  unitName: string;
  unitNumber: string;
  activeMemberCount: number;
}

export const Dashboard: React.FC = () => {
  const { user, isCoordinatorOrOfficer } = useAuth();
  const [units, setUnits] = useState<UnitSummary[]>([]);
  const [volunteerCount, setVolunteerCount] = useState<number>(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function loadStats() {
      try {
        const unitsData = await apiRequest<UnitSummary[]>("/units");
        setUnits(unitsData);

        if (isCoordinatorOrOfficer) {
          const volPage = await apiRequest<{ totalElements: number }>("/volunteers?size=1");
          setVolunteerCount(volPage.totalElements || 0);
        }
      } catch {
        // Ignored in initial dashboard preview
      } finally {
        setLoading(false);
      }
    }
    loadStats();
  }, [isCoordinatorOrOfficer]);

  const totalMemberships = units.reduce((acc, u) => acc + (u.activeMemberCount || 0), 0);

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h1>Welcome, {user?.name}</h1>
          <p className="subtitle">
            Role: <strong>{user?.roles[0]?.replace("ROLE_", "")}</strong> — NSS Institutional Workspace
          </p>
        </div>
      </div>

      <div className="stats-grid">
        <div className="stat-card">
          <span className="stat-label">Configured NSS Units</span>
          <span className="stat-value">{loading ? "..." : units.length}</span>
          <span className="stat-desc">Active operational units</span>
        </div>

        {isCoordinatorOrOfficer && (
          <div className="stat-card">
            <span className="stat-label">Enrolled Volunteers</span>
            <span className="stat-value">{loading ? "..." : volunteerCount}</span>
            <span className="stat-desc">Registered student volunteers</span>
          </div>
        )}

        <div className="stat-card">
          <span className="stat-label">Active Unit Memberships</span>
          <span className="stat-value">{loading ? "..." : totalMemberships}</span>
          <span className="stat-desc">Volunteers currently assigned to units</span>
        </div>
      </div>

      <div className="section-card">
        <div className="section-header">
          <h2>Active NSS Units</h2>
          <Link to="/units" className="btn-secondary-sm">
            View All Units
          </Link>
        </div>

        {loading ? (
          <p className="loading-state">Loading NSS units...</p>
        ) : units.length === 0 ? (
          <div className="empty-state">
            <p>No NSS units have been configured yet.</p>
            {isCoordinatorOrOfficer && (
              <Link to="/units" className="btn-primary-sm">
                Create First Unit
              </Link>
            )}
          </div>
        ) : (
          <div className="units-table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Unit Number</th>
                  <th>Unit Name</th>
                  <th>Active Members</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {units.map((unit) => (
                  <tr key={unit.unitId}>
                    <td>
                      <span className="badge badge-primary">{unit.unitNumber}</span>
                    </td>
                    <td>{unit.unitName}</td>
                    <td>{unit.activeMemberCount} members</td>
                    <td>
                      <Link to={`/units/${unit.unitId}`} className="table-action-link">
                        Manage Unit &rarr;
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};
