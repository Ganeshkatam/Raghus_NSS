import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { apiRequest } from "../../api/client";

interface VolunteerProfile {
  volunteerId: string;
  collegeId: string;
  department: string;
  yearOfStudy: number;
  status: string;
  name: string;
  activeUnitId: string | null;
  activeUnitNumber: string | null;
  activeUnitName: string | null;
}

interface ServiceHourSummary {
  totalApprovedHours: number;
  approvedCount: number;
  pendingCount: number;
  entries: {
    entryId: string;
    hours: number;
    status: string;
    eventTitle: string | null;
    description: string;
    approvedByName: string | null;
    createdAt: string;
  }[];
}

interface EventItem {
  eventId: string;
  title: string;
  eventType: string;
  startAt: string;
  venue: string;
  capacity: number;
  registeredCount: number;
  remainingCapacity: number;
  status: string;
}

interface AnnouncementItem {
  announcementId: string;
  title: string;
  content: string;
  priority: string;
  publishedAt: string;
}

export const VolunteerDashboard: React.FC = () => {
  const { user } = useAuth();
  const [profile, setProfile] = useState<VolunteerProfile | null>(null);
  const [hoursSummary, setHoursSummary] = useState<ServiceHourSummary | null>(null);
  const [upcomingEvents, setUpcomingEvents] = useState<EventItem[]>([]);
  const [announcements, setAnnouncements] = useState<AnnouncementItem[]>([]);
  const [unreadNotifications, setUnreadNotifications] = useState<number>(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function loadData() {
      setLoading(true);
      try {
        const [profData, hoursData, eventsData, annData, notifData] = await Promise.all([
          apiRequest<VolunteerProfile>("/volunteers/me").catch(() => null),
          apiRequest<ServiceHourSummary>("/service-hours/my").catch(() => null),
          apiRequest<{ content: EventItem[] }>("/events?size=4").catch(() => ({ content: [] })),
          apiRequest<{ content: AnnouncementItem[] }>("/announcements?size=3").catch(() => ({ content: [] })),
          apiRequest<{ unreadCount: number }>("/notifications/unread-count").catch(() => ({ unreadCount: 0 })),
        ]);
        setProfile(profData);
        setHoursSummary(hoursData);
        setUpcomingEvents(eventsData.content || []);
        setAnnouncements(annData.content || []);
        setUnreadNotifications(notifData.unreadCount || 0);
      } finally {
        setLoading(false);
      }
    }
    loadData();
  }, []);

  const approvedHours = hoursSummary?.totalApprovedHours || 0;
  const milestone120Percent = Math.min(100, Math.round((approvedHours / 120) * 100));
  const certificate240Percent = Math.min(100, Math.round((approvedHours / 240) * 100));

  if (loading) {
    return <p className="loading-state">Loading volunteer dashboard...</p>;
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>
      {/* Header Banner */}
      <div className="page-header">
        <div>
          <div className="breadcrumbs">
            <span>Raghu Engineering College</span>
            <span>/</span>
            <span className="current">Volunteer Portal</span>
          </div>
          <h1>Welcome, {user?.name}</h1>
          <p className="subtitle">
            College ID: <strong>{profile?.collegeId || "\u2014"}</strong> &bull; Department: <strong>{profile?.department || "Unassigned"}</strong> &bull; Unit: <strong>{profile?.activeUnitName ? `${profile.activeUnitName} (Unit ${profile.activeUnitNumber})` : "Unassigned"}</strong>
          </p>
        </div>
        <div className="card-actions">
          <Link to="/events" className="btn-primary">
            Browse Events
          </Link>
          <Link to="/attendance" className="btn-secondary">
            Check-In to Event
          </Link>
          <Link to="/service-hours" className="btn-secondary">
            Submit Claim
          </Link>
        </div>
      </div>

      {/* 120-Hour Accreditation & Degree Milestone Progress */}
      <div className="section-card" style={{ background: "linear-gradient(135deg, #ffffff 0%, #f0fdf4 100%)", border: "1px solid #bbf7d0" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", flexWrap: "wrap", gap: "1rem", marginBottom: "1rem" }}>
          <div>
            <span className="badge badge-success" style={{ marginBottom: "0.5rem" }}>Annual Accreditation Milestone</span>
            <h2 style={{ margin: "0.25rem 0", color: "#166534" }}>NSS Service Hours Progress</h2>
            <p style={{ margin: 0, color: "#475569", fontSize: "0.9rem" }}>
              Standard annual certification requires 120 verified community service hours (240 hours over 2 academic years for University NSS Certificate).
            </p>
          </div>
          <div style={{ textAlign: "right" }}>
            <span style={{ fontSize: "2.25rem", fontWeight: 800, color: "#16a34a" }}>{approvedHours}</span>
            <span style={{ fontSize: "1.1rem", color: "#64748b", fontWeight: 600 }}> / 120 hrs</span>
            <div style={{ fontSize: "0.8rem", color: "#64748b" }}>{milestone120Percent}% of annual target completed</div>
          </div>
        </div>

        {/* Progress Bar */}
        <div style={{ height: "10px", backgroundColor: "#e2e8f0", borderRadius: "9999px", overflow: "hidden", marginBottom: "0.75rem" }}>
          <div
            style={{
              width: `${milestone120Percent}%`,
              height: "100%",
              backgroundColor: milestone120Percent >= 100 ? "#16a34a" : "#22c55e",
              borderRadius: "9999px",
              transition: "width 0.4s ease-in-out",
            }}
          />
        </div>

        <div style={{ display: "flex", justifyContent: "space-between", fontSize: "0.8rem", color: "#64748b" }}>
          <span>Overall 2-Year University Certificate: {approvedHours} / 240 hrs ({certificate240Percent}%)</span>
          <span>Pending Audit Claims: {hoursSummary?.pendingCount || 0}</span>
        </div>
      </div>

      {/* Main Grid: Upcoming Events & Recent Verified Attendance */}
      <div className="grid-2-col">
        {/* Upcoming Programmes */}
        <div className="section-card">
          <div className="section-header">
            <div>
              <h3>Upcoming NSS Programmes</h3>
              <p className="subtitle">Enroll early to secure confirmed seats or waitlist priority.</p>
            </div>
            <Link to="/events" className="btn-secondary-sm">
              All Events
            </Link>
          </div>

          {upcomingEvents.length === 0 ? (
            <div className="empty-state">
              <p>No open programmes scheduled at this time.</p>
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
              {upcomingEvents.map((ev) => (
                <div
                  key={ev.eventId}
                  style={{
                    padding: "0.85rem 1rem",
                    borderRadius: "0.5rem",
                    border: "1px solid #e2e8f0",
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                    gap: "1rem",
                  }}
                >
                  <div>
                    <strong style={{ display: "block", color: "#0f172a" }}>{ev.title}</strong>
                    <span className="cell-sub">
                      {new Date(ev.startAt).toLocaleDateString()} &bull; {ev.venue}
                    </span>
                  </div>
                  <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
                    <span className="badge badge-primary">{ev.eventType}</span>
                    <Link to={`/events/${ev.eventId}`} className="btn-primary-sm">
                      View
                    </Link>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Recent Attendance / Verified Records */}
        <div className="section-card">
          <div className="section-header">
            <div>
              <h3>Recent Verified Activity</h3>
              <p className="subtitle">Audited service hour entries credited to your transcript.</p>
            </div>
            <Link to="/service-hours" className="btn-secondary-sm">
              View Ledger
            </Link>
          </div>

          {!hoursSummary || hoursSummary.entries.length === 0 ? (
            <div className="empty-state">
              <p>No verified service hours recorded yet.</p>
            </div>
          ) : (
            <div className="table-wrapper">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Programme</th>
                    <th>Hours</th>
                    <th>Status</th>
                    <th>Date</th>
                  </tr>
                </thead>
                <tbody>
                  {hoursSummary.entries.slice(0, 5).map((e) => (
                    <tr key={e.entryId}>
                      <td>
                        <strong>{e.eventTitle || e.description}</strong>
                      </td>
                      <td>
                        <strong style={{ color: "#1e3a8a" }}>+{e.hours}h</strong>
                      </td>
                      <td>
                        <span className={`badge ${e.status === "APPROVED" ? "badge-success" : "badge-warning"}`}>
                          {e.status}
                        </span>
                      </td>
                      <td>
                        <span className="cell-sub">{new Date(e.createdAt).toLocaleDateString()}</span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>

      {/* Announcements & Notices */}
      <div className="section-card">
        <div className="section-header">
          <div>
            <h3>Announcements &amp; Institutional Notices</h3>
            <p className="subtitle">Directives from Faculty Coordinator and Programme Officers.</p>
          </div>
          <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
            {unreadNotifications > 0 && (
              <span className="badge badge-danger">
                {unreadNotifications} Unread Notifications
              </span>
            )}
            <Link to="/announcements" className="btn-secondary-sm">
              Notice Board
            </Link>
          </div>
        </div>

        {announcements.length === 0 ? (
          <p className="empty-state">No announcements published.</p>
        ) : (
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))", gap: "1rem" }}>
            {announcements.map((ann) => (
              <div
                key={ann.announcementId}
                style={{
                  padding: "1rem",
                  borderRadius: "0.5rem",
                  border: "1px solid #e2e8f0",
                  backgroundColor: ann.priority === "URGENT" ? "#fef2f2" : "#f8fafc",
                }}
              >
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "0.5rem" }}>
                  <span
                    className={`badge ${
                      ann.priority === "URGENT"
                        ? "badge-danger"
                        : ann.priority === "HIGH"
                        ? "badge-warning"
                        : "badge-primary"
                    }`}
                  >
                    {ann.priority}
                  </span>
                  <span className="cell-sub">{new Date(ann.publishedAt).toLocaleDateString()}</span>
                </div>
                <strong style={{ display: "block", marginBottom: "0.35rem" }}>{ann.title}</strong>
                <p style={{ margin: 0, fontSize: "0.85rem", color: "#475569", lineHeight: "1.4" }}>
                  {ann.content.length > 140 ? `${ann.content.substring(0, 140)}...` : ann.content}
                </p>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
