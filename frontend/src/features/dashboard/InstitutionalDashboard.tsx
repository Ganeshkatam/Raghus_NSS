import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { apiRequest } from "../../api/client";

interface InstitutionalMetrics {
  totalVolunteers: number;
  activeVolunteers: number;
  totalUnits: number;
  totalEvents: number;
  completedEvents: number;
  totalServiceHours: number;
}

interface UnitSummary {
  unitId: string;
  unitName: string;
  unitNumber: string;
  officerName: string | null;
  officerEmail: string | null;
  activeMemberCount: number;
  capacity?: number;
}

export const InstitutionalDashboard: React.FC = () => {
  const { user, isAdmin } = useAuth();
  const [metrics, setMetrics] = useState<InstitutionalMetrics | null>(null);
  const [units, setUnits] = useState<UnitSummary[]>([]);
  const [pendingApprovals, setPendingApprovals] = useState<number>(0);
  const [pendingClaims, setPendingClaims] = useState<number>(0);
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    async function loadData() {
      setLoading(true);
      try {
        const [summaryData, unitsData] = await Promise.all([
          apiRequest<any>("/dashboard/summary").catch(() => null),
          apiRequest<UnitSummary[]>("/units").catch(() => []),
        ]);

        if (summaryData?.institutionalData) {
          const id = summaryData.institutionalData;
          setMetrics({
            totalVolunteers: id.totalVolunteers || 0,
            activeVolunteers: id.activeVolunteers || 0,
            totalUnits: id.totalUnits || 0,
            totalEvents: id.totalEvents || 0,
            completedEvents: id.completedEvents || 0,
            totalServiceHours: id.totalServiceHours || 0,
          });
          setPendingApprovals(id.pendingApprovals || 0);
          setPendingClaims(id.pendingClaims || 0);
        }
        const unitList = Array.isArray(unitsData)
          ? unitsData
          : Array.isArray((unitsData as any)?.content)
          ? (unitsData as any).content
          : [];
        setUnits(unitList);
      } finally {
        setLoading(false);
      }
    }
    loadData();
  }, []);

  if (loading) {
    return <p className="loading-state">Loading central institutional dashboard...</p>;
  }

  const assignedOfficersCount = units.filter(
    (u) => u.officerName && u.officerName.trim() !== "" && u.officerName !== "Unassigned"
  ).length;
  const totalUnitsCount = metrics?.totalUnits ?? units.length;

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>
      {/* Header */}
      <div className="page-header">
        <div>
          <div className="breadcrumbs">
            <span>Raghu Engineering College</span>
            <span>/</span>
            <span className="current">Central NSS Oversight</span>
          </div>
          <h1>{isAdmin ? "Institutional System Administration" : "Central College NSS Oversight"}</h1>
          <p className="subtitle">
            Governance authority: <strong>{user?.name}</strong> &bull; Total Operational Units: <strong>{units.length}</strong>
          </p>
        </div>
        <div className="card-actions">
          <Link to="/reports" className="btn-primary">
            Institutional Reports
          </Link>
          <Link to="/units" className="btn-secondary">
            Manage Units
          </Link>
          {isAdmin && (
            <Link to="/admin" className="btn-secondary">
              Administration Console
            </Link>
          )}
        </div>
      </div>

      {/* Institutional KPI Cards */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: "1rem" }}>
        <div className="section-card" style={{ padding: "1.25rem" }}>
          <div className="cell-sub" style={{ textTransform: "uppercase", fontSize: "0.75rem", letterSpacing: "0.05em" }}>
            Total Volunteers
          </div>
          <div style={{ fontSize: "2rem", fontWeight: 800, color: "#1e40af", marginTop: "0.25rem" }}>
            {metrics?.totalVolunteers ?? 0}
          </div>
          <span style={{ fontSize: "0.8rem", color: "#16a34a", fontWeight: 600 }}>
            {metrics?.activeVolunteers ?? 0} active in units
          </span>
        </div>

        <div className="section-card" style={{ padding: "1.25rem" }}>
          <div className="cell-sub" style={{ textTransform: "uppercase", fontSize: "0.75rem", letterSpacing: "0.05em" }}>
            Configured Units
          </div>
          <div style={{ fontSize: "2rem", fontWeight: 800, color: "#0f766e", marginTop: "0.25rem" }}>
            {totalUnitsCount}
          </div>
          <span
            style={{
              fontSize: "0.8rem",
              color: assignedOfficersCount === totalUnitsCount && assignedOfficersCount > 0 ? "#16a34a" : "#dc2626",
              fontWeight: 600,
            }}
          >
            {assignedOfficersCount === 0
              ? "0 active faculty officers assigned"
              : `${assignedOfficersCount} of ${totalUnitsCount} units with assigned officer`}
          </span>
        </div>

        <div className="section-card" style={{ padding: "1.25rem" }}>
          <div className="cell-sub" style={{ textTransform: "uppercase", fontSize: "0.75rem", letterSpacing: "0.05em" }}>
            Programmes Executed
          </div>
          <div style={{ fontSize: "2rem", fontWeight: 800, color: "#7c3aed", marginTop: "0.25rem" }}>
            {metrics?.totalEvents ?? 0}
          </div>
          <span style={{ fontSize: "0.8rem", color: "#64748b" }}>
            {metrics?.completedEvents ?? 0} completed successfully
          </span>
        </div>

        <div className="section-card" style={{ padding: "1.25rem" }}>
          <div className="cell-sub" style={{ textTransform: "uppercase", fontSize: "0.75rem", letterSpacing: "0.05em" }}>
            Accredited Hours
          </div>
          <div style={{ fontSize: "2rem", fontWeight: 800, color: "#ea580c", marginTop: "0.25rem" }}>
            {metrics?.totalServiceHours ?? 0} <span style={{ fontSize: "1rem", fontWeight: 500 }}>hrs</span>
          </div>
          <span style={{ fontSize: "0.8rem", color: "#64748b" }}>
            Verified student service ledger
          </span>
        </div>
      </div>

      {/* Institutional Action Queues Banner */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))", gap: "1rem" }}>
        <div style={{ padding: "1rem", backgroundColor: "#fffbeb", border: "1px solid #fde68a", borderRadius: "0.5rem" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <div>
              <strong style={{ color: "#92400e" }}>Pending Onboarding Approvals</strong>
              <div style={{ fontSize: "0.85rem", color: "#b45309" }}>{pendingApprovals} volunteer registrations awaiting officer verification</div>
            </div>
            <Link to="/volunteers?status=PENDING_APPROVAL" className="btn-primary-sm">
              Review
            </Link>
          </div>
        </div>

        <div style={{ padding: "1rem", backgroundColor: "#ecfdf5", border: "1px solid #a7f3d0", borderRadius: "0.5rem" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <div>
              <strong style={{ color: "#065f46" }}>Service Hour Claims Queue</strong>
              <div style={{ fontSize: "0.85rem", color: "#047857" }}>{pendingClaims} claims requiring coordinator or officer verification</div>
            </div>
            <Link to="/service-hours" className="btn-primary-sm">
              Audit
            </Link>
          </div>
        </div>
      </div>

      {/* Multi-Unit Overview Table */}
      <div className="section-card">
        <div className="section-header">
          <div>
            <h3>NSS Operational Units Comparison</h3>
            <p className="subtitle">Capacity allocation, faculty officer jurisdiction, and participation rosters.</p>
          </div>
          <Link to="/units" className="btn-secondary-sm">
            Units Console
          </Link>
        </div>

        {units.length === 0 ? (
          <p className="empty-state">No operational units configured.</p>
        ) : (
          <div className="units-table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Unit Number</th>
                  <th>Unit Name</th>
                  <th>Officer In Charge</th>
                  <th>Roster / Capacity</th>
                  <th>Capacity Utilisation</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {units.map((u) => {
                  const cap = u.capacity;
                  const percent = cap && cap > 0 ? Math.min(100, Math.round((u.activeMemberCount / cap) * 100)) : null;
                  return (
                    <tr key={u.unitId}>
                      <td>
                        <span className="badge badge-primary">{u.unitNumber}</span>
                      </td>
                      <td>
                        <strong>{u.unitName}</strong>
                      </td>
                      <td>
                        {u.officerName && u.officerName !== "Unassigned" ? (
                          <div>
                            <div>{u.officerName}</div>
                            <span className="cell-sub">{u.officerEmail || "\u2014"}</span>
                          </div>
                        ) : (
                          <div>
                            <span className="badge badge-warning" style={{ fontSize: "0.75rem" }}>Unassigned</span>
                            {isAdmin && (
                              <div style={{ marginTop: "0.25rem" }}>
                                <Link to={`/units/${u.unitId}`} className="table-action-link" style={{ fontSize: "0.75rem" }}>
                                  Assign Officer &rarr;
                                </Link>
                              </div>
                            )}
                          </div>
                        )}
                      </td>
                      <td>
                        <strong>{u.activeMemberCount}</strong> {cap ? `/ ${cap}` : ""}
                      </td>
                      <td>
                        {percent !== null ? (
                          <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
                            <div style={{ flex: 1, height: "6px", backgroundColor: "#e2e8f0", borderRadius: "9999px", overflow: "hidden", maxWidth: "80px" }}>
                              <div
                                style={{
                                  width: `${percent}%`,
                                  height: "100%",
                                  backgroundColor: percent >= 90 ? "#e11d48" : "#2563eb",
                                }}
                              />
                            </div>
                            <span style={{ fontSize: "0.75rem", fontWeight: 700, color: "#64748b" }}>{percent}%</span>
                          </div>
                        ) : (
                          <span style={{ fontSize: "0.75rem", color: "#94a3b8" }}>&mdash;</span>
                        )}
                      </td>
                      <td>
                        <Link to={`/units/${u.unitId}`} className="table-action-link">
                          Manage
                        </Link>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};
