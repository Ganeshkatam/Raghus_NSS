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
  name: string;
  activeUnitId: number | null;
  activeUnitNumber: string | null;
  activeUnitName: string | null;
}

interface ServiceHourSummary {
  totalApprovedHours: number;
  approvedCount: number;
  pendingCount: number;
  entries: {
    entryId: number;
    hours: number;
    status: string;
    eventTitle: string | null;
    description: string;
    approvedByName: string | null;
    createdAt: string;
  }[];
}

interface AnnouncementItem {
  announcementId: number;
  title: string;
  content: string;
  priority: string;
  targetAudience: string;
  publishedAt: string;
}

export const Dashboard: React.FC = () => {
  const { user, roleDisplayName, isAdmin, isCoordinator, isOfficer, isCoordinatorOrOfficer, isStudentLeader, isVolunteer } = useAuth();
  const isPlainVolunteer = isVolunteer && !isCoordinatorOrOfficer && !isStudentLeader;

  const [units, setUnits] = useState<UnitSummary[]>([]);
  const [events, setEvents] = useState<EventItem[]>([]);
  const [volunteerCount, setVolunteerCount] = useState<number>(0);
  const [pendingClaimsCount, setPendingClaimsCount] = useState<number>(0);
  const [volunteerProfile, setVolunteerProfile] = useState<VolunteerProfile | null>(null);
  const [serviceSummary, setServiceSummary] = useState<ServiceHourSummary | null>(null);
  const [announcements, setAnnouncements] = useState<AnnouncementItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function loadData() {
      setLoading(true);
      try {
        const [eventsData, announcementsData] = await Promise.all([
          apiRequest<{ content: EventItem[] }>("/events?size=10").catch(() => ({ content: [] })),
          apiRequest<{ content: AnnouncementItem[] }>("/announcements?size=4").catch(() => ({ content: [] })),
        ]);
        setEvents(eventsData.content || []);
        setAnnouncements(announcementsData.content || []);

        if (isCoordinatorOrOfficer || isStudentLeader) {
          const [unitsData, volPage, pendingClaims] = await Promise.all([
            apiRequest<UnitSummary[]>("/units").catch(() => []),
            apiRequest<{ totalElements: number }>("/volunteers?size=1").catch(() => ({ totalElements: 0 })),
            apiRequest<any[]>("/service-hours/pending").catch(() => []),
          ]);
          setUnits(unitsData);
          setVolunteerCount(volPage.totalElements || 0);
          setPendingClaimsCount(pendingClaims?.length || 0);
        }

        if (isPlainVolunteer) {
          const [profile, hours] = await Promise.all([
            apiRequest<VolunteerProfile>("/volunteers/me").catch(() => null),
            apiRequest<ServiceHourSummary>("/service-hours/my").catch(() => null),
          ]);
          setVolunteerProfile(profile);
          setServiceSummary(hours);
        }
      } finally {
        setLoading(false);
      }
    }
    loadData();
  }, [isCoordinatorOrOfficer, isStudentLeader, isPlainVolunteer]);

  const totalMemberships = units.reduce((acc, u) => acc + (u.activeMemberCount || 0), 0);
  const upcomingEvents = events.filter((e) => e.status === "OPEN" || e.status === "PUBLISHED");
  const attentionEvents = events.filter((e) => e.status === "OPEN" && e.remainingCapacity <= Math.max(1, Math.floor(e.capacity * 0.15)));

  // Title and subtitle according to role
  const pageTitle = isPlainVolunteer
    ? "My NSS Dashboard"
    : isStudentLeader
    ? "Unit Leadership Overview"
    : isOfficer
    ? "Programme Officer Operational Overview"
    : isCoordinator
    ? "College NSS Central Overview"
    : "Institutional System Administration";

  const approvedHours = serviceSummary?.totalApprovedHours || 0;
  const certificatePercent = Math.min(100, Math.round((approvedHours / 240) * 100));

  return (
    <div className="page-container">
      {/* Header */}
      <div className="page-header">
        <div>
          <div className="breadcrumbs">
            <span>Raghu Engineering College</span>
            <span>/</span>
            <span className="current">National Service Scheme</span>
          </div>
          <h1>{pageTitle}</h1>
          <p className="subtitle">
            Welcome back, <strong>{user?.name}</strong> &bull; {roleDisplayName}
            {volunteerProfile?.collegeId && (
              <span> &bull; College ID: <strong style={{ fontFamily: "monospace" }}>{volunteerProfile.collegeId}</strong></span>
            )}
            {volunteerProfile?.activeUnitNumber && (
              <span> &bull; Assigned: <strong>{volunteerProfile.activeUnitNumber}</strong></span>
            )}
          </p>
        </div>

        <div className="card-actions">
          {isCoordinatorOrOfficer ? (
            <>
              <Link to="/events" className="btn-primary">
                + Create Event
              </Link>
              <Link to="/attendance" className="btn-secondary">
                Attendance Operations
              </Link>
              <Link to="/service-hours" className="btn-secondary">
                Review Claims Queue ({pendingClaimsCount})
              </Link>
            </>
          ) : (
            <>
              <Link to="/events" className="btn-primary">
                Browse Programmes
              </Link>
              <Link to="/attendance" className="btn-secondary">
                Attendance Check-In
              </Link>
              <Link to="/service-hours" className="btn-secondary">
                My Service Ledger
              </Link>
            </>
          )}
        </div>
      </div>

      {/* ----------------- 1. VOLUNTEER VIEW ("MY NSS") ----------------- */}
      {isPlainVolunteer && (
        <>
          {/* 240-Hour Certificate Tracker Banner */}
          <div className="section-card" style={{ marginBottom: "1.5rem", background: "linear-gradient(135deg, #f8fafc 0%, #eff6ff 100%)", border: "1px solid #bfdbfe" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: "1rem" }}>
              <div>
                <span className="badge badge-primary" style={{ marginBottom: "0.5rem" }}>Degree Requirement</span>
                <h2 style={{ margin: "0.25rem 0", color: "#1e3a8a" }}>240-Hour University NSS Certificate Tracker</h2>
                <p style={{ margin: 0, color: "#475569", fontSize: "0.9rem" }}>
                  Completion of 240 verified community service hours qualifies for the official university NSS graduation certificate.
                </p>
              </div>
              <div style={{ textAlign: "right" }}>
                <span style={{ fontSize: "2.25rem", fontWeight: 800, color: "#1e40af" }}>{approvedHours}</span>
                <span style={{ fontSize: "1.1rem", color: "#64748b", fontWeight: 600 }}> / 240 hrs</span>
                <div style={{ fontSize: "0.8rem", color: "#64748b" }}>{certificatePercent}% of milestone completed</div>
              </div>
            </div>

            {/* Progress bar */}
            <div style={{ width: "100%", height: "10px", backgroundColor: "#e2e8f0", borderRadius: "9999px", marginTop: "1rem", overflow: "hidden" }}>
              <div
                style={{
                  height: "100%",
                  width: `${certificatePercent}%`,
                  backgroundColor: "#2563eb",
                  borderRadius: "9999px",
                  transition: "width 0.4s ease",
                }}
              />
            </div>

            {/* Quick stats under milestone */}
            <div style={{ display: "flex", gap: "2rem", marginTop: "1rem", flexWrap: "wrap", fontSize: "0.85rem", color: "#475569" }}>
              <span>Verified Activities: <strong>{serviceSummary?.approvedCount || 0}</strong></span>
              <span>Pending Claims: <strong>{serviceSummary?.pendingCount || 0}</strong></span>
              <span>Assigned Unit: <strong>{volunteerProfile?.activeUnitNumber || "Unit 01"} ({volunteerProfile?.activeUnitName || "NSS Unit"})</strong></span>
              <span>Enrollment Status: <span className="badge badge-success">{volunteerProfile?.status || "ACTIVE"}</span></span>
            </div>
          </div>

          {/* Metric Grid */}
          <div className="metric-grid">
            <div className="metric-card">
              <span className="metric-label">Verified Service Hours</span>
              <span className="metric-value">{loading ? "..." : approvedHours}</span>
              <span className="metric-context">Institutional accredited hours</span>
            </div>

            <div className="metric-card">
              <span className="metric-label">Approved Activities</span>
              <span className="metric-value">{loading ? "..." : (serviceSummary?.approvedCount || 0)}</span>
              <span className="metric-context">Campus drives &amp; community events</span>
            </div>

            <div className="metric-card">
              <span className="metric-label">Pending Claim Hours</span>
              <span className="metric-value">{loading ? "..." : (serviceSummary?.pendingCount || 0)}</span>
              <span className="metric-context">Awaiting coordinator review</span>
            </div>

            <div className="metric-card">
              <span className="metric-label">Upcoming Programmes</span>
              <span className="metric-value">{loading ? "..." : upcomingEvents.length}</span>
              <span className="metric-context">Available for registration</span>
            </div>
          </div>

          {/* Detail Grid */}
          <div className="detail-grid">
            {/* Left: Open Programmes */}
            <section className="section-card">
              <div className="section-header">
                <div>
                  <h2>Open NSS Programmes</h2>
                  <p className="subtitle">Participate in upcoming campus and community drives to earn verified hours.</p>
                </div>
                <Link to="/events" className="btn-secondary-sm">
                  View All ({events.length})
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
                        <th>Date &amp; Venue</th>
                        <th>Seats Left</th>
                        <th>Action</th>
                      </tr>
                    </thead>
                    <tbody>
                      {upcomingEvents.slice(0, 5).map((evt) => (
                        <tr key={evt.eventId}>
                          <td>
                            <strong>{evt.title}</strong>
                            <div className="cell-sub">{evt.unitName}</div>
                          </td>
                          <td>
                            <span className="badge badge-primary">{evt.eventType}</span>
                          </td>
                          <td>
                            <div>{new Date(evt.startAt).toLocaleDateString()}</div>
                            <div className="cell-sub">{evt.venue}</div>
                          </td>
                          <td>
                            <span className="badge badge-success">
                              {evt.remainingCapacity} seats
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

            {/* Right: Recent Verified Service Activities & Announcements */}
            <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>
              {/* Recent Activity History */}
              <section className="section-card">
                <div className="section-header">
                  <div>
                    <h2>My Recent Service Activities</h2>
                    <p className="subtitle">Verified attendance and service claims.</p>
                  </div>
                  <Link to="/service-hours" className="cell-sub">
                    All History
                  </Link>
                </div>

                {!serviceSummary || serviceSummary.entries.length === 0 ? (
                  <div className="empty-state" style={{ padding: "1.5rem 1rem" }}>
                    <p>No verified activities recorded yet. Register for an event or submit an external claim.</p>
                    <Link to="/service-hours" className="btn-secondary-sm" style={{ marginTop: "0.5rem" }}>
                      + Log Service Claim
                    </Link>
                  </div>
                ) : (
                  <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
                    {serviceSummary.entries.slice(0, 4).map((entry) => (
                      <div
                        key={entry.entryId}
                        style={{
                          display: "flex",
                          justifyContent: "space-between",
                          alignItems: "center",
                          padding: "0.75rem",
                          borderRadius: "0.5rem",
                          border: "1px solid #f1f5f9",
                          backgroundColor: "#f8fafc",
                        }}
                      >
                        <div>
                          <div style={{ fontWeight: 600, fontSize: "0.875rem" }}>
                            {entry.eventTitle || entry.description}
                          </div>
                          <div className="cell-sub">
                            {new Date(entry.createdAt).toLocaleDateString()} &bull; {entry.approvedByName || "Awaiting review"}
                          </div>
                        </div>
                        <div style={{ textAlign: "right" }}>
                          <span style={{ fontWeight: 700, fontSize: "1rem", color: "#1e3a8a" }}>
                            +{entry.hours} hrs
                          </span>
                          <div>
                            <span
                              className={`badge ${
                                entry.status === "APPROVED"
                                  ? "badge-success"
                                  : entry.status === "PENDING"
                                  ? "badge-warning"
                                  : "badge-muted"
                              }`}
                              style={{ fontSize: "0.7rem" }}
                            >
                              {entry.status}
                            </span>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </section>

              {/* Official Announcements */}
              <section className="section-card">
                <div className="section-header">
                  <h2>Official NSS Circulars</h2>
                  <Link to="/announcements" className="cell-sub">
                    View All
                  </Link>
                </div>
                {announcements.length === 0 ? (
                  <p className="cell-sub" style={{ padding: "1rem" }}>No active announcements at this time.</p>
                ) : (
                  <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
                    {announcements.map((ann) => (
                      <div
                        key={ann.announcementId}
                        style={{
                          borderLeft: ann.priority === "URGENT" ? "3px solid #dc2626" : "3px solid var(--nss-navy)",
                          paddingLeft: "0.75rem",
                        }}
                      >
                        <div style={{ fontWeight: 600, fontSize: "0.9rem" }}>{ann.title}</div>
                        <div className="cell-sub">
                          {new Date(ann.publishedAt).toLocaleDateString()} &bull; {ann.targetAudience}
                        </div>
                        <p style={{ fontSize: "0.82rem", marginTop: "0.25rem", color: "var(--text-secondary)" }}>
                          {ann.content.length > 120 ? ann.content.substring(0, 120) + "..." : ann.content}
                        </p>
                      </div>
                    ))}
                  </div>
                )}
              </section>
            </div>
          </div>
        </>
      )}

      {/* ----------------- 2. OPERATIONAL / ADMINISTRATIVE VIEW ----------------- */}
      {(isCoordinatorOrOfficer || isStudentLeader) && (
        <>
          {/* Pending claims action notification */}
          {pendingClaimsCount > 0 && (
            <div className="alert alert-warning" style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <div style={{ display: "flex", alignItems: "center", gap: "0.75rem" }}>
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <circle cx="12" cy="12" r="10"></circle>
                  <line x1="12" y1="8" x2="12" y2="12"></line>
                  <line x1="12" y1="16" x2="12.01" y2="16"></line>
                </svg>
                <span>
                  <strong>Action Required:</strong> You have <strong>{pendingClaimsCount}</strong> volunteer service hour claim(s) awaiting review.
                </span>
              </div>
              <Link to="/service-hours" className="btn-secondary-sm">
                Review Claims Queue
              </Link>
            </div>
          )}

          {attentionEvents.length > 0 && (
            <div className="alert alert-warning">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="12" cy="12" r="10"></circle>
                <line x1="12" y1="8" x2="12" y2="12"></line>
                <line x1="12" y1="16" x2="12.01" y2="16"></line>
              </svg>
              <span>
                <strong>Capacity Alert:</strong> {attentionEvents.length} event(s) have reached over 85% enrollment capacity.
              </span>
            </div>
          )}

          {/* Operational Metrics */}
          <div className="metric-grid">
            <div className="metric-card">
              <span className="metric-label">Enrolled Volunteers</span>
              <span className="metric-value">{loading ? "..." : volunteerCount}</span>
              <span className="metric-context">Registered student volunteers</span>
            </div>

            <div className="metric-card">
              <span className="metric-label">NSS Units</span>
              <span className="metric-value">{loading ? "..." : units.length}</span>
              <span className="metric-context">Operational units (Units 1–5)</span>
            </div>

            <div className="metric-card">
              <span className="metric-label">Pending Claim Reviews</span>
              <span className="metric-value" style={{ color: pendingClaimsCount > 0 ? "#b45309" : "inherit" }}>
                {loading ? "..." : pendingClaimsCount}
              </span>
              <span className="metric-context">Claims awaiting PO sign-off</span>
            </div>

            <div className="metric-card">
              <span className="metric-label">Institutional Programmes</span>
              <span className="metric-value">{loading ? "..." : events.length}</span>
              <span className="metric-context">Total planned and completed events</span>
            </div>
          </div>

          {/* Operational Detail Grid */}
          <div className="detail-grid">
            {/* Upcoming Programmes & Live Capacity */}
            <section className="section-card">
              <div className="section-header">
                <div>
                  <h2>Programmes &amp; Registration Capacity</h2>
                  <p className="subtitle">Monitor enrollment limits, registration windows, and attendance sessions.</p>
                </div>
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
                  <Link to="/events" className="btn-primary" style={{ marginTop: "0.5rem" }}>
                    + Create Event
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
                        <th>Venue</th>
                        <th>Capacity</th>
                        <th>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {events.slice(0, 6).map((evt) => {
                        const capPct = evt.capacity > 0 ? Math.min(100, Math.round((evt.registeredCount / evt.capacity) * 100)) : 0;
                        return (
                          <tr key={evt.eventId}>
                            <td>
                              <strong>{evt.title}</strong>
                              <div className="cell-sub">{evt.unitName} &bull; {evt.eventType}</div>
                            </td>
                            <td>
                              <span
                                className={`badge ${
                                  evt.status === "OPEN"
                                    ? "badge-success"
                                    : evt.status === "PUBLISHED"
                                    ? "badge-primary"
                                    : evt.status === "COMPLETED"
                                    ? "badge-muted"
                                    : "badge-warning"
                                }`}
                              >
                                {evt.status}
                              </span>
                            </td>
                            <td>{new Date(evt.startAt).toLocaleString([], { dateStyle: "short", timeStyle: "short" })}</td>
                            <td>{evt.venue}</td>
                            <td>
                              <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
                                <span>{evt.registeredCount}/{evt.capacity}</span>
                                <div style={{ width: "60px", height: "6px", backgroundColor: "#e2e8f0", borderRadius: "3px", overflow: "hidden" }}>
                                  <div
                                    style={{
                                      width: `${capPct}%`,
                                      height: "100%",
                                      backgroundColor: capPct > 85 ? "#dc2626" : "#2563eb",
                                    }}
                                  />
                                </div>
                              </div>
                            </td>
                            <td>
                              <div style={{ display: "flex", gap: "0.5rem" }}>
                                <Link to={`/events/${evt.eventId}`} className="btn-secondary-sm">
                                  Manage
                                </Link>
                                <Link to="/attendance" className="btn-secondary-sm">
                                  Attendance
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
            </section>

            {/* Right Column: Units Overview & Quick Shortcuts */}
            <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>
              {/* Units Overview Card */}
              <section className="section-card">
                <div className="section-header">
                  <div>
                    <h2>NSS Units Master</h2>
                    <p className="subtitle">5 active operational units.</p>
                  </div>
                  <Link to="/units" className="cell-sub">
                    View Details
                  </Link>
                </div>

                <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
                  {units.map((u) => (
                    <div
                      key={u.unitId}
                      style={{
                        display: "flex",
                        justifyContent: "space-between",
                        alignItems: "center",
                        padding: "0.75rem 1rem",
                        borderRadius: "0.5rem",
                        border: "1px solid #f1f5f9",
                        backgroundColor: "#f8fafc",
                      }}
                    >
                      <div>
                        <strong>{u.unitNumber}</strong> &bull; {u.unitName}
                      </div>
                      <span className="badge badge-primary">
                        {u.activeMemberCount || 0} volunteers
                      </span>
                    </div>
                  ))}
                </div>
              </section>

              {/* Administrative Shortcuts */}
              <section className="section-card">
                <div className="section-header">
                  <h2>Operational Actions</h2>
                </div>
                <div style={{ display: "flex", flexDirection: "column", gap: "0.5rem" }}>
                  <Link to="/volunteers" className="btn-secondary">
                    Browse and Manage Volunteers
                  </Link>
                  <Link to="/attendance" className="btn-secondary">
                    Open Live Attendance Session
                  </Link>
                  <Link to="/service-hours" className="btn-secondary">
                    Review Pending Service Claims ({pendingClaimsCount})
                  </Link>
                  <Link to="/reports" className="btn-secondary">
                    Generate Institutional Reports
                  </Link>
                  {isAdmin && (
                    <Link to="/admin" className="btn-primary">
                      Institutional System Admin
                    </Link>
                  )}
                </div>
              </section>
            </div>
          </div>
        </>
      )}
    </div>
  );
};
