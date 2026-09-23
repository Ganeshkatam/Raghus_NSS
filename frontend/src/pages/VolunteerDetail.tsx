import React, { useEffect, useState } from "react";
import { useParams, Link } from "react-router-dom";
import { apiRequest, ApiError } from "../api/client";

interface VolunteerData {
  volunteerId: number;
  userId: string;
  name: string;
  email: string;
  phone: string;
  collegeId: string;
  department: string;
  yearOfStudy: number;
  joinDate: string;
  status: string;
  activeUnitId: number | null;
  activeUnitName: string | null;
  createdAt: string;
}

interface MembershipHistoryItem {
  membershipId: number;
  unitId: number;
  unitName: string;
  unitNumber: string;
  joinedAt: string;
  leftAt: string | null;
  isActive: boolean;
}

export const VolunteerDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const [volunteer, setVolunteer] = useState<VolunteerData | null>(null);
  const [history, setHistory] = useState<MembershipHistoryItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function loadData() {
      if (!id) return;
      setLoading(true);
      setError(null);
      try {
        const [volData, memData] = await Promise.all([
          apiRequest<VolunteerData>(`/volunteers/${id}`),
          apiRequest<MembershipHistoryItem[]>(`/volunteers/${id}/memberships`),
        ]);
        setVolunteer(volData);
        setHistory(memData);
      } catch (err: unknown) {
        const apiErr = err as ApiError;
        setError(apiErr.message || "Failed to load volunteer profile.");
      } finally {
        setLoading(false);
      }
    }
    loadData();
  }, [id]);

  if (loading) {
    return (
      <div className="page-container">
        <p className="loading-state">Loading volunteer profile...</p>
      </div>
    );
  }

  if (error || !volunteer) {
    return (
      <div className="page-container">
        <div className="alert alert-error">
          <strong>Error:</strong> {error || "Volunteer not found."}
        </div>
        <Link to="/volunteers" className="btn-secondary">
          &larr; Back to Volunteers
        </Link>
      </div>
    );
  }

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <Link to="/volunteers" className="back-link">
            &larr; Volunteers Registry
          </Link>
          <h1>{volunteer.name}</h1>
          <p className="subtitle">
            College ID: <strong>{volunteer.collegeId}</strong> &bull; Status:{" "}
            <span
              className={`badge ${
                volunteer.status === "ACTIVE" ? "badge-primary" : "badge-muted"
              }`}
            >
              {volunteer.status}
            </span>
          </p>
        </div>
      </div>

      <div className="grid-2-col">
        <div className="section-card">
          <div className="section-header">
            <h3>Academic & Contact Information</h3>
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
                View Unit Roster &rarr;
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
    </div>
  );
};
