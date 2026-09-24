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

interface UserDirectoryItem {
  userId: string;
  name: string;
  email: string;
  phone: string | null;
  status: string;
  roles: string[];
  permissions: string[];
}

interface AuditLogItem {
  id: string;
  entityType: string;
  entityName: string;
  action: string;
  previousState: string;
  newState: string;
  reason: string;
  actorName: string;
  timestamp: string;
}

type AdminTab = "units" | "users" | "audit" | "health";

export const Admin: React.FC = () => {
  const { user } = useAuth();

  const [activeTab, setActiveTab] = useState<AdminTab>("units");
  const [metrics, setMetrics] = useState<InstitutionalMetrics | null>(null);
  const [units, setUnits] = useState<UnitSummary[]>([]);
  const [users, setUsers] = useState<UserDirectoryItem[]>([]);
  const [auditLogs, setAuditLogs] = useState<AuditLogItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [successNotice, setSuccessNotice] = useState<string | null>(null);

  // Unit creation modal
  const [showUnitModal, setShowUnitModal] = useState(false);
  const [unitName, setUnitName] = useState("");
  const [unitNumber, setUnitNumber] = useState("");
  const [savingUnit, setSavingUnit] = useState(false);
  const [unitModalError, setUnitModalError] = useState<string | null>(null);

  // User filters & role modal
  const [userSearch, setUserSearch] = useState("");
  const [roleFilter, setRoleFilter] = useState("");
  const [selectedUserForRole, setSelectedUserForRole] = useState<UserDirectoryItem | null>(null);
  const [newRole, setNewRole] = useState("VOLUNTEER");
  const [updatingRole, setUpdatingRole] = useState(false);

  // Audit filter
  const [auditSearch, setAuditSearch] = useState("");

  const loadAdminData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [metricsData, unitsData, usersData, auditData] = await Promise.all([
        apiRequest<InstitutionalMetrics>("/reports/metrics"),
        apiRequest<UnitSummary[]>("/units"),
        apiRequest<UserDirectoryItem[]>("/users").catch(() => []),
        apiRequest<AuditLogItem[]>("/users/audit").catch(() => []),
      ]);
      setMetrics(metricsData);
      setUnits(unitsData || []);
      setUsers(usersData || []);
      setAuditLogs(auditData || []);
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
      setSuccessNotice("NSS operational unit provisioned successfully.");
      await loadAdminData();
    } catch (err: unknown) {
      const apiErr = err as ApiError;
      setUnitModalError(apiErr.message || "Unable to provision NSS unit.");
    } finally {
      setSavingUnit(false);
    }
  };

  const handleToggleUserStatus = async (targetUser: UserDirectoryItem) => {
    const nextStatus = targetUser.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
    try {
      await apiRequest(`/users/${targetUser.userId}/status`, {
        method: "PATCH",
        body: JSON.stringify({ status: nextStatus }),
      });
      setSuccessNotice(`User ${targetUser.name} status updated to ${nextStatus}.`);
      await loadAdminData();
    } catch (err: unknown) {
      const apiErr = err as ApiError;
      alert("Failed to update user status: " + apiErr.message);
    }
  };

  const handleUpdateRole = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedUserForRole) return;
    setUpdatingRole(true);
    try {
      await apiRequest(`/users/${selectedUserForRole.userId}/role`, {
        method: "PATCH",
        body: JSON.stringify({ role: newRole }),
      });
      setSuccessNotice(`Role for ${selectedUserForRole.name} updated to ${newRole}.`);
      setSelectedUserForRole(null);
      await loadAdminData();
    } catch (err: unknown) {
      const apiErr = err as ApiError;
      alert("Failed to update role: " + apiErr.message);
    } finally {
      setUpdatingRole(false);
    }
  };

  // Filtered users
  const filteredUsers = users.filter((u) => {
    if (roleFilter && !u.roles.includes(roleFilter)) return false;
    if (!userSearch.trim()) return true;
    const q = userSearch.toLowerCase();
    return u.name.toLowerCase().includes(q) || u.email.toLowerCase().includes(q);
  });

  // Filtered audit logs
  const filteredAuditLogs = auditLogs.filter((log) => {
    if (!auditSearch.trim()) return true;
    const q = auditSearch.toLowerCase();
    return (
      log.entityName.toLowerCase().includes(q) ||
      log.actorName.toLowerCase().includes(q) ||
      log.action.toLowerCase().includes(q) ||
      log.reason.toLowerCase().includes(q)
    );
  });

  return (
    <div className="page-container">
      {/* Header */}
      <div className="page-header" style={{ alignItems: "flex-start" }}>
        <div>
          <h1>Institutional System Administration</h1>
          <p className="subtitle">
            System governance, user directory, role delegation, security audit logs, and operational unit provisioning.
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

      {successNotice && (
        <div className="alert alert-success" style={{ marginBottom: "1.5rem" }}>
          <strong>Success:</strong> {successNotice}
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
            />
            <strong style={{ fontSize: "1rem" }}>System Status: Operational</strong>
            <span className="badge badge-primary">Academic Year 2025–26</span>
          </div>
          <p className="subtitle" style={{ margin: 0 }}>
            PostgreSQL Primary Database Active &bull; JWT Stateless Security Enforced &bull; Production Host Connected
          </p>
        </div>

        <div style={{ display: "flex", gap: "1.5rem", flexWrap: "wrap" }}>
          <div>
            <div style={{ fontSize: "0.75rem", color: "var(--text-muted)", textTransform: "uppercase" }}>
              Administrator
            </div>
            <div style={{ fontWeight: 600 }}>{user?.name || "System Admin"}</div>
          </div>
          <div>
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
            <h3>System Users</h3>
          </div>
          <div className="metric-value">
            {loading ? "..." : users.length}
          </div>
          <div className="metric-subtext">
            <span>Active authentication accounts</span>
          </div>
        </div>

        <div className="metric-card">
          <div className="metric-header">
            <h3>Audit Records</h3>
          </div>
          <div className="metric-value">
            {loading ? "..." : auditLogs.length}
          </div>
          <div className="metric-subtext">
            <span>Immutable state transitions</span>
          </div>
        </div>
      </div>

      {/* Admin Tabs */}
      <div
        style={{
          display: "flex",
          gap: "0.5rem",
          borderBottom: "1px solid #e2e8f0",
          marginBottom: "1.5rem",
          overflowX: "auto",
        }}
      >
        <button
          type="button"
          onClick={() => setActiveTab("units")}
          style={{
            padding: "0.75rem 1.25rem",
            border: "none",
            background: "none",
            borderBottom: activeTab === "units" ? "2px solid #1e40af" : "2px solid transparent",
            color: activeTab === "units" ? "#1e40af" : "#64748b",
            fontWeight: activeTab === "units" ? 700 : 500,
            fontSize: "0.95rem",
            cursor: "pointer",
            whiteSpace: "nowrap",
          }}
        >
          NSS Units Master ({units.length})
        </button>
        <button
          type="button"
          onClick={() => setActiveTab("users")}
          style={{
            padding: "0.75rem 1.25rem",
            border: "none",
            background: "none",
            borderBottom: activeTab === "users" ? "2px solid #1e40af" : "2px solid transparent",
            color: activeTab === "users" ? "#1e40af" : "#64748b",
            fontWeight: activeTab === "users" ? 700 : 500,
            fontSize: "0.95rem",
            cursor: "pointer",
            whiteSpace: "nowrap",
          }}
        >
          User Directory &amp; Roles ({users.length})
        </button>
        <button
          type="button"
          onClick={() => setActiveTab("audit")}
          style={{
            padding: "0.75rem 1.25rem",
            border: "none",
            background: "none",
            borderBottom: activeTab === "audit" ? "2px solid #1e40af" : "2px solid transparent",
            color: activeTab === "audit" ? "#1e40af" : "#64748b",
            fontWeight: activeTab === "audit" ? 700 : 500,
            fontSize: "0.95rem",
            cursor: "pointer",
            whiteSpace: "nowrap",
          }}
        >
          Security &amp; State Audit Trail ({auditLogs.length})
        </button>
        <button
          type="button"
          onClick={() => setActiveTab("health")}
          style={{
            padding: "0.75rem 1.25rem",
            border: "none",
            background: "none",
            borderBottom: activeTab === "health" ? "2px solid #1e40af" : "2px solid transparent",
            color: activeTab === "health" ? "#1e40af" : "#64748b",
            fontWeight: activeTab === "health" ? 700 : 500,
            fontSize: "0.95rem",
            cursor: "pointer",
            whiteSpace: "nowrap",
          }}
        >
          System Health &amp; Security Controls
        </button>
      </div>

      {/* TAB 1: UNITS MASTER */}
      {activeTab === "units" && (
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
                    <th>Active Volunteers</th>
                    <th>Actions</th>
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
      )}

      {/* TAB 2: USER DIRECTORY & ROLES */}
      {activeTab === "users" && (
        <div className="section-card">
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1rem", flexWrap: "wrap", gap: "0.75rem" }}>
            <div>
              <h2 style={{ margin: 0 }}>User Management Directory</h2>
              <p className="subtitle" style={{ margin: 0 }}>
                Manage system users, activate/deactivate accounts, and assign administrative roles.
              </p>
            </div>

            <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap" }}>
              <input
                type="text"
                placeholder="Search user by name or email..."
                value={userSearch}
                onChange={(e) => setUserSearch(e.target.value)}
                style={{ minWidth: "240px" }}
              />
              <select value={roleFilter} onChange={(e) => setRoleFilter(e.target.value)}>
                <option value="">All Roles</option>
                <option value="ADMIN">ADMIN</option>
                <option value="FACULTY_COORDINATOR">FACULTY_COORDINATOR</option>
                <option value="PROGRAMME_OFFICER">PROGRAMME_OFFICER</option>
                <option value="VOLUNTEER">VOLUNTEER</option>
              </select>
            </div>
          </div>

          {loading ? (
            <p className="loading-state">Loading user directory...</p>
          ) : filteredUsers.length === 0 ? (
            <div className="empty-state">
              <p>No user accounts match the current filters.</p>
            </div>
          ) : (
            <div className="table-wrapper">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Email</th>
                    <th>Phone</th>
                    <th>Status</th>
                    <th>Assigned Roles</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredUsers.map((u) => (
                    <tr key={u.userId}>
                      <td>
                        <strong>{u.name}</strong>
                      </td>
                      <td>{u.email}</td>
                      <td>{u.phone || "-"}</td>
                      <td>
                        <span
                          className={`badge ${
                            u.status === "ACTIVE"
                              ? "badge-success"
                              : u.status === "PENDING"
                              ? "badge-warning"
                              : "badge-muted"
                          }`}
                        >
                          {u.status}
                        </span>
                      </td>
                      <td>
                        <div style={{ display: "flex", gap: "0.25rem", flexWrap: "wrap" }}>
                          {u.roles.map((r) => (
                            <span key={r} className="badge badge-primary" style={{ fontSize: "0.75rem" }}>
                              {r}
                            </span>
                          ))}
                        </div>
                      </td>
                      <td>
                        <div style={{ display: "flex", gap: "0.4rem" }}>
                          <button
                            type="button"
                            className="btn-secondary-sm"
                            style={{ fontSize: "0.75rem" }}
                            onClick={() => {
                              setSelectedUserForRole(u);
                              setNewRole(u.roles[0] || "VOLUNTEER");
                            }}
                          >
                            Edit Role
                          </button>
                          <button
                            type="button"
                            className={u.status === "ACTIVE" ? "btn-danger-sm" : "btn-primary-sm"}
                            style={{ fontSize: "0.75rem" }}
                            onClick={() => handleToggleUserStatus(u)}
                          >
                            {u.status === "ACTIVE" ? "Deactivate" : "Activate"}
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* TAB 3: AUDIT TRAIL */}
      {activeTab === "audit" && (
        <div className="section-card">
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1rem", flexWrap: "wrap", gap: "0.75rem" }}>
            <div>
              <h2 style={{ margin: 0 }}>Security &amp; State Audit Trail</h2>
              <p className="subtitle" style={{ margin: 0 }}>
                Immutable ledger of volunteer status approvals, unit transfers, and role actions.
              </p>
            </div>
            <input
              type="text"
              placeholder="Search audit events by name, actor, reason..."
              value={auditSearch}
              onChange={(e) => setAuditSearch(e.target.value)}
              style={{ minWidth: "260px" }}
            />
          </div>

          {loading ? (
            <p className="loading-state">Loading audit history...</p>
          ) : filteredAuditLogs.length === 0 ? (
            <div className="empty-state">
              <p>No audit trail records found.</p>
            </div>
          ) : (
            <div className="table-wrapper">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Timestamp</th>
                    <th>Entity Type</th>
                    <th>Target / Volunteer</th>
                    <th>Action</th>
                    <th>Transition</th>
                    <th>Reason</th>
                    <th>Acting Officer</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredAuditLogs.map((log) => (
                    <tr key={log.id}>
                      <td style={{ fontSize: "0.8rem", whiteSpace: "nowrap" }}>
                        {new Date(log.timestamp).toLocaleString()}
                      </td>
                      <td>
                        <span className="badge badge-muted" style={{ fontSize: "0.75rem" }}>
                          {log.entityType}
                        </span>
                      </td>
                      <td>
                        <strong>{log.entityName}</strong>
                      </td>
                      <td>
                        <span className="badge badge-primary" style={{ fontSize: "0.75rem" }}>
                          {log.action}
                        </span>
                      </td>
                      <td>
                        <span style={{ color: "#64748b" }}>{log.previousState}</span> &rarr;{" "}
                        <span style={{ fontWeight: 600, color: "#1e40af" }}>{log.newState}</span>
                      </td>
                      <td style={{ fontSize: "0.85rem", color: "#475569" }}>{log.reason}</td>
                      <td>{log.actorName}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* TAB 4: SYSTEM HEALTH & SECURITY CONTROLS */}
      {activeTab === "health" && (
        <div className="grid-2-col">
          <div className="section-card">
            <div className="section-header">
              <h2>Infrastructure &amp; Server Health</h2>
            </div>
            <dl className="detail-list">
              <div>
                <dt>Primary Database</dt>
                <dd>PostgreSQL 15 (HikariCP Connection Pool Active)</dd>
              </div>
              <div>
                <dt>Application Runtime</dt>
                <dd>Spring Boot 3.4.3 / Java 21 LTS</dd>
              </div>
              <div>
                <dt>Security Architecture</dt>
                <dd>Stateless JWT with IssuedAt Revocation &amp; BCrypt Hashing</dd>
              </div>
              <div>
                <dt>Password Policy</dt>
                <dd>Enforced: 8+ chars, upper, lower, numeric, special char</dd>
              </div>
              <div>
                <dt>Operational Unit Limit</dt>
                <dd>Max 100 volunteers / Unit (Ministry NSS Guidelines)</dd>
              </div>
              <div>
                <dt>Certificate Milestone</dt>
                <dd>120 Approved Service Hours accreditation threshold</dd>
              </div>
            </dl>
          </div>

          <div className="section-card">
            <div className="section-header">
              <h2>Role Hierarchy &amp; Access Controls</h2>
            </div>
            <p className="subtitle" style={{ marginBottom: "1rem" }}>
              Server-side security enforced via Spring Security method authorization rules.
            </p>

            <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
              <div style={{ padding: "0.75rem 1rem", backgroundColor: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: "6px" }}>
                <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "0.25rem" }}>
                  <strong>ADMIN</strong>
                  <span className="badge badge-primary">Full Institutional Scope</span>
                </div>
                <p style={{ fontSize: "0.82rem", color: "#64748b", margin: 0 }}>
                  System governance, unit creation, role delegation, cross-unit audit, and server configuration.
                </p>
              </div>

              <div style={{ padding: "0.75rem 1rem", backgroundColor: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: "6px" }}>
                <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "0.25rem" }}>
                  <strong>FACULTY_COORDINATOR</strong>
                  <span className="badge badge-muted">Cross-Unit Review</span>
                </div>
                <p style={{ fontSize: "0.82rem", color: "#64748b", margin: 0 }}>
                  Institutional oversight, event publishing approval, and cross-unit analytics review.
                </p>
              </div>

              <div style={{ padding: "0.75rem 1rem", backgroundColor: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: "6px" }}>
                <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "0.25rem" }}>
                  <strong>PROGRAMME_OFFICER</strong>
                  <span className="badge badge-muted">Unit Operational Scope</span>
                </div>
                <p style={{ fontSize: "0.82rem", color: "#64748b", margin: 0 }}>
                  Assigned unit volunteer approvals, attendance sessions, and service hour validation.
                </p>
              </div>

              <div style={{ padding: "0.75rem 1rem", backgroundColor: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: "6px" }}>
                <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "0.25rem" }}>
                  <strong>VOLUNTEER</strong>
                  <span className="badge badge-muted">Self-Service Scope</span>
                </div>
                <p style={{ fontSize: "0.82rem", color: "#64748b", margin: 0 }}>
                  Profile maintenance, programme enrollment, QR check-in, and service hour claim filing.
                </p>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Edit Role Modal */}
      {selectedUserForRole && (
        <div className="modal-backdrop">
          <div className="modal-card">
            <div className="modal-header">
              <h2>Change Role: {selectedUserForRole.name}</h2>
              <button
                type="button"
                onClick={() => setSelectedUserForRole(null)}
                className="btn-close"
                aria-label="Close modal"
              >
                &times;
              </button>
            </div>
            <form onSubmit={handleUpdateRole} className="form-stack">
              <p style={{ fontSize: "0.9rem", color: "#475569" }}>
                Select the administrative role to grant to <strong>{selectedUserForRole.email}</strong>:
              </p>
              <div className="form-group">
                <label>System Role *</label>
                <select value={newRole} onChange={(e) => setNewRole(e.target.value)} required>
                  <option value="VOLUNTEER">VOLUNTEER</option>
                  <option value="PROGRAMME_OFFICER">PROGRAMME_OFFICER</option>
                  <option value="FACULTY_COORDINATOR">FACULTY_COORDINATOR</option>
                  <option value="ADMIN">ADMIN</option>
                </select>
              </div>
              <div className="modal-actions">
                <button
                  type="button"
                  onClick={() => setSelectedUserForRole(null)}
                  className="btn-secondary"
                  disabled={updatingRole}
                >
                  Cancel
                </button>
                <button type="submit" className="btn-primary" disabled={updatingRole}>
                  {updatingRole ? "Updating..." : "Update Role"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

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

            <form onSubmit={handleCreateUnit} className="form-stack">
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
