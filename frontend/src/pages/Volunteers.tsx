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

      {error ? (
        <div className="alert alert-error">
          <strong>Access Notice:</strong> {error}
        </div>
      ) : (
        <>
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
                  <option value="INACTIVE">INACTIVE</option>
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
                      <th>Year</th>
                      <th>Active Unit</th>
                      <th>Status</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {volunteers.map((vol) => (
                      <tr key={vol.volunteerId}>
                        <td>
                          <strong>{vol.collegeId}</strong>
                        </td>
                        <td>
                          <div className="cell-title">{vol.name}</div>
                          <div className="cell-sub">{vol.email}</div>
                        </td>
                        <td>{vol.department}</td>
                        <td>Year {vol.yearOfStudy}</td>
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
                                ? "badge-primary"
                                : "badge-muted"
                            }`}
                          >
                            {vol.status}
                          </span>
                        </td>
                        <td>
                          <Link
                            to={`/volunteers/${vol.volunteerId}`}
                            className="table-action-link"
                          >
                            View Profile
                          </Link>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
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
