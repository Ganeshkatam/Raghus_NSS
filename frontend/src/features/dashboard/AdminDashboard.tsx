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
  pendingApprovals: number;
  pendingClaims: number;
}

interface UnitSummary {
  unitId: string;
  unitName: string;
  unitNumber: string;
  officerName: string | null;
  activeMemberCount: number;
  capacity?: number;
}

interface AuditItem {
  id: string;
  entityType: string;
  action: string;
  actorName: string;
  timestamp: string;
  reason: string;
}

export const AdminDashboard: React.FC = () => {
  const { user } = useAuth();
  const [metrics, setMetrics] = useState<InstitutionalMetrics | null>(null);
  const [units, setUnits] = useState<UnitSummary[]>([]);
  const [recentAudits, setRecentAudits] = useState<AuditItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function loadData() {
      setLoading(true);
      try {
        const [summaryData, unitsData, auditData] = await Promise.all([
          apiRequest<any>("/dashboard/summary").catch(() => null),
          apiRequest<UnitSummary[]>("/units").catch(() => []),
          apiRequest<{ content: AuditItem[] }>("/admin/audit-logs?size=5").catch(() => ({ content: [] })),
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
            pendingApprovals: id.pendingApprovals || 0,
            pendingClaims: id.pendingClaims || 0,
          });
        }

        const unitList = Array.isArray(unitsData)
          ? unitsData
          : Array.isArray((unitsData as any)?.content)
          ? (unitsData as any).content
          : [];
        setUnits(unitList);

        const auditList = Array.isArray(auditData)
          ? auditData
          : Array.isArray((auditData as any)?.content)
          ? (auditData as any).content
          : [];
        setRecentAudits(auditList);
      } finally {
        setLoading(false);
      }
    }
    loadData();
  }, []);

  if (loading) {
    return <p className="loading-state">Loading institutional administration console...</p>;
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>
      {/* Header */}
      <div className="page-header">
        <div>
          <div className="breadcrumbs">
            <span>Raghu Engineering College</span>
            <span>/</span>
            <span className="current">System Administration</span>
          </div>
          <h1>Institutional Governance &amp; Administration</h1>
          <p className="subtitle">
            System Administrator: <strong>{user?.name}</strong> &bull; Total Operational Units: <strong>{units.length}</strong>
          </p>
        </div>
        <div className="card-actions">
          <Link to="/admin" className="btn-primary">
            Users &amp; Roles
          </Link>
          <Link to="/units" className="btn-secondary">
            Manage Units
          </Link>
          <Link to="/reports" className="btn-secondary">
            Reports &amp; Exports
          </Link>
        </div>
      </div>

      {/* Institutional Overview Metrics */}
      <div className="stats-grid">
        <div className="stat-card">
          <div className="stat-label">Total Volunteers</div>
          <div className="stat-value">{metrics?.totalVolunteers ?? "—"}</div>
          <p className="stat-meta">{metrics?.activeVolunteers ?? 0} active members</p>
        </div>
        <div className="stat-card">
          <div className="stat-label">Operational Units</div>
          <div className="stat-value">{metrics?.totalUnits ?? units.length}</div>
          <p className="stat-meta">REC campus units</p>
        </div>
        <div className="stat-card">
          <div className="stat-label">Programmes Organized</div>
          <div className="stat-value">{metrics?.totalEvents ?? "—"}</div>
          <p className="stat-meta">{metrics?.completedEvents ?? 0} completed</p>
        </div>
        <div className="stat-card">
          <div className="stat-label">Total Service Hours</div>
          <div className="stat-value">{metrics?.totalServiceHours ? Math.round(metrics.totalServiceHours) : "0"}</div>
          <p className="stat-meta">Verified across college</p>
        </div>
      </div>

      {/* Administration & Governance Section */}
      <div className="dashboard-grid">
        {/* Left Column: Units & Governance Summary */}
        <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>
          <div className="card">
            <div className="card-header" style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <h2>NSS Units Governance</h2>
              <Link to="/units" className="btn-secondary-sm">View Units</Link>
            </div>
            {units.length === 0 ? (
              <p className="empty-state">No unit records found.</p>
            ) : (
              <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
                {units.slice(0, 5).map((u) => (
                  <div key={u.unitId} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "0.6rem 0", borderBottom: "1px solid var(--border-color, #e5e7eb)" }}>
                    <div>
                      <strong style={{ fontSize: "0.9rem" }}>{u.unitName} (Unit {u.unitNumber})</strong>
                      <div style={{ fontSize: "0.8rem", color: "var(--text-secondary, #6b7280)", marginTop: "0.15rem" }}>
                        Officer: {u.officerName || "Unassigned"}
                      </div>
                    </div>
                    <span className="badge badge-info">{u.activeMemberCount || 0} members</span>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="card">
            <div className="card-header">
              <h2>System Health &amp; Security Controls</h2>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "1rem", marginTop: "0.5rem" }}>
              <div style={{ padding: "0.75rem", backgroundColor: "var(--bg-secondary, #f9fafb)", borderRadius: "8px" }}>
                <div style={{ fontSize: "0.8rem", color: "var(--text-secondary, #6b7280)" }}>Security Model</div>
                <strong style={{ color: "#16a34a" }}>Enforced</strong>
                <p style={{ fontSize: "0.75rem", margin: "0.25rem 0 0" }}>Strict role-based isolation &amp; unit boundaries</p>
              </div>
              <div style={{ padding: "0.75rem", backgroundColor: "var(--bg-secondary, #f9fafb)", borderRadius: "8px" }}>
                <div style={{ fontSize: "0.8rem", color: "var(--text-secondary, #6b7280)" }}>Audit Trail</div>
                <strong style={{ color: "#2563eb" }}>Active</strong>
                <p style={{ fontSize: "0.75rem", margin: "0.25rem 0 0" }}>Immutable change ledger enabled</p>
              </div>
            </div>
          </div>
        </div>

        {/* Right Column: Recent Audits & Governance Actions */}
        <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>
          <div className="card">
            <div className="card-header" style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <h2>Recent Audit Activity</h2>
              <Link to="/admin" className="btn-secondary-sm">Full Audit</Link>
            </div>
            {recentAudits.length === 0 ? (
              <p className="empty-state">No recent audit log entries.</p>
            ) : (
              <div style={{ display: "flex", flexDirection: "column", gap: "0.5rem" }}>
                {recentAudits.map((a) => (
                  <div key={a.id} style={{ padding: "0.5rem 0", borderBottom: "1px solid var(--border-color, #e5e7eb)", fontSize: "0.85rem" }}>
                    <div style={{ display: "flex", justifyContent: "space-between" }}>
                      <strong>{a.action} ({a.entityType})</strong>
                      <span style={{ fontSize: "0.75rem", color: "var(--text-secondary, #6b7280)" }}>
                        {new Date(a.timestamp).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                      </span>
                    </div>
                    <div style={{ color: "var(--text-secondary, #6b7280)", marginTop: "0.15rem" }}>
                      Actor: {a.actorName} &bull; {a.reason || "Administrative update"}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="card">
            <div className="card-header">
              <h2>Administrative Workflows</h2>
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: "0.5rem", marginTop: "0.5rem" }}>
              <Link to="/admin" className="btn-secondary" style={{ textAlign: "center" }}>
                User &amp; Role Management
              </Link>
              <Link to="/units" className="btn-secondary" style={{ textAlign: "center" }}>
                Create or Configure Unit
              </Link>
              <Link to="/reports" className="btn-secondary" style={{ textAlign: "center" }}>
                Generate Institutional Analytics
              </Link>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
