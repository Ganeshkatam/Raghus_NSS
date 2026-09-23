import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { apiRequest } from "../api/client";

interface UnitSummary {
  unitId: number;
  unitName: string;
  unitNumber: string;
  activeMemberCount: number;
}

interface EventItem {
  eventId: number;
  unitId: number;
  unitName: string;
  title: string;
  eventType: string;
  startAt: string;
  venue: string;
  capacity: number;
  registeredCount: number;
  remainingCapacity: number;
  status: string;
}

interface VolunteerProfile {
  volunteerId: number;
  collegeId: string;
  department: string;
  yearOfStudy: number;
  status: string;
  activeUnitId: number | null;
  activeUnitNumber: string | null;
  activeUnitName: string | null;
}

export const Dashboard: React.FC = () => {
  const { user, roleDisplayName, isCoordinatorOrOfficer, isVolunteer } = useAuth();
  const [units, setUnits] = useState<UnitSummary[]>([]);
  const [events, setEvents] = useState<EventItem[]>([]);
  const [volunteerCount, setVolunteerCount] = useState<number>(0);
  const [volunteerProfile, setVolunteerProfile] = useState<VolunteerProfile | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function loadData() {
      setLoading(true);
      try {
        const [unitsData, eventsData] = await Promise.all([
          apiRequest<UnitSummary[]>("/units").catch(() => []),
          apiRequest<{ content: EventItem[] }>("/events?size=6").catch(() => ({ content: [] })),
        ]);
        setUnits(unitsData);
        setEvents(eventsData.content || []);

        if (isCoordinatorOrOfficer) {
          const volPage = await apiRequest<{ totalElements: number }>("/volunteers?size=1").catch(() => ({ totalElements: 0 }));
          setVolunteerCount(volPage.totalElements || 0);
        } else {
          const profile = await apiRequest<VolunteerProfile>("/volunteers/me").catch(() => null);
          setVolunteerProfile(profile);
        }
      } finally {
        setLoading(false);
      }
    }
    loadData();
  }, [isCoordinatorOrOfficer]);

  const totalMemberships = units.reduce((acc, u) => acc + (u.activeMemberCount || 0), 0);
  const upcomingEvents = events.filter((e) => e.status === "OPEN" || e.status === "PUBLISHED");
  const attentionEvents = events.filter((e) => e.status === "OPEN" && e.remainingCapacity <= Math.max(1, Math.floor(e.capacity * 0.15)));

  return (
    <div className="page-container">
      {/* Page Title & Context */}
      <div className="page-header">
        <div>
          <div className="breadcrumbs">
            <span>Raghu Engineering College</span>
            <span>/</span>
            <span className="current">National Service Scheme</span>
          </div>
          <h1>{isVolunteer && !isCoordinatorOrOfficer ? "My NSS Dashboard" : "NSS Operational Overview"}</h1>
          <p className="subtitle">
            Welcome back, <strong>{user?.name}</strong> &bull; {roleDisplayName}
          </p>
        </div>

        <div className="card-actions">
          {isCoordinatorOrOfficer ? (
            <>
              <Link to="/events" className="btn-primary">
                + Create Event
              </Link>
              <Link to="/units" className="btn-secondary">
                Manage Units
              </Link>
            </>
          ) : (
            <>
              <Link to="/events" className="btn-primary">
                Browse Events
              </Link>
              <Link to="/attendance" className="btn-secondary">
                My Attendance QR
              </Link>
            </>
          )}
        </div>
      </div>

      {/* ----------------- VOLUNTEER VIEW ("MY NSS") ----------------- */}
      {isVolunteer && !isCoordinatorOrOfficer && (
        <>
          <div className="metric-grid">
            <div className="metric-card">
              <span className="metric-label">Verified Service Hours</span>
              <span className="metric-value">42.5</span>
              <span className="metric-context">Academic Year 2025–26 &bull; Verified</span>
            </div>

            <div className="metric-card">
              <span className="metric-label">Attendance Rate</span>
              <span className="metric-value">92%</span>
              <span className="metric-context">11 of 12 registered events attended</span>
            </div>

            <div className="metric-card">
              <span className="metric-label">Assigned NSS Unit</span>
              <span className="metric-value" style={{ fontSize: "1.25rem", paddingTop: "0.4rem" }}>
                {volunteerProfile?.activeUnitNumber || "Unit 01"}
              </span>
              <span className="metric-context">{volunteerProfile?.activeUnitName || "NSS Unit 01 (Campus & Health)"}</span>
            </div>

            <div className="metric-card">
              <span className="metric-label">Active Programmes</span>
              <span className="metric-value">{upcomingEvents.length}</span>
              <span className="metric-context">Open for registration</span>
            </div>
          </div>

          <div className="detail-grid">
            <section className="section-card">
              <div className="section-header">
                <h2>Open NSS Programmes</h2>
                <Link to="/events" className="btn-secondary">
                  View All Events
                </Link>
              </div>

              {loading ? (
                <p className="loading-state">Loading programmes...</p>
              ) : upcomingEvents.length === 0 ? (
                <div className="empty-state">
                  <h3>No Open Programmes Currently</h3>
                  <p>Check back soon. Newly scheduled NSS activities for your unit will appear here.</p>
                </div>
              ) : (
                <div className="table-wrapper">
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th>Programme Title</th>
                        <th>Type</th>
                        <th>When</th>
                        <th>Venue</th>
                        <th>Availability</th>
                        <th>Action</th>
                      </tr>
                    </thead>
                    <tbody>
                      {upcomingEvents.map((evt) => (
                        <tr key={evt.eventId}>
                          <td>
                            <strong>{evt.title}</strong>
                            <div className="cell-sub">{evt.unitName}</div>
                          </td>
                          <td>
                            <span className="badge badge-primary">{evt.eventType}</span>
                          </td>
                          <td>{new Date(evt.startAt).toLocaleDateString()}</td>
                          <td>{evt.venue}</td>
                          <td>
                            <span className="badge badge-success">
                              {evt.remainingCapacity} seats left
                            </span>
                          </td>
                          <td>
                            <Link to={`/events/${evt.eventId}`} className="btn-primary-sm">
                              View &amp; Register
                            </Link>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>

            <section className="section-card">
              <div className="section-header">
                <h2>Announcements</h2>
                <Link to="/announcements" className="cell-sub">
                  All
                </Link>
              </div>
              <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
                <div style={{ borderLeft: "3px solid var(--nss-navy)", paddingLeft: "0.75rem" }}>
                  <div style={{ fontWeight: 600, fontSize: "0.9rem" }}>Annual Special Camp Orientation</div>
                  <div className="cell-sub">24 Sep 2026 &bull; Programme Officer</div>
                  <p style={{ fontSize: "0.82rem", marginTop: "0.25rem", color: "var(--text-secondary)" }}>
                    All second-year volunteers are invited to attend the briefing session in the Main Auditorium.
                  </p>
                </div>
                <div style={{ borderLeft: "3px solid var(--nss-crimson)", paddingLeft: "0.75rem" }}>
                  <div style={{ fontWeight: 600, fontSize: "0.9rem" }}>Service Hour Submission Deadline</div>
                  <div className="cell-sub">21 Sep 2026 &bull; NSS Cell</div>
                  <p style={{ fontSize: "0.82rem", marginTop: "0.25rem", color: "var(--text-secondary)" }}>
                    Ensure all external community service certificates are submitted before semester closure.
                  </p>
                </div>
              </div>
            </section>
          </div>
        </>
      )}

      {/* ----------------- OPERATIONAL VIEW (OFFICER / COORDINATOR / ADMIN) ----------------- */}
      {isCoordinatorOrOfficer && (
        <>
          {attentionEvents.length > 0 && (
            <div className="alert alert-warning">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="12" cy="12" r="10"></circle>
                <line x1="12" y1="8" x2="12" y2="12"></line>
                <line x1="12" y1="16" x2="12.01" y2="16"></line>
              </svg>
              <span>
                <strong>Attention Required:</strong> {attentionEvents.length} event(s) have reached over 85% capacity.
              </span>
            </div>
          )}

          <div className="metric-grid">
            <div className="metric-card">
              <span className="metric-label">Enrolled Volunteers</span>
              <span className="metric-value">{loading ? "..." : volunteerCount}</span>
              <span className="metric-context">Registered student volunteers</span>
            </div>

            <div className="metric-card">
              <span className="metric-label">NSS Units</span>
              <span className="metric-value">{loading ? "..." : units.length}</span>
              <span className="metric-context">Active operational units</span>
            </div>

            <div className="metric-card">
              <span className="metric-label">Active Memberships</span>
              <span className="metric-value">{loading ? "..." : totalMemberships}</span>
              <span className="metric-context">Assigned unit volunteers</span>
            </div>

            <div className="metric-card">
              <span className="metric-label">Institutional Programmes</span>
              <span className="metric-value">{loading ? "..." : events.length}</span>
              <span className="metric-context">Total planned / completed events</span>
            </div>
          </div>

          <div className="detail-grid">
            <section className="section-card">
              <div className="section-header">
                <h2>Upcoming Programmes &amp; Capacity</h2>
                <Link to="/events" className="btn-secondary">
                  Manage Events
                </Link>
              </div>

              {loading ? (
                <p className="loading-state">Loading programmes...</p>
              ) : events.length === 0 ? (
                <div className="empty-state">
                  <h3>No Programmes Configured</h3>
                  <p>Create your first NSS event to initiate registration and attendance workflows.</p>
                  <Link to="/events" className="btn-primary">
                    Create Event
                  </Link>
                </div>
              ) : (
                <div className="table-wrapper">
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th>Event</th>
                        <th>Status</th>
                        <th>Start Time</th>
                        <th>Capacity Filled</th>
                        <th>Action</th>
                      </tr>
                    </thead>
                    <tbody>
                      {events.slice(0, 6).map((evt) => (
                        <tr key={evt.eventId}>
                          <td>
                            <strong>{evt.title}</strong>
                            <div className="cell-sub">{evt.unitName} &bull; {evt.venue}</div>
                          </td>
                          <td>
                            <span className={`badge ${
                              evt.status === "OPEN"
                                ? "badge-success"
                                : evt.status === "PUBLISHED"
                                ? "badge-primary"
                                : evt.status === "CLOSED"
                                ? "badge-warning"
                                : "badge-muted"
                            }`}>
                              {evt.status}
                            </span>
                          </td>
                          <td>{new Date(evt.startAt).toLocaleString()}</td>
                          <td>
                            <strong>{evt.registeredCount} / {evt.capacity}</strong>
                            <div className="cell-sub">{evt.remainingCapacity} seats open</div>
                          </td>
                          <td>
                            <Link to={`/events/${evt.eventId}`} className="btn-secondary-sm">
                              Manage
                            </Link>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>

            <section className="section-card">
              <div className="section-header">
                <h2>Operational Quick Actions</h2>
              </div>
              <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
                <Link to="/events" className="btn-secondary" style={{ textAlign: "center" }}>
                  Schedule New Programme
                </Link>
                <Link to="/volunteers" className="btn-secondary" style={{ textAlign: "center" }}>
                  Enroll / Allocate Volunteer
                </Link>
                <Link to="/attendance" className="btn-secondary" style={{ textAlign: "center" }}>
                  Launch Attendance Session
                </Link>
                <Link to="/reports" className="btn-secondary" style={{ textAlign: "center" }}>
                  Export Activity Report
                </Link>
              </div>

              <div style={{ marginTop: "1.5rem", paddingTop: "1rem", borderTop: "1px solid var(--border-subtle)" }}>
                <div style={{ fontWeight: 700, fontSize: "0.85rem", color: "var(--nss-navy)", marginBottom: "0.5rem" }}>
                  Institutional Status
                </div>
                <div className="cell-sub">Authoritative Store: PostgreSQL</div>
                <div className="cell-sub">Identity: Spring Security + JWT RBAC</div>
                <div className="cell-sub">Concurrency: Pessimistic Row Locking</div>
              </div>
            </section>
          </div>
        </>
      )}
    </div>
  );
};
