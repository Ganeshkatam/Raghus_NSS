import React, { useEffect, useState, useCallback } from "react";
import { useParams, Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { apiRequest, ApiError } from "../api/client";
import { CustomSelect } from "../components/CustomSelect";


interface VolunteerData {
  volunteerId: string;
  userId: string;
  name: string;
  email: string;
  phone: string;
  collegeId: string;
  department: string;
  yearOfStudy: number;
  joinDate: string;
  status: string;
  activeUnitId: string | null;
  activeUnitName: string | null;
  createdAt: string;
}

interface MembershipHistoryItem {
  membershipId: string;
  unitId: string;
  unitName: string;
  unitNumber: string;
  joinedAt: string;
  leftAt: string | null;
  isActive: boolean;
}

interface StatusHistoryItem {
  historyId: string;
  previousStatus: string;
  newStatus: string;
  remarks: string | null;
  changedByName: string;
  createdAt: string;
}

interface TransferHistoryItem {
  transferId: string;
  fromUnitName: string | null;
  toUnitName: string;
  reason: string | null;
  transferredByName: string;
  transferredAt: string;
}

export const VolunteerDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const { user, isCoordinatorOrOfficer, hasCapability } = useAuth();
  const isManager = isCoordinatorOrOfficer || hasCapability("VOLUNTEERS_MANAGE") || Boolean(user?.roles?.some((r) =>
    ["ADMIN", "FACULTY_COORDINATOR", "PROGRAMME_OFFICER"].includes(r)
  ));

  const [volunteer, setVolunteer] = useState<VolunteerData | null>(null);
  const [history, setHistory] = useState<MembershipHistoryItem[]>([]);
  const [statusHistory, setStatusHistory] = useState<StatusHistoryItem[]>([]);
  const [transferHistory, setTransferHistory] = useState<TransferHistoryItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  // Status Change Modal State
  const [showStatusModal, setShowStatusModal] = useState(false);
  const [selectedNewStatus, setSelectedNewStatus] = useState("ACTIVE");
  const [statusRemarks, setStatusRemarks] = useState("");
  const [updatingStatus, setUpdatingStatus] = useState(false);

  const loadData = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError(null);
    try {
      const [volData, memData] = await Promise.all([
        apiRequest<VolunteerData>(`/volunteers/${id}`),
        apiRequest<MembershipHistoryItem[]>(`/volunteers/${id}/memberships`).catch(() => []),
      ]);
      setVolunteer(volData);
      setSelectedNewStatus(volData.status || "ACTIVE");
      setHistory(memData || []);

      if (isManager) {
        const [statHist, transHist] = await Promise.all([
          apiRequest<StatusHistoryItem[]>(`/volunteers/${id}/history`).catch(() => []),
          apiRequest<TransferHistoryItem[]>(`/units/volunteers/${id}/transfers`).catch(() => []),
        ]);
        setStatusHistory(statHist || []);
        setTransferHistory(transHist || []);
      }
    } catch (err: unknown) {
      const apiErr = err as ApiError;
      setError(apiErr.message || "Failed to load volunteer profile.");
    } finally {
      setLoading(false);
    }
  }, [id, isManager]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleUpdateStatus = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!id) return;
    setUpdatingStatus(true);
    setError(null);
    try {
      await apiRequest(`/volunteers/${id}/status`, {
        method: "POST",
        body: JSON.stringify({
          newStatus: selectedNewStatus,
          remarks: statusRemarks.trim() || undefined,
        }),
      });
      setSuccess(`Volunteer status successfully updated to ${selectedNewStatus}.`);
      setShowStatusModal(false);
      setStatusRemarks("");
      loadData();
    } catch (err: any) {
      setError(err.message || "Failed to update volunteer status.");
    } finally {
      setUpdatingStatus(false);
    }
  };

  if (loading) {
    return (
      <div className="page-container">
        <p className="loading-state">Loading volunteer profile...</p>
      </div>
    );
  }

  if (error && !volunteer) {
    return (
      <div className="page-container">
        <div className="alert alert-error">
          <strong>Error:</strong> {error || "Volunteer not found."}
        </div>
        <Link to="/volunteers" className="btn-secondary">
          Back to Volunteers
        </Link>
      </div>
    );
  }

  if (!volunteer) return null;

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <Link to="/volunteers" className="back-link">
            Volunteers Registry
          </Link>
          <h1>{volunteer.name}</h1>
          <p className="subtitle">
            College ID: <strong>{volunteer.collegeId}</strong> &bull; Status:{" "}
            <span
              className={`badge ${
                volunteer.status === "ACTIVE"
                  ? "badge-success"
                  : volunteer.status === "PENDING_APPROVAL"
                  ? "badge-warning"
                  : "badge-muted"
              }`}
            >
              {volunteer.status}
            </span>
          </p>
        </div>

        {isManager && (
          <button
            type="button"
            onClick={() => setShowStatusModal(true)}
            className="btn-primary"
          >
            Update Volunteer Status
          </button>
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

      <div className="grid-2-col">
        <div className="section-card">
          <div className="section-header">
            <h3>Academic &amp; Contact Information</h3>
          </div>
          <div className="details-list">
            <div className="details-row">
              <span className="details-label">Department</span>
              <span className="details-value">{volunteer.department}</span>
            </div>
            <div className="details-row">
              <span className="details-label">Year of Study</span>
              <span className="details-value">Year {volunteer.yearOfStudy}</span>
            </div>
            <div className="details-row">
              <span className="details-label">Email Address</span>
              <span className="details-value">{volunteer.email}</span>
            </div>
            <div className="details-row">
              <span className="details-label">Phone</span>
              <span className="details-value">{volunteer.phone || "Not recorded"}</span>
            </div>
            <div className="details-row">
              <span className="details-label">Enrolled Date</span>
              <span className="details-value">{volunteer.joinDate}</span>
            </div>
          </div>
        </div>

        <div className="section-card">
          <div className="section-header">
            <h3>Assigned NSS Unit</h3>
          </div>
          {volunteer.activeUnitId ? (
            <div className="unit-badge-card">
              <div className="unit-badge-title">{volunteer.activeUnitName}</div>
              <p className="unit-badge-sub">
                Volunteer is an active participant in this operational unit.
              </p>
              <Link to={`/units/${volunteer.activeUnitId}`} className="btn-secondary-sm">
                View Unit Details &amp; Operations
              </Link>
            </div>
          ) : (
            <div className="empty-state">
              <p>No active unit membership.</p>
              <Link to="/units" className="btn-secondary-sm">
                Assign Unit from Units Roster
              </Link>
            </div>
          )}
        </div>
      </div>

      {/* Status History Audit Trail */}
      {isManager && (
        <div className="section-card" style={{ marginTop: "1.5rem" }}>
          <div className="section-header">
            <h3>Status Transition &amp; Onboarding Audit Trail</h3>
          </div>
          {statusHistory.length === 0 ? (
            <p className="empty-state">No status transition history recorded for this volunteer.</p>
          ) : (
            <div className="units-table-wrapper">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Date &amp; Time</th>
                    <th>Previous Status</th>
                    <th>New Status</th>
                    <th>Remarks</th>
                    <th>Changed By</th>
                  </tr>
                </thead>
                <tbody>
                  {statusHistory.map((s) => (
                    <tr key={s.historyId}>
                      <td>{new Date(s.createdAt).toLocaleString()}</td>
                      <td>
                        <span className="badge badge-muted">{s.previousStatus || "INIT"}</span>
                      </td>
                      <td>
                        <span
                          className={`badge ${
                            s.newStatus === "ACTIVE"
                              ? "badge-success"
                              : s.newStatus === "PENDING_APPROVAL"
                              ? "badge-warning"
                              : "badge-primary"
                          }`}
                        >
                          {s.newStatus}
                        </span>
                      </td>
                      <td>{s.remarks || "\u2014"}</td>
                      <td>
                        <strong>{s.changedByName}</strong>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Unit Transfer History */}
      {isManager && (
        <div className="section-card" style={{ marginTop: "1.5rem" }}>
          <div className="section-header">
            <h3>Unit Transfer Audit Trail</h3>
          </div>
          {transferHistory.length === 0 ? (
            <p className="empty-state">No historical unit transfers recorded.</p>
          ) : (
            <div className="units-table-wrapper">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Date &amp; Time</th>
                    <th>From Unit</th>
                    <th>To Unit</th>
                    <th>Transfer Reason</th>
                    <th>Authorized By</th>
                  </tr>
                </thead>
                <tbody>
                  {transferHistory.map((t) => (
                    <tr key={t.transferId}>
                      <td>{new Date(t.transferredAt).toLocaleString()}</td>
                      <td>{t.fromUnitName || "Initial Allocation"}</td>
                      <td>
                        <strong>{t.toUnitName}</strong>
                      </td>
                      <td>{t.reason || "\u2014"}</td>
                      <td>{t.transferredByName}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Historical Membership records */}
      <div className="section-card" style={{ marginTop: "1.5rem" }}>
        <div className="section-header">
          <h3>Unit Membership History</h3>
        </div>

        {history.length === 0 ? (
          <p className="empty-state">No recorded membership changes.</p>
        ) : (
          <div className="units-table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Unit Number</th>
                  <th>Unit Name</th>
                  <th>Joined Date</th>
                  <th>Left Date</th>
                  <th>Membership Status</th>
                </tr>
              </thead>
              <tbody>
                {history.map((item) => (
                  <tr key={item.membershipId}>
                    <td>
                      <span className="badge badge-primary">{item.unitNumber}</span>
                    </td>
                    <td>{item.unitName}</td>
                    <td>{new Date(item.joinedAt).toLocaleDateString()}</td>
                    <td>
                      {item.leftAt
                        ? new Date(item.leftAt).toLocaleDateString()
                        : "Current"}
                    </td>
                    <td>
                      {item.isActive ? (
                        <span className="badge badge-success">ACTIVE</span>
                      ) : (
                        <span className="badge badge-muted">HISTORICAL</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Status Update Modal */}
      {showStatusModal && (
        <div className="modal-backdrop">
          <div className="modal-card">
            <div className="modal-header">
              <h2>Update Volunteer Status</h2>
              <button
                type="button"
                className="btn-close"
                onClick={() => setShowStatusModal(false)}
              >
                &times;
              </button>
            </div>
            <div className="modal-body">
              <form onSubmit={handleUpdateStatus} className="form-stack" style={{ padding: 0 }}>
                <div className="form-group">
                  <label htmlFor="newStatusSelect">Status Transition *</label>
                  <CustomSelect
                    id="newStatusSelect"
                    value={selectedNewStatus}
                    onChange={setSelectedNewStatus}
                    options={[
                      { value: "ACTIVE", label: "ACTIVE - Fully Approved Volunteer" },
                      { value: "PENDING_APPROVAL", label: "PENDING_APPROVAL - Under Verification" },
                      { value: "INACTIVE", label: "INACTIVE - Suspended Participation" },
                      { value: "ALUMNI", label: "ALUMNI - Graduated NSS Member" },
                      { value: "SUSPENDED", label: "SUSPENDED - Disciplinary Hold" },
                    ]}
                  />
                </div>

                <div className="form-group">
                  <label htmlFor="statusRemarksInput">Audit Remarks / Justification</label>
                  <textarea
                    id="statusRemarksInput"
                    rows={3}
                    placeholder="e.g. Completed orientation and verified enrollment documents."
                    value={statusRemarks}
                    onChange={(e) => setStatusRemarks(e.target.value)}
                  />
                </div>

                <div className="modal-actions">
                  <button
                    type="button"
                    onClick={() => setShowStatusModal(false)}
                    className="btn-secondary"
                    disabled={updatingStatus}
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="btn-primary"
                    disabled={updatingStatus}
                  >
                    {updatingStatus ? "Recording Update..." : "Confirm Status Change"}
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

