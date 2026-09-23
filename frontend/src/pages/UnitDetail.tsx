import React, { useEffect, useState, useCallback } from "react";
import { useParams, Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { apiRequest, ApiError } from "../api/client";

interface UnitData {
  unitId: number;
  unitName: string;
  unitNumber: string;
  officerId: string | null;
  officerName: string | null;
  officerEmail: string | null;
  activeMemberCount: number;
  createdAt: string;
}

interface MemberItem {
  membershipId: number;
  volunteerId: number;
  volunteerName: string;
  collegeId: string;
  department: string;
  unitId: number;
  unitName: string;
  unitNumber: string;
  joinedAt: string;
  leftAt: string | null;
  isActive: boolean;
}

interface AvailableVolunteer {
  volunteerId: number;
  name: string;
  collegeId: string;
  department: string;
  activeUnitName: string | null;
}

export const UnitDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const { isCoordinatorOrOfficer } = useAuth();

  const [unit, setUnit] = useState<UnitData | null>(null);
  const [members, setMembers] = useState<MemberItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Add Member Modal
  const [showAddModal, setShowAddModal] = useState(false);
  const [availableVolunteers, setAvailableVolunteers] = useState<AvailableVolunteer[]>([]);
  const [selectedVolunteerId, setSelectedVolunteerId] = useState<string>("");
  const [adding, setAdding] = useState(false);
  const [modalError, setModalError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError(null);
    try {
      const [unitData, membersData] = await Promise.all([
        apiRequest<UnitData>(`/units/${id}`),
        apiRequest<MemberItem[]>(`/units/${id}/members`),
      ]);
      setUnit(unitData);
      setMembers(membersData);
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

  const handleAddMember = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedVolunteerId) return;
    setAdding(true);
    setModalError(null);

    try {
      await apiRequest<MemberItem>(`/units/${id}/members`, {
        method: "POST",
        body: JSON.stringify({
          volunteerId: Number(selectedVolunteerId),
        }),
      });

      setShowAddModal(false);
      loadData();
    } catch (err: unknown) {
      const apiErr = err as ApiError;
      setModalError(apiErr.message || "Failed to allocate volunteer to unit.");
    } finally {
      setAdding(false);
    }
  };

  const handleDeactivateMember = async (membershipId: number) => {
    if (!window.confirm("Remove this volunteer from the unit? (Historical record is preserved)")) {
      return;
    }
    try {
      await apiRequest(`/units/${id}/members/${membershipId}`, {
        method: "PATCH",
      });
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
          &larr; Back to Units
        </Link>
      </div>
    );
  }

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <Link to="/units" className="back-link">
            &larr; NSS Units
          </Link>
          <h1>
            {unit.unitName}{" "}
            <span className="badge badge-primary">{unit.unitNumber}</span>
          </h1>
          <p className="subtitle">
            Assigned Programme Officer:{" "}
            <strong>{unit.officerName || "Unassigned"}</strong> &bull; Active
            Roster: <strong>{members.length}</strong> volunteers
          </p>
        </div>

        {isCoordinatorOrOfficer && (
          <button onClick={openAddModal} className="btn-primary">
            + Assign Volunteer to Unit
          </button>
        )}
      </div>

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
    </div>
  );
};
