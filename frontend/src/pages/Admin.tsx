import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { apiRequest, ApiError } from "../api/client";

interface UnitSummary {
  unitId: string;
  unitName: string;
  unitNumber: string;
  officerName: string | null;
  officerEmail: string | null;
  activeMemberCount: number;
  createdAt: string;
}

interface InstitutionalMetrics {
  totalVolunteers: number;
  activeVolunteers: number;
  totalUnits: number;
  totalEvents: number;
  completedEvents: number;
  totalApprovedHours: number;
}

export const Admin: React.FC = () => {
  const { user } = useAuth();

  const [metrics, setMetrics] = useState<InstitutionalMetrics | null>(null);
  const [units, setUnits] = useState<UnitSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Unit creation modal
  const [showUnitModal, setShowUnitModal] = useState(false);
  const [unitName, setUnitName] = useState("");
  const [unitNumber, setUnitNumber] = useState("");
  const [savingUnit, setSavingUnit] = useState(false);
  const [unitModalError, setUnitModalError] = useState<string | null>(null);

  const loadAdminData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [metricsData, unitsData] = await Promise.all([
        apiRequest<InstitutionalMetrics>("/reports/metrics"),
        apiRequest<UnitSummary[]>("/units"),
      ]);
      setMetrics(metricsData);
      setUnits(unitsData);
    } catch (err: unknown) {
      const apiErr = err as ApiError;
      setError(apiErr.message || "Failed to load administrative console data.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadAdminData();
  }, []);

  const handleCreateUnit = async (e: React.FormEvent) => {
    e.preventDefault();
    setUnitModalError(null);
    setSavingUnit(true);

    try {
      await apiRequest<UnitSummary>("/units", {
        method: "POST",
        body: JSON.stringify({
          unitName: unitName.trim(),
          unitNumber: unitNumber.trim(),
        }),
      });

      setShowUnitModal(false);
      setUnitName("");
      setUnitNumber("");
      await loadAdminData();
    } catch (err: unknown) {
      const apiErr = err as ApiError;
      setUnitModalError(apiErr.message || "Unable to provision NSS unit.");
    } finally {
      setSavingUnit(false);
    }
  };

  return (
    <div className="page-container">
      {/* Header */}
      <div className="page-header">
        <div>
          <h1>Institutional System Administration</h1>
          <p className="subtitle">
            System governance, operational unit provisioning, role hierarchy, and compliance oversight.
          </p>
        </div>
        <div style={{ display: "flex", gap: "0.5rem" }}>
          <button onClick={() => setShowUnitModal(true)} className="btn-primary">
            + Provision NSS Unit
          </button>
        </div>
      </div>

      {error && (
        <div className="alert alert-error">
          <strong>Administrative Notice:</strong> {error}
        </div>
      )}

      {/* System Status Banner */}
      <div
        className="section-card"
        style={{
          borderLeft: "4px solid var(--nss-navy)",
          marginBottom: "1.5rem",
          display: "flex",
          flexWrap: "wrap",
          justifyContent: "space-between",
          alignItems: "center",
          gap: "1rem",
        }}
      >
        <div>
          <div style={{ display: "flex", alignItems: "center", gap: "0.5rem", marginBottom: "0.25rem" }}>
            <span
              style={{
                display: "inline-block",
                width: "10px",
                height: "10px",
                borderRadius: "50%",
                backgroundColor: "#10b981",
              }}
            ></span>
            <strong style={{ fontSize: "1rem" }}>System Status: Operational</strong>
            <span className="badge badge-primary">Academic Year 2025–26</span>
          </div>
          <p className="subtitle" style={{ margin: 0 }}>
            PostgreSQL Primary Database Active &bull; JWT Stateless Security Enforced &bull; Production Host Connected
          </p>
        </div>

        <div style={{ display: "flex", gap: "1rem", flexWrap: "wrap" }}>
          <div style={{ textAlign: "right" }}>
            <div style={{ fontSize: "0.75rem", color: "var(--text-muted)", textTransform: "uppercase" }}>
              Administrator
            </div>
            <div style={{ fontWeight: 600 }}>{user?.name || "System Admin"}</div>
          </div>
          <div style={{ textAlign: "right" }}>
            <div style={{ fontSize: "0.75rem", color: "var(--text-muted)", textTransform: "uppercase" }}>
              Email
            </div>
            <div style={{ fontWeight: 600 }}>{user?.email || "admin@raghunss.edu"}</div>
          </div>
        </div>
      </div>

      {/* Administrative KPIs */}
      <div className="metrics-grid" style={{ marginBottom: "1.5rem" }}>
        <div className="metric-card">
          <div className="metric-header">
            <h3>Registered Volunteers</h3>
          </div>
          <div className="metric-value">
            {loading ? "..." : metrics?.totalVolunteers ?? 0}
          </div>
          <div className="metric-subtext">
            <span>{metrics?.activeVolunteers ?? 0} active in community units</span>
          </div>
        </div>

        <div className="metric-card">
          <div className="metric-header">
            <h3>Operational Units</h3>
          </div>
          <div className="metric-value">
            {loading ? "..." : units.length}
          </div>
          <div className="metric-subtext">
            <span>All college units provisioned</span>
          </div>
        </div>

        <div className="metric-card">
          <div className="metric-header">
            <h3>Programmes Conducted</h3>
          </div>
          <div className="metric-value">
            {loading ? "..." : metrics?.totalEvents ?? 0}
          </div>
          <div className="metric-subtext">
            <span>{metrics?.completedEvents ?? 0} marked completed</span>
          </div>
        </div>

        <div className="metric-card">
          <div className="metric-header">
            <h3>Accredited Service Hours</h3>
          </div>
          <div className="metric-value">
            {loading ? "..." : `${metrics?.totalApprovedHours ?? 0} hrs`}
          </div>
          <div className="metric-subtext">
            <span>Verified institutional credit</span>
          </div>
        </div>
      </div>

      <div className="grid-2-col">
        {/* Left Column: NSS Units Master */}
        <div className="section-card">
          <div className="section-header">
            <div>
              <h2>NSS Units Master Roster</h2>
              <p className="subtitle">Operational units governed under Raghu Engineering College NSS Cell.</p>
            </div>
            <button onClick={() => setShowUnitModal(true)} className="btn-secondary-sm">
              + Add Unit
            </button>
          </div>

          {loading ? (
            <p className="loading-state">Loading operational units roster...</p>
          ) : units.length === 0 ? (
            <div className="empty-state">
              <p>No NSS units configured. Provision your first operational unit.</p>
            </div>
          ) : (
            <div className="table-wrapper">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Unit Code</th>
                    <th>Unit Name</th>
                    <th>Programme Officer</th>
                    <th>Volunteers</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {units.map((unit) => (
                    <tr key={unit.unitId}>
                      <td>
                        <span className="badge badge-primary">{unit.unitNumber}</span>
                      </td>
                      <td>
                        <strong>{unit.unitName}</strong>
                      </td>
                      <td>{unit.officerName || "Unassigned"}</td>
                      <td>
                        <strong>{unit.activeMemberCount}</strong>
                      </td>
                      <td>
                        <Link to={`/units/${unit.unitId}`} className="table-action-link">
                          Manage Unit
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Right Column: Role Hierarchy & Governance Controls */}
        <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>
          {/* Security & Access Hierarchy Card */}
          <div className="section-card">
            <div className="section-header">
              <h2>Role Hierarchy &amp; Access Controls</h2>
            </div>
            <p className="subtitle" style={{ marginBottom: "1rem" }}>
              Server-side security enforced via Spring Security method authorization rules.
            </p>

            <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
              <div
                style={{
                  padding: "0.75rem 1rem",
                  backgroundColor: "var(--bg-subtle)",
                  border: "1px solid var(--border-subtle)",
                  borderRadius: "6px",
                }}
              >
                <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "0.25rem" }}>
                  <strong>ADMIN</strong>
                  <span className="badge badge-primary">Full System Scope</span>
                </div>
                <p style={{ fontSize: "0.82rem", color: "var(--text-secondary)", margin: 0 }}>
                  System governance, unit creation, role delegation, cross-unit audit, and server configuration.
                </p>
              </div>

              <div
                style={{
                  padding: "0.75rem 1rem",
                  backgroundColor: "var(--bg-subtle)",
                  border: "1px solid var(--border-subtle)",
                  borderRadius: "6px",
                }}
              >
                <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "0.25rem" }}>
                  <strong>FACULTY_COORDINATOR</strong>
                  <span className="badge badge-muted">Cross-Unit Review</span>
                </div>
                <p style={{ fontSize: "0.82rem", color: "var(--text-secondary)", margin: 0 }}>
                  Institutional oversight, event publishing approval, and cross-unit analytics review.
                </p>
              </div>

              <div
                style={{
                  padding: "0.75rem 1rem",
                  backgroundColor: "var(--bg-subtle)",
                  border: "1px solid var(--border-subtle)",
                  borderRadius: "6px",
                }}
              >
                <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "0.25rem" }}>
                  <strong>PROGRAMME_OFFICER</strong>
                  <span className="badge badge-muted">Unit Operational Scope</span>
                </div>
                <p style={{ fontSize: "0.82rem", color: "var(--text-secondary)", margin: 0 }}>
                  Assigned unit volunteer approvals, attendance sessions, and service hour validation.
                </p>
              </div>

              <div
                style={{
                  padding: "0.75rem 1rem",
                  backgroundColor: "var(--bg-subtle)",
                  border: "1px solid var(--border-subtle)",
                  borderRadius: "6px",
                }}
              >
                <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "0.25rem" }}>
                  <strong>VOLUNTEER</strong>
                  <span className="badge badge-muted">Self-Service Scope</span>
                </div>
                <p style={{ fontSize: "0.82rem", color: "var(--text-secondary)", margin: 0 }}>
                  Profile maintenance, programme enrollment, QR check-in, and service hour claim filing.
                </p>
              </div>
            </div>
          </div>

          {/* Quick Administrative Operations */}
          <div className="section-card">
            <div className="section-header">
              <h2>Administrative Fast Actions</h2>
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: "0.5rem" }}>
              <Link to="/volunteers" className="btn-secondary">
                Browse Full Volunteer Registry
              </Link>
              <Link to="/units" className="btn-secondary">
                Configure NSS Operational Units
              </Link>
              <Link to="/events" className="btn-secondary">
                Manage Events and Activities
              </Link>
              <Link to="/service-hours" className="btn-secondary">
                Review Pending Service Hour Claims
              </Link>
              <Link to="/reports" className="btn-secondary">
                Generate Institutional Accreditation Reports
              </Link>
            </div>
          </div>
        </div>
      </div>

      {/* Provision NSS Unit Modal */}
      {showUnitModal && (
        <div className="modal-backdrop">
          <div className="modal-card">
            <div className="modal-header">
              <h2>Provision New NSS Unit</h2>
              <button
                type="button"
                onClick={() => setShowUnitModal(false)}
                className="btn-close"
                aria-label="Close modal"
              >
                &times;
              </button>
            </div>

            {unitModalError && (
              <div className="alert alert-error">
                <strong>Error:</strong> {unitModalError}
              </div>
            )}

            <form onSubmit={handleCreateUnit}>
              <div className="form-group">
                <label htmlFor="unitNumber">Unit Number / Code (e.g. REC-06)</label>
                <input
                  id="unitNumber"
                  type="text"
                  required
                  placeholder="e.g. REC-06"
                  value={unitNumber}
                  onChange={(e) => setUnitNumber(e.target.value)}
                  className="form-control"
                />
              </div>

              <div className="form-group">
                <label htmlFor="unitName">Unit Name</label>
                <input
                  id="unitName"
                  type="text"
                  required
                  placeholder="e.g. Mechanical & Civil Engineering Unit"
                  value={unitName}
                  onChange={(e) => setUnitName(e.target.value)}
                  className="form-control"
                />
              </div>

              <div className="modal-actions">
                <button
                  type="button"
                  onClick={() => setShowUnitModal(false)}
                  className="btn-secondary"
                  disabled={savingUnit}
                >
                  Cancel
                </button>
                <button type="submit" className="btn-primary" disabled={savingUnit}>
                  {savingUnit ? "Provisioning..." : "Provision Unit"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
