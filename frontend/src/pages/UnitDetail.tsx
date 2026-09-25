import React, { useEffect, useState, useCallback } from "react";
import { useParams, Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { apiRequest, ApiError } from "../api/client";
import { CustomSelect } from "../components/CustomSelect";


interface UnitData {
  unitId: string;
  unitName: string;
  unitNumber: string;
  officerId: string | null;
  officerName: string | null;
  officerEmail: string | null;
  activeMemberCount: number;
  capacity?: number | null;
  createdAt: string;
}

interface UnitStats {
  unitId: string;
  unitName: string;
  unitNumber: string;
  capacity?: number | null;
  activeVolunteers: number;
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
  activeUnitId?: string | null;
  activeUnitName: string | null;
}

interface UnitSummary {
  unitId: string;
  unitName: string;
  unitNumber: string;
}

interface OfficerCandidate {
  userId: string;
  name: string;
  email: string;
  phone?: string;
  roles: string[];
}

export const UnitDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const { isCoordinatorOrOfficer, isAdmin, hasCapability } = useAuth();
  const canManageUnits = isAdmin || isCoordinatorOrOfficer || hasCapability("UNITS_MANAGE");

  const [unit, setUnit] = useState<UnitData | null>(null);
  const [stats, setStats] = useState<UnitStats | null>(null);
  const [members, setMembers] = useState<MemberItem[]>([]);
  const [allUnits, setAllUnits] = useState<UnitSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  // Programme Officer Modal
  const [showOfficerModal, setShowOfficerModal] = useState(false);
  const [officerCandidates, setOfficerCandidates] = useState<OfficerCandidate[]>([]);
  const [selectedOfficerId, setSelectedOfficerId] = useState<string>("");
  const [unitCapacity, setUnitCapacity] = useState<string>("");
  const [loadingCandidates, setLoadingCandidates] = useState(false);
  const [assigningOfficer, setAssigningOfficer] = useState(false);
  const [officerModalError, setOfficerModalError] = useState<string | null>(null);

  // Add (Allot) Member Modal
  const [showAddModal, setShowAddModal] = useState(false);
  const [availableVolunteers, setAvailableVolunteers] = useState<AvailableVolunteer[]>([]);
  const [selectedVolunteerId, setSelectedVolunteerId] = useState<string>("");
  const [adding, setAdding] = useState(false);
  const [modalError, setModalError] = useState<string | null>(null);

  // Transfer Member Modal
  const [showTransferModal, setShowTransferModal] = useState(false);
  const [transferMode, setTransferMode] = useState<"in" | "out">("in");
  const [transferringMember, setTransferringMember] = useState<MemberItem | null>(null);
  const [transferVolunteerId, setTransferVolunteerId] = useState("");
  const [transferTargetUnitId, setTransferTargetUnitId] = useState("");
  const [transferReason, setTransferReason] = useState("");
  const [transferring, setTransferring] = useState(false);
  const [transferError, setTransferError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError(null);
    try {
      const [unitData, membersData, statsData, unitsList] = await Promise.all([
        apiRequest<UnitData>(`/units/${id}`),
        apiRequest<MemberItem[]>(`/units/${id}/members`),
        apiRequest<UnitStats>(`/units/${id}/stats`).catch(() => null),
        apiRequest<UnitSummary[]>("/units").catch(() => []),
      ]);
      setUnit(unitData);
      setMembers(membersData);
      setStats(statsData);
      setAllUnits(unitsList || []);
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

  const openOfficerModal = async () => {
    setShowOfficerModal(true);
    setOfficerModalError(null);
    setSelectedOfficerId(unit?.officerId || "");
    setUnitCapacity(unit?.capacity ? String(unit.capacity) : "");
    setLoadingCandidates(true);
    try {
      const candidates = await apiRequest<OfficerCandidate[]>("/units/officer-candidates");
      setOfficerCandidates(candidates || []);
    } catch (err: unknown) {
      const apiErr = err as ApiError;
      setOfficerModalError(apiErr.message || "Failed to load officer candidates.");
    } finally {
      setLoadingCandidates(false);
    }
  };

  const handleAssignOfficer = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!id) return;
    setAssigningOfficer(true);
    setOfficerModalError(null);

    const parsedCapacity = unitCapacity.trim() ? parseInt(unitCapacity.trim(), 10) : null;
    if (unitCapacity.trim() && (isNaN(parsedCapacity!) || parsedCapacity! <= 0)) {
      setOfficerModalError("Unit capacity must be a positive number.");
      setAssigningOfficer(false);
      return;
    }

    try {
      await apiRequest<UnitData>(`/units/${id}`, {
        method: "PATCH",
        body: JSON.stringify({
          officerId: selectedOfficerId ? selectedOfficerId : null,
          clearOfficer: !selectedOfficerId,
          capacity: parsedCapacity,
        }),
      });

      setShowOfficerModal(false);
      setSuccess("Unit settings and officer assignment updated successfully.");
      loadData();
    } catch (err: unknown) {
      const apiErr = err as ApiError;
      setOfficerModalError(apiErr.message || "Failed to update unit settings.");
    } finally {
      setAssigningOfficer(false);
    }
  };

  const openAddModal = async () => {
    setShowAddModal(true);
    setModalError(null);
    try {
      const res = await apiRequest<{ content: AvailableVolunteer[] }>("/volunteers?size=100");
      const list = res.content || [];
      setAvailableVolunteers(list);
      // Prefer unallotted volunteers by default
      const unassigned = list.find((v) => !v.activeUnitName);
      if (unassigned) {
        setSelectedVolunteerId(unassigned.volunteerId);
      } else if (list.length > 0) {
        setSelectedVolunteerId(list[0].volunteerId);
      }
    } catch {
      // Fallback
    }
  };

  const openTransferModal = async () => {
    setTransferMode("in");
    setTransferringMember(null);
    setShowTransferModal(true);
    setTransferError(null);
    setTransferReason("");
    try {
      const res = await apiRequest<{ content: AvailableVolunteer[] }>("/volunteers?size=100");
      const list = res.content || [];
      setAvailableVolunteers(list);
      const eligibleTransfers = list.filter((v) => v.activeUnitName && v.activeUnitId !== id);
      if (eligibleTransfers.length > 0) {
        setTransferVolunteerId(eligibleTransfers[0].volunteerId);
      } else {
        setTransferVolunteerId("");
      }
    } catch {
      // Fallback
    }
  };

  const openTransferOutModal = (m: MemberItem) => {
    setTransferMode("out");
    setTransferringMember(m);
    setTransferVolunteerId(m.volunteerId);
    setTransferReason("");
    setTransferError(null);
    const otherUnits = allUnits.filter((u) => u.unitId !== id);
    if (otherUnits.length > 0) {
      setTransferTargetUnitId(otherUnits[0].unitId);
    } else {
      setTransferTargetUnitId("");
    }
    setShowTransferModal(true);
  };

  const switchToTransferFromAllot = (vol: AvailableVolunteer) => {
    setShowAddModal(false);
    setTransferMode("in");
    setTransferringMember(null);
    setTransferVolunteerId(vol.volunteerId);
    setTransferReason("");
    setTransferError(null);
    setShowTransferModal(true);
  };

  const handleAddMember = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedVolunteerId) return;

    const chosenVol = availableVolunteers.find((v) => v.volunteerId === selectedVolunteerId);
    if (chosenVol?.activeUnitName) {
      setModalError(`Cannot allot ${chosenVol.name}: Volunteer is already active in ${chosenVol.activeUnitName}. Please use the Transfer Volunteer action.`);
      return;
    }

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
      setSuccess("Volunteer allotted to unit successfully.");
      loadData();
    } catch (err: unknown) {
      const apiErr = err as ApiError;
      setModalError(apiErr.message || "Failed to allot volunteer to unit.");
    } finally {
      setAdding(false);
    }
  };

  const handleTransferVolunteer = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!id) return;
    setTransferring(true);
    setTransferError(null);

    try {
      if (transferMode === "out") {
        if (!transferringMember || !transferTargetUnitId) {
          setTransferError("Please select a destination NSS unit.");
          setTransferring(false);
          return;
        }
        await apiRequest(`/units/${id}/transfer`, {
          method: "POST",
          body: JSON.stringify({
            volunteerId: transferringMember.volunteerId,
            targetUnitId: transferTargetUnitId,
            reason: transferReason.trim() || undefined,
          }),
        });
        setSuccess(`Volunteer ${transferringMember.volunteerName} transferred out successfully.`);
      } else {
        // Transfer IN
        const targetVol = availableVolunteers.find((v) => v.volunteerId === transferVolunteerId);
        if (!targetVol) {
          setTransferError("Please choose a volunteer to transfer into this unit.");
          setTransferring(false);
          return;
        }
        const sourceUnitId = targetVol.activeUnitId || id;
        await apiRequest(`/units/${sourceUnitId}/transfer`, {
          method: "POST",
          body: JSON.stringify({
            volunteerId: targetVol.volunteerId,
            targetUnitId: id,
            reason: transferReason.trim() || undefined,
          }),
        });
        setSuccess(`Volunteer ${targetVol.name} transferred into ${unit?.unitName || "this unit"} successfully.`);
      }

      setShowTransferModal(false);
      setTransferReason("");
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
          <p className="subtitle" style={{ display: "flex", alignItems: "center", flexWrap: "wrap", gap: "0.5rem" }}>
            <span>
              Assigned Programme Officer:{" "}
              <strong>{unit.officerName || "Unassigned"}</strong>
            </span>
            {canManageUnits && (
              <button
                type="button"
                onClick={openOfficerModal}
                style={{
                  padding: "0.2rem 0.55rem",
                  fontSize: "0.75rem",
                  fontWeight: 600,
                  cursor: "pointer",
                  borderRadius: "4px",
                  border: "1px solid #cbd5e1",
                  backgroundColor: "#ffffff",
                  color: "#2563eb",
                }}
              >
                {unit.officerName ? "Change Officer" : "Assign Officer"}
              </button>
            )}
            <span>
              &bull; Active Roster: <strong>{members.length}</strong> {unit?.capacity ? `/ ${unit.capacity} capacity` : ""}
            </span>
          </p>
        </div>

        {canManageUnits && (
          <div style={{ display: "flex", gap: "0.5rem" }}>
            <button onClick={openOfficerModal} className="btn-secondary">
              {unit.officerName ? "Configure Unit" : "Assign Officer"}
            </button>
            <button onClick={openTransferModal} className="btn-secondary">
              Transfer Volunteer In
            </button>
            <button onClick={openAddModal} className="btn-primary">
              + Allot Volunteer
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
              {stats.activeVolunteers} {(stats.capacity ?? unit?.capacity) ? <span style={{ fontSize: "0.9rem", color: "#64748b", fontWeight: 400 }}>/ {stats.capacity ?? unit?.capacity}</span> : null}
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
                Allot First Volunteer
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
                        <div style={{ display: "flex", gap: "0.4rem" }}>
                          <button
                            type="button"
                            onClick={() => openTransferOutModal(m)}
                            className="btn-secondary-sm"
                            style={{ fontSize: "0.75rem" }}
                          >
                            Transfer
                          </button>
                          <button
                            type="button"
                            onClick={() => handleDeactivateMember(m.membershipId)}
                            className="btn-danger-sm"
                            style={{ fontSize: "0.75rem" }}
                          >
                            Remove
                          </button>
                        </div>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* ALLOT VOLUNTEER MODAL */}
      {showAddModal && (
        <div className="modal-backdrop">
          <div className="modal-card">
            <div className="modal-header">
              <h3>Allot Volunteer to {unit.unitName}</h3>
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
                <label htmlFor="volunteerSelect">Select Registered Volunteer to Allot *</label>
                <CustomSelect
                  id="volunteerSelect"
                  value={selectedVolunteerId}
                  onChange={setSelectedVolunteerId}
                  options={[
                    { value: "", label: "-- Choose a volunteer --" },
                    ...availableVolunteers.map((v) => ({
                      value: v.volunteerId,
                      label: `${v.name} (${v.collegeId}) - ${v.department} ${
                        v.activeUnitName ? `[Already in: ${v.activeUnitName}]` : "[Unallotted]"
                      }`,
                    })),
                  ]}
                  placeholder="-- Choose a volunteer --"
                />

                {(() => {
                  const selVol = availableVolunteers.find((v) => v.volunteerId === selectedVolunteerId);
                  if (!selVol) return null;
                  if (selVol.activeUnitName) {
                    return (
                      <div
                        style={{
                          marginTop: "0.75rem",
                          padding: "0.85rem 1rem",
                          backgroundColor: "#fef3c7",
                          border: "1px solid #f59e0b",
                          borderRadius: "6px",
                        }}
                      >
                        <div style={{ display: "flex", alignItems: "center", gap: "0.5rem", marginBottom: "0.25rem" }}>
                          <span style={{ fontWeight: 700, color: "#92400e", fontSize: "0.9rem" }}>
                            Volunteer Already Allotted
                          </span>
                          <span className="badge badge-warning" style={{ fontSize: "0.75rem" }}>
                            {selVol.activeUnitName}
                          </span>
                        </div>
                        <p style={{ margin: "0 0 0.5rem 0", fontSize: "0.82rem", color: "#78350f" }}>
                          <strong>{selVol.name}</strong> ({selVol.collegeId}) is currently active in <strong>{selVol.activeUnitName}</strong>.
                          Each volunteer can belong to at most one NSS Unit. Direct allotment is blocked to prevent silent reassignment.
                        </p>
                        <button
                          type="button"
                          className="btn-primary-sm"
                          style={{ fontSize: "0.8rem", padding: "0.3rem 0.75rem" }}
                          onClick={() => switchToTransferFromAllot(selVol)}
                        >
                          Transfer {selVol.name} to {unit.unitName} &rarr;
                        </button>
                      </div>
                    );
                  }
                  return (
                    <small className="form-hint" style={{ color: "#16a34a" }}>
                      Volunteer is currently unallotted and eligible for primary unit allotment.
                    </small>
                  );
                })()}
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
                  disabled={
                    adding ||
                    !selectedVolunteerId ||
                    Boolean(availableVolunteers.find((v) => v.volunteerId === selectedVolunteerId)?.activeUnitName)
                  }
                >
                  {adding ? "Allotting..." : "Allot to Unit"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* EXPLICIT TRANSFER MODAL */}
      {showTransferModal && (
        <div className="modal-backdrop">
          <div className="modal-card">
            <div className="modal-header">
              <h3>
                {transferMode === "out"
                  ? `Transfer Volunteer Out of ${unit.unitName}`
                  : `Transfer Volunteer into ${unit.unitName}`}
              </h3>
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
              {transferMode === "out" && transferringMember ? (
                <>
                  <div className="form-group">
                    <label>Volunteer to Transfer</label>
                    <div
                      style={{
                        padding: "0.6rem 0.85rem",
                        backgroundColor: "#f8fafc",
                        border: "1px solid #cbd5e1",
                        borderRadius: "6px",
                        fontSize: "0.9rem",
                      }}
                    >
                      <strong>{transferringMember.volunteerName}</strong> ({transferringMember.collegeId}) &bull; {transferringMember.department}
                    </div>
                  </div>

                  <div className="form-group">
                    <label>Current Origin Unit</label>
                    <div style={{ fontSize: "0.9rem", color: "#475569" }}>
                      <strong>{unit.unitName}</strong> ({unit.unitNumber})
                    </div>
                  </div>

                  <div className="form-group">
                    <label htmlFor="targetUnitSelect">Destination NSS Unit *</label>
                    <CustomSelect
                      id="targetUnitSelect"
                      value={transferTargetUnitId}
                      onChange={setTransferTargetUnitId}
                      options={[
                        { value: "", label: "-- Select destination unit --" },
                        ...allUnits
                          .filter((u) => u.unitId !== id)
                          .map((u) => ({
                            value: u.unitId,
                            label: `${u.unitName} (${u.unitNumber})`,
                          })),
                      ]}
                      placeholder="-- Select destination unit --"
                    />
                  </div>
                </>
              ) : (
                <>
                  <div className="form-group">
                    <label htmlFor="transferVolunteerSelect">Select Enrolled Volunteer to Transfer In *</label>
                    <CustomSelect
                      id="transferVolunteerSelect"
                      value={transferVolunteerId}
                      onChange={setTransferVolunteerId}
                      options={[
                        { value: "", label: "-- Choose an actively allotted volunteer --" },
                        ...availableVolunteers
                          .filter((v) => v.activeUnitName && v.activeUnitId !== id)
                          .map((v) => ({
                            value: v.volunteerId,
                            label: `${v.name} (${v.collegeId}) - [Current: ${v.activeUnitName}]`,
                          })),
                      ]}
                      placeholder="-- Choose an actively allotted volunteer --"
                    />
                    <small className="form-hint">
                      Lists volunteers currently active in other units. Destination unit is <strong>{unit.unitName}</strong>.
                    </small>
                  </div>
                </>
              )}

              <div className="form-group">
                <label htmlFor="transferReasonInput">Transfer Reason / Authorization Note *</label>
                <textarea
                  id="transferReasonInput"
                  rows={3}
                  required
                  placeholder="e.g. Academic schedule realignment or approved unit transfer request."
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
                  disabled={
                    transferring ||
                    (transferMode === "out" ? !transferTargetUnitId : !transferVolunteerId)
                  }
                >
                  {transferring ? "Processing Transfer..." : "Confirm & Execute Transfer"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {showOfficerModal && (
        <div className="modal-backdrop">
          <div className="modal-card">
            <div className="modal-header">
              <h3>{unit.officerName ? "Configure Unit & Officer" : "Assign Programme Officer"}</h3>
              <button onClick={() => setShowOfficerModal(false)} className="btn-close">
                &times;
              </button>
            </div>

            {officerModalError && (
              <div className="alert alert-error">
                <strong>Error:</strong> {officerModalError}
              </div>
            )}

            <form onSubmit={handleAssignOfficer} className="form-stack">
              <p style={{ fontSize: "0.875rem", color: "#64748b" }}>
                Configure Programme Officer and volunteer capacity limit for <strong>{unit.unitName} ({unit.unitNumber})</strong>.
              </p>

              <div className="form-group">
                <label htmlFor="officerSelect">Programme Officer</label>
                {loadingCandidates ? (
                  <p className="cell-sub">Loading available faculty officers...</p>
                ) : (
                  <CustomSelect
                    id="officerSelect"
                    value={selectedOfficerId}
                    onChange={setSelectedOfficerId}
                    options={[
                      { value: "", label: "-- No Officer Assigned (Unassigned) --" },
                      ...officerCandidates.map((c) => ({
                        value: c.userId,
                        label: `${c.name} (${c.email})${c.roles?.length ? ` [${c.roles.join(", ")}]` : ""}`,
                      })),
                    ]}
                    placeholder="-- No Officer Assigned (Unassigned) --"
                  />
                )}
              </div>

              <div className="form-group">
                <label htmlFor="unitCapacity">Unit Max Capacity</label>
                <input
                  id="unitCapacity"
                  type="number"
                  min="1"
                  value={unitCapacity}
                  onChange={(e) => setUnitCapacity(e.target.value)}
                  placeholder="e.g. 50, 100, 150 (Leave blank for no limit)"
                />
              </div>

              <div className="modal-actions">
                <button
                  type="button"
                  onClick={() => setShowOfficerModal(false)}
                  className="btn-secondary"
                  disabled={assigningOfficer}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn-primary"
                  disabled={assigningOfficer}
                >
                  {assigningOfficer ? "Saving Settings..." : "Save Configuration"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
