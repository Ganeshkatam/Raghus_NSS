import React, { useEffect, useState, useCallback } from "react";
import { useParams, Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { apiRequest, ApiError } from "../api/client";

interface UnitData {
  unitId: string;
  unitName: string;
  unitNumber: string;
  officerId: string | null;
  officerName: string | null;
  officerEmail: string | null;
  activeMemberCount: number;
  capacity?: number;
  createdAt: string;
}

interface UnitStats {
  unitId: string;
  unitName: string;
  unitNumber: string;
  activeMembers: number;
  totalEvents: number;
  totalServiceHours: number;
}

interface MemberItem {
  membershipId: string;
  volunteerId: string;
  volunteerName: string;
  collegeId: string;
  department: string;
  unitId: string;
  unitName: string;
  unitNumber: string;
  joinedAt: string;
  leftAt: string | null;
  isActive: boolean;
}

interface AvailableVolunteer {
  volunteerId: string;
  name: string;
  collegeId: string;
  department: string;
  activeUnitName: string | null;
}

export const UnitDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const { isCoordinatorOrOfficer } = useAuth();

  const [unit, setUnit] = useState<UnitData | null>(null);
  const [stats, setStats] = useState<UnitStats | null>(null);
  const [members, setMembers] = useState<MemberItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  // Add Member Modal
  const [showAddModal, setShowAddModal] = useState(false);
  const [availableVolunteers, setAvailableVolunteers] = useState<AvailableVolunteer[]>([]);
  const [selectedVolunteerId, setSelectedVolunteerId] = useState<string>("");
  const [adding, setAdding] = useState(false);
  const [modalError, setModalError] = useState<string | null>(null);

  // Transfer Member Modal
  const [showTransferModal, setShowTransferModal] = useState(false);
  const [transferVolunteerId, setTransferVolunteerId] = useState("");
  const [transferReason, setTransferReason] = useState("");
  const [transferring, setTransferring] = useState(false);
  const [transferError, setTransferError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError(null);
    try {
      const [unitData, membersData, statsData] = await Promise.all([
        apiRequest<UnitData>(`/units/${id}`),
        apiRequest<MemberItem[]>(`/units/${id}/members`),
        apiRequest<UnitStats>(`/units/${id}/stats`).catch(() => null),
      ]);
      setUnit(unitData);
      setMembers(membersData);
      setStats(statsData);
    } catch (err: unknown) {
      const apiErr = err as ApiError;
      setError(apiErr.message || "Failed to load unit details.");
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const openAddModal = async () => {
    setShowAddModal(true);
    setModalError(null);
    try {
      const res = await apiRequest<{ content: AvailableVolunteer[] }>("/volunteers?size=100");
      setAvailableVolunteers(res.content || []);
      if (res.content && res.content.length > 0) {
        setSelectedVolunteerId(res.content[0].volunteerId.toString());
      }
    } catch {
      // Fallback
    }
  };

  const openTransferModal = async () => {
    setShowTransferModal(true);
    setTransferError(null);
    try {
      const res = await apiRequest<{ content: AvailableVolunteer[] }>("/volunteers?size=100");
      setAvailableVolunteers(res.content || []);
      if (res.content && res.content.length > 0) {
        setTransferVolunteerId(res.content[0].volunteerId.toString());
      }
    } catch {
      // Fallback
    }
  };

  const handleAddMember = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedVolunteerId) return;
    setAdding(true);
    setModalError(null);

    try {
      await apiRequest<MemberItem>(`/units/${id}/members`, {
        method: "POST",
        body: JSON.stringify({
          volunteerId: selectedVolunteerId,
        }),
      });

      setShowAddModal(false);
      setSuccess("Volunteer assigned to unit.");
      loadData();
    } catch (err: unknown) {
      const apiErr = err as ApiError;
      setModalError(apiErr.message || "Failed to allocate volunteer to unit.");
    } finally {
      setAdding(false);
    }
  };

  const handleTransferVolunteer = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!id || !transferVolunteerId) return;
    setTransferring(true);
    setTransferError(null);

    try {
      await apiRequest(`/units/${id}/transfer`, {
        method: "POST",
        body: JSON.stringify({
          volunteerId: transferVolunteerId,
          reason: transferReason.trim() || undefined,
        }),
      });

      setShowTransferModal(false);
      setTransferReason("");
      setSuccess("Volunteer successfully transferred into this unit.");
      loadData();
    } catch (err: unknown) {
      const apiErr = err as ApiError;
      setTransferError(apiErr.message || "Failed to transfer volunteer.");
    } finally {
      setTransferring(false);
    }
  };

  const handleDeactivateMember = async (membershipId: string) => {
    if (!window.confirm("Remove this volunteer from the unit? (Historical record is preserved)")) {
      return;
    }
    try {
      await apiRequest(`/units/${id}/members/${membershipId}`, {
        method: "PATCH",
      });
      setSuccess("Volunteer removed from active unit roster.");
      loadData();
    } catch (err: unknown) {
      const apiErr = err as ApiError;
      alert(apiErr.message || "Failed to update membership.");
    }
  };

  if (loading) {
    return (
      <div className="page-container">
        <p className="loading-state">Loading NSS Unit details...</p>
      </div>
    );
  }

  if (error || !unit) {
    return (
      <div className="page-container">
        <div className="alert alert-error">
          <strong>Error:</strong> {error || "Unit not found."}
        </div>
        <Link to="/units" className="btn-secondary">
          Back to Units
        </Link>
      </div>
    );
  }

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <Link to="/units" className="back-link">
            NSS Units
          </Link>
          <h1>
            {unit.unitName}{" "}
            <span className="badge badge-primary">{unit.unitNumber}</span>
          </h1>
          <p className="subtitle">
            Assigned Programme Officer:{" "}
            <strong>{unit.officerName || "Unassigned"}</strong> &bull; Active
            Roster: <strong>{members.length}</strong> / {unit.capacity || 100} capacity
          </p>
        </div>

        {isCoordinatorOrOfficer && (
          <div style={{ display: "flex", gap: "0.5rem" }}>
            <button onClick={openTransferModal} className="btn-secondary">
              Transfer Volunteer In
            </button>
            <button onClick={openAddModal} className="btn-primary">
              + Assign Volunteer
            </button>
          </div>
        )}
      </div>

      {error && (
        <div className="alert alert-error">
          <strong>Notice:</strong> {error}
        </div>
      )}
      {success && (
        <div className="alert alert-success">
          <strong>Success:</strong> {success}
        </div>
      )}

      {/* Unit KPI Statistics */}
      {stats && (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: "1rem", marginBottom: "1.5rem" }}>
          <div className="section-card" style={{ padding: "1.25rem" }}>
            <div className="cell-sub" style={{ textTransform: "uppercase", fontSize: "0.75rem", letterSpacing: "0.05em" }}>Enrolled Volunteers</div>
            <div style={{ fontSize: "1.75rem", fontWeight: 700, color: "#1e3a8a", marginTop: "0.25rem" }}>
              {stats.activeMembers} <span style={{ fontSize: "0.9rem", color: "#64748b", fontWeight: 400 }}>/ {unit.capacity || 100}</span>
            </div>
          </div>
          <div className="section-card" style={{ padding: "1.25rem" }}>
            <div className="cell-sub" style={{ textTransform: "uppercase", fontSize: "0.75rem", letterSpacing: "0.05em" }}>Organised Events</div>
            <div style={{ fontSize: "1.75rem", fontWeight: 700, color: "#166534", marginTop: "0.25rem" }}>
              {stats.totalEvents}
            </div>
          </div>
          <div className="section-card" style={{ padding: "1.25rem" }}>
            <div className="cell-sub" style={{ textTransform: "uppercase", fontSize: "0.75rem", letterSpacing: "0.05em" }}>Accredited Service Hours</div>
            <div style={{ fontSize: "1.75rem", fontWeight: 700, color: "#c2410c", marginTop: "0.25rem" }}>
              {stats.totalServiceHours} <span style={{ fontSize: "0.9rem", color: "#64748b", fontWeight: 400 }}>hrs</span>
            </div>
          </div>
        </div>
      )}

      <div className="section-card">
        <div className="section-header">
          <h3>Unit Member Roster</h3>
          <span className="results-count">{members.length} Active Volunteers</span>
        </div>

        {members.length === 0 ? (
          <div className="empty-state">
            <p>No volunteers are currently assigned to this unit.</p>
            {isCoordinatorOrOfficer && (
              <button onClick={openAddModal} className="btn-primary-sm">
                Assign First Volunteer
              </button>
            )}
          </div>
        ) : (
          <div className="units-table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th>College ID</th>
                  <th>Volunteer Name</th>
                  <th>Department</th>
                  <th>Joined Unit On</th>
                  <th>Status</th>
                  {isCoordinatorOrOfficer && <th>Actions</th>}
                </tr>
              </thead>
              <tbody>
                {members.map((m) => (
                  <tr key={m.membershipId}>
                    <td>
                      <strong>{m.collegeId}</strong>
                    </td>
                    <td>
                      <Link
                        to={`/volunteers/${m.volunteerId}`}
                        className="table-action-link"
                      >
                        {m.volunteerName}
                      </Link>
                    </td>
                    <td>{m.department}</td>
                    <td>{new Date(m.joinedAt).toLocaleDateString()}</td>
                    <td>
                      <span className="badge badge-success">ACTIVE</span>
                    </td>
                    {isCoordinatorOrOfficer && (
                      <td>
                        <button
                          onClick={() => handleDeactivateMember(m.membershipId)}
                          className="btn-danger-sm"
                        >
                          Remove from Unit
                        </button>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {showAddModal && (
        <div className="modal-backdrop">
          <div className="modal-card">
            <div className="modal-header">
              <h3>Assign Volunteer to {unit.unitName}</h3>
              <button
                onClick={() => setShowAddModal(false)}
                className="btn-close"
              >
                &times;
              </button>
            </div>

            {modalError && (
              <div className="alert alert-error">
                <strong>Error:</strong> {modalError}
              </div>
            )}

            <form onSubmit={handleAddMember} className="form-stack">
              <div className="form-group">
                <label htmlFor="volunteerSelect">Select Registered Volunteer *</label>
                <select
                  id="volunteerSelect"
                  value={selectedVolunteerId}
                  onChange={(e) => setSelectedVolunteerId(e.target.value)}
                  required
                >
                  <option value="">-- Choose a volunteer --</option>
                  {availableVolunteers.map((v) => (
                    <option key={v.volunteerId} value={v.volunteerId}>
                      {v.name} ({v.collegeId}) - {v.department}{" "}
                      {v.activeUnitName ? `[Currently in: ${v.activeUnitName}]` : "[Unassigned]"}
                    </option>
                  ))}
                </select>
                <small className="form-hint">
                  Note: If the volunteer belongs to another unit, their previous
                  membership will be archived to preserve history.
                </small>
              </div>

              <div className="modal-actions">
                <button
                  type="button"
                  onClick={() => setShowAddModal(false)}
                  className="btn-secondary"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn-primary"
                  disabled={adding || !selectedVolunteerId}
                >
                  {adding ? "Assigning..." : "Assign to Unit"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {showTransferModal && (
        <div className="modal-backdrop">
          <div className="modal-card">
            <div className="modal-header">
              <h3>Transfer Volunteer to {unit.unitName}</h3>
              <button
                onClick={() => setShowTransferModal(false)}
                className="btn-close"
              >
                &times;
              </button>
            </div>

            {transferError && (
              <div className="alert alert-error">
                <strong>Error:</strong> {transferError}
              </div>
            )}

            <form onSubmit={handleTransferVolunteer} className="form-stack">
              <div className="form-group">
                <label htmlFor="transferVolunteerSelect">Select Volunteer to Transfer *</label>
                <select
                  id="transferVolunteerSelect"
                  value={transferVolunteerId}
                  onChange={(e) => setTransferVolunteerId(e.target.value)}
                  required
                >
                  <option value="">-- Choose a volunteer --</option>
                  {availableVolunteers.map((v) => (
                    <option key={v.volunteerId} value={v.volunteerId}>
                      {v.name} ({v.collegeId}) - {v.department}{" "}
                      {v.activeUnitName ? `[From: ${v.activeUnitName}]` : "[Unassigned]"}
                    </option>
                  ))}
                </select>
              </div>

              <div className="form-group">
                <label htmlFor="transferReasonInput">Transfer Reason / Authorization</label>
                <textarea
                  id="transferReasonInput"
                  rows={3}
                  placeholder="e.g., Departmental re-alignment or schedule conflict accommodation."
                  value={transferReason}
                  onChange={(e) => setTransferReason(e.target.value)}
                />
              </div>

              <div className="modal-actions">
                <button
                  type="button"
                  onClick={() => setShowTransferModal(false)}
                  className="btn-secondary"
                  disabled={transferring}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn-primary"
                  disabled={transferring || !transferVolunteerId}
                >
                  {transferring ? "Processing Transfer..." : "Commit Transfer"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
