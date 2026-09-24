import React, { useEffect, useState, useCallback } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { apiRequest, ApiError } from "../api/client";

interface VolunteerItem {
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
}

interface VolunteerPageResponse {
  content: VolunteerItem[];
  totalElements: number;
  totalPages: number;
  number: number;
}

export const Volunteers: React.FC = () => {
  const { isCoordinatorOrOfficer } = useAuth();

  const [volunteers, setVolunteers] = useState<VolunteerItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Filters & Pagination
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("");
  const [department, setDepartment] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalCount, setTotalCount] = useState(0);

  // Modal for new volunteer
  const [showModal, setShowModal] = useState(false);
  const [newName, setNewName] = useState("");
  const [newEmail, setNewEmail] = useState("");
  const [newCollegeId, setNewCollegeId] = useState("");
  const [newDept, setNewDept] = useState("Computer Science");
  const [newYear, setNewYear] = useState(1);
  const [newPhone, setNewPhone] = useState("");
  const [modalError, setModalError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);

  const loadVolunteers = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params = new URLSearchParams();
      if (search) params.append("search", search);
      if (status) params.append("status", status);
      if (department) params.append("department", department);
      params.append("page", page.toString());
      params.append("size", "10");

      const data = await apiRequest<VolunteerPageResponse>(`/volunteers?${params.toString()}`);
      setVolunteers(data.content || []);
      setTotalPages(data.totalPages || 1);
      setTotalCount(data.totalElements || 0);
    } catch (err: unknown) {
      const apiErr = err as ApiError;
      if (apiErr.code === "FORBIDDEN") {
        setError("You do not have administrative permission to view the global volunteers list.");
      } else {
        setError(apiErr.message || "Failed to load volunteers.");
      }
    } finally {
      setLoading(false);
    }
  }, [search, status, department, page]);

  useEffect(() => {
    loadVolunteers();
  }, [loadVolunteers]);

  const handleCreateVolunteer = async (e: React.FormEvent) => {
    e.preventDefault();
    setModalError(null);
    setCreating(true);

    try {
      await apiRequest<VolunteerItem>("/volunteers", {
        method: "POST",
        body: JSON.stringify({
          name: newName,
          email: newEmail,
          collegeId: newCollegeId,
          department: newDept,
          yearOfStudy: Number(newYear),
          phone: newPhone || undefined,
        }),
      });

      setShowModal(false);
      setNewName("");
      setNewEmail("");
      setNewCollegeId("");
      setNewPhone("");
      loadVolunteers();
    } catch (err: unknown) {
      const apiErr = err as ApiError;
      setModalError(apiErr.message || "Failed to register volunteer.");
    } finally {
      setCreating(false);
    }
  };

  // Quick Status Transition Modal
  const [statusModalVol, setStatusModalVol] = useState<VolunteerItem | null>(null);
  const [targetStatus, setTargetStatus] = useState<string>("ACTIVE");
  const [transitionRemarks, setTransitionRemarks] = useState<string>("");
  const [processingStatus, setProcessingStatus] = useState<boolean>(false);
  const [statusActionSuccess, setStatusActionSuccess] = useState<string | null>(null);

  const calculateCompleteness = (vol: VolunteerItem): number => {
    let score = 0;
    if (vol.name) score += 20;
    if (vol.email) score += 20;
    if (vol.collegeId) score += 20;
    if (vol.department) score += 15;
    if (vol.phone && vol.phone.trim().length > 0) score += 15;
    if (vol.activeUnitId) score += 10;
    return score;
  };

  const handleQuickStatusSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!statusModalVol) return;
    setProcessingStatus(true);
    try {
      await apiRequest(`/volunteers/${statusModalVol.volunteerId}/status`, {
        method: "POST",
        body: JSON.stringify({
          newStatus: targetStatus,
          remarks: transitionRemarks.trim() || undefined,
        }),
      });
      setStatusActionSuccess(`Volunteer status changed to ${targetStatus}.`);
      setStatusModalVol(null);
      setTransitionRemarks("");
      loadVolunteers();
    } catch (err: any) {
      alert(err.message || "Failed to update volunteer status.");
    } finally {
      setProcessingStatus(false);
    }
  };

  const statusPills = [
    { label: "All", value: "" },
    { label: "Pending Approval", value: "PENDING_APPROVAL" },
    { label: "Active", value: "ACTIVE" },
    { label: "Inactive", value: "INACTIVE" },
    { label: "Alumni", value: "ALUMNI" },
  ];

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h1>Volunteers Registry</h1>
          <p className="subtitle">
            Manage enrolled NSS student volunteers and their unit allocations.
          </p>
        </div>

        {isCoordinatorOrOfficer && (
          <button onClick={() => setShowModal(true)} className="btn-primary">
            + Enroll Volunteer
          </button>
        )}
      </div>

      {statusActionSuccess && (
        <div className="alert alert-success" style={{ marginBottom: "1rem" }}>
          <strong>Success:</strong> {statusActionSuccess}
        </div>
      )}

      {error ? (
        <div className="alert alert-error">
          <strong>Access Notice:</strong> {error}
        </div>
      ) : (
        <>
          {/* Status Filter Pills */}
          <div style={{ display: "flex", flexWrap: "wrap", gap: "0.5rem", marginBottom: "1rem" }}>
            {statusPills.map((pill) => {
              const isSelected = status === pill.value;
              return (
                <button
                  key={pill.value}
                  type="button"
                  onClick={() => {
                    setStatus(pill.value);
                    setPage(0);
                  }}
                  style={{
                    padding: "0.4rem 0.85rem",
                    borderRadius: "9999px",
                    border: isSelected ? "1px solid #1e40af" : "1px solid #cbd5e1",
                    backgroundColor: isSelected ? "#1e40af" : "#ffffff",
                    color: isSelected ? "#ffffff" : "#475569",
                    fontWeight: isSelected ? 700 : 500,
                    fontSize: "0.85rem",
                    cursor: "pointer",
                    transition: "all 0.15s ease-in-out",
                  }}
                >
                  {pill.label}
                </button>
              );
            })}
          </div>

          <div className="filters-card">
            <div className="filters-grid">
              <div className="filter-item search-box">
                <label htmlFor="search">Search</label>
                <input
                  id="search"
                  type="text"
                  value={search}
                  onChange={(e) => {
                    setSearch(e.target.value);
                    setPage(0);
                  }}
                  placeholder="Search by name or college ID..."
                />
              </div>

              <div className="filter-item">
                <label htmlFor="department">Department</label>
                <select
                  id="department"
                  value={department}
                  onChange={(e) => {
                    setDepartment(e.target.value);
                    setPage(0);
                  }}
                >
                  <option value="">All Departments</option>
                  <option value="Computer Science">Computer Science</option>
                  <option value="Information Technology">Information Technology</option>
                  <option value="Mechanical Engineering">Mechanical Engineering</option>
                  <option value="Electronics & Communication">Electronics & Communication</option>
                  <option value="Electrical Engineering">Electrical Engineering</option>
                  <option value="Civil Engineering">Civil Engineering</option>
                </select>
              </div>

              <div className="filter-item">
                <label htmlFor="status">Status</label>
                <select
                  id="status"
                  value={status}
                  onChange={(e) => {
                    setStatus(e.target.value);
                    setPage(0);
                  }}
                >
                  <option value="">All Statuses</option>
                  <option value="ACTIVE">ACTIVE</option>
                  <option value="PENDING_APPROVAL">PENDING_APPROVAL</option>
                  <option value="INACTIVE">INACTIVE</option>
                  <option value="SUSPENDED">SUSPENDED</option>
                  <option value="ALUMNI">ALUMNI</option>
                </select>
              </div>
            </div>
          </div>

          <div className="section-card">
            <div className="results-count">
              Showing {volunteers.length} of {totalCount} volunteers
            </div>

            {loading ? (
              <p className="loading-state">Loading volunteers list...</p>
            ) : volunteers.length === 0 ? (
              <div className="empty-state">
                <p>No volunteers matched your search/filter criteria.</p>
              </div>
            ) : (
              <div className="units-table-wrapper">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>College ID</th>
                      <th>Full Name</th>
                      <th>Department</th>
                      <th>Active Unit</th>
                      <th>Status</th>
                      <th>Profile Completeness</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {volunteers.map((vol) => {
                      const completeness = calculateCompleteness(vol);
                      const isPending = vol.status === "PENDING_APPROVAL";
                      return (
                        <tr key={vol.volunteerId}>
                          <td>
                            <strong>{vol.collegeId}</strong>
                          </td>
                          <td>
                            <div className="cell-title">{vol.name}</div>
                            <div className="cell-sub">{vol.email}</div>
                          </td>
                          <td>
                            <div>{vol.department}</div>
                            <div className="cell-sub">Year {vol.yearOfStudy}</div>
                          </td>
                          <td>
                            {vol.activeUnitName ? (
                              <span className="badge badge-success">
                                {vol.activeUnitName}
                              </span>
                            ) : (
                              <span className="badge badge-muted">Unassigned</span>
                            )}
                          </td>
                          <td>
                            <span
                              className={`badge ${
                                vol.status === "ACTIVE"
                                  ? "badge-success"
                                  : vol.status === "PENDING_APPROVAL"
                                  ? "badge-warning"
                                  : vol.status === "SUSPENDED"
                                  ? "badge-danger"
                                  : "badge-muted"
                              }`}
                            >
                              {vol.status}
                            </span>
                          </td>
                          <td>
                            <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
                              <div
                                style={{
                                  flex: 1,
                                  height: "6px",
                                  backgroundColor: "#e2e8f0",
                                  borderRadius: "9999px",
                                  overflow: "hidden",
                                  maxWidth: "60px",
                                }}
                              >
                                <div
                                  style={{
                                    width: `${completeness}%`,
                                    height: "100%",
                                    backgroundColor: completeness >= 90 ? "#16a34a" : completeness >= 60 ? "#3b82f6" : "#f59e0b",
                                  }}
                                />
                              </div>
                              <span style={{ fontSize: "0.75rem", fontWeight: 700, color: "#64748b" }}>
                                {completeness}%
                              </span>
                            </div>
                          </td>
                          <td>
                            <div style={{ display: "flex", gap: "0.4rem", alignItems: "center" }}>
                              {isPending && isCoordinatorOrOfficer && (
                                <>
                                  <button
                                    type="button"
                                    onClick={() => {
                                      setStatusModalVol(vol);
                                      setTargetStatus("ACTIVE");
                                      setTransitionRemarks("Approved onboarding application.");
                                    }}
                                    className="btn-primary-sm"
                                    style={{ padding: "0.2rem 0.5rem", fontSize: "0.75rem" }}
                                  >
                                    Approve
                                  </button>
                                  <button
                                    type="button"
                                    onClick={() => {
                                      setStatusModalVol(vol);
                                      setTargetStatus("INACTIVE");
                                      setTransitionRemarks("Rejected application.");
                                    }}
                                    className="btn-danger-sm"
                                    style={{ padding: "0.2rem 0.5rem", fontSize: "0.75rem" }}
                                  >
                                    Reject
                                  </button>
                                </>
                              )}
                              <Link
                                to={`/volunteers/${vol.volunteerId}`}
                                className="table-action-link"
                              >
                                View
                              </Link>
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}

            {statusModalVol && (
              <div className="modal-backdrop">
                <div className="modal-card">
                  <div className="modal-header">
                    <h2>{targetStatus === "ACTIVE" ? "Approve Volunteer" : "Reject Volunteer"}</h2>
                    <button
                      type="button"
                      className="btn-close"
                      onClick={() => setStatusModalVol(null)}
                    >
                      &times;
                    </button>
                  </div>
                  <div className="modal-body">
                    <p className="subtitle" style={{ marginBottom: "1rem" }}>
                      Target Status: <strong>{targetStatus}</strong> for candidate <strong>{statusModalVol.name}</strong> ({statusModalVol.collegeId}).
                    </p>
                    <form onSubmit={handleQuickStatusSubmit} className="form-stack" style={{ padding: 0 }}>
                      <div className="form-group">
                        <label htmlFor="modalRemarksInput">Audit Remarks / Decision Note</label>
                        <textarea
                          id="modalRemarksInput"
                          rows={3}
                          value={transitionRemarks}
                          onChange={(e) => setTransitionRemarks(e.target.value)}
                          placeholder="Provide reasoning for institutional audit trail"
                        />
                      </div>
                      <div className="modal-actions">
                        <button
                          type="button"
                          onClick={() => setStatusModalVol(null)}
                          className="btn-secondary"
                          disabled={processingStatus}
                        >
                          Cancel
                        </button>
                        <button
                          type="submit"
                          className={targetStatus === "ACTIVE" ? "btn-primary" : "btn-danger"}
                          disabled={processingStatus}
                        >
                          {processingStatus ? "Updating..." : `Confirm ${targetStatus === "ACTIVE" ? "Approval" : "Rejection"}`}
                        </button>
                      </div>
                    </form>
                  </div>
                </div>
              </div>
            )}

            {totalPages > 1 && (
              <div className="pagination">
                <button
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={page === 0}
                  className="btn-pagination"
                >
                  Previous
                </button>
                <span className="pagination-info">
                  Page {page + 1} of {totalPages}
                </span>
                <button
                  onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                  disabled={page >= totalPages - 1}
                  className="btn-pagination"
                >
                  Next
                </button>
              </div>
            )}
          </div>
        </>
      )}

      {showModal && (
        <div className="modal-backdrop">
          <div className="modal-card">
            <div className="modal-header">
              <h3>Enroll New NSS Volunteer</h3>
              <button onClick={() => setShowModal(false)} className="btn-close">
                &times;
              </button>
            </div>

            {modalError && (
              <div className="alert alert-error">
                <strong>Error:</strong> {modalError}
              </div>
            )}

            <form onSubmit={handleCreateVolunteer} className="form-stack">
              <div className="form-group">
                <label htmlFor="modal-name">Student Full Name *</label>
                <input
                  id="modal-name"
                  type="text"
                  value={newName}
                  onChange={(e) => setNewName(e.target.value)}
                  placeholder="e.g. John Doe"
                  required
                />
              </div>

              <div className="form-group">
                <label htmlFor="modal-email">Institutional Email *</label>
                <input
                  id="modal-email"
                  type="email"
                  value={newEmail}
                  onChange={(e) => setNewEmail(e.target.value)}
                  placeholder="e.g. john@college.edu"
                  required
                />
              </div>

              <div className="form-row">
                <div className="form-group">
                  <label htmlFor="modal-collegeId">College Roll / ID *</label>
                  <input
                    id="modal-collegeId"
                    type="text"
                    value={newCollegeId}
                    onChange={(e) => setNewCollegeId(e.target.value)}
                    placeholder="e.g. 2026CS101"
                    required
                  />
                </div>

                <div className="form-group">
                  <label htmlFor="modal-year">Year of Study *</label>
                  <select
                    id="modal-year"
                    value={newYear}
                    onChange={(e) => setNewYear(Number(e.target.value))}
                  >
                    <option value={1}>1st Year</option>
                    <option value={2}>2nd Year</option>
                    <option value={3}>3rd Year</option>
                    <option value={4}>4th Year</option>
                  </select>
                </div>
              </div>

              <div className="form-group">
                <label htmlFor="modal-dept">Department *</label>
                <select
                  id="modal-dept"
                  value={newDept}
                  onChange={(e) => setNewDept(e.target.value)}
                >
                  <option value="Computer Science">Computer Science</option>
                  <option value="Information Technology">Information Technology</option>
                  <option value="Mechanical Engineering">Mechanical Engineering</option>
                  <option value="Electronics & Communication">Electronics & Communication</option>
                  <option value="Electrical Engineering">Electrical Engineering</option>
                  <option value="Civil Engineering">Civil Engineering</option>
                </select>
              </div>

              <div className="form-group">
                <label htmlFor="modal-phone">Phone Number</label>
                <input
                  id="modal-phone"
                  type="text"
                  value={newPhone}
                  onChange={(e) => setNewPhone(e.target.value)}
                  placeholder="e.g. 9876543210"
                />
              </div>

              <div className="modal-actions">
                <button
                  type="button"
                  onClick={() => setShowModal(false)}
                  className="btn-secondary"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn-primary"
                  disabled={creating}
                >
                  {creating ? "Enrolling..." : "Enroll Volunteer"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
