import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { apiRequest } from "../../api/client";

interface VolunteerSummary {
  volunteerId: string;
  name: string;
  collegeId: string;
  department: string;
  yearOfStudy: number;
  status: string;
}

interface EventItem {
  eventId: string;
  title: string;
  eventType: string;
  startAt: string;
  venue: string;
  status: string;
}

interface AnnouncementItem {
  announcementId: string;
  title: string;
  content: string;
  priority: string;
  publishedAt: string;
}

export const StudentLeaderDashboard: React.FC = () => {
  const { user } = useAuth();
  const [unitInfo, setUnitInfo] = useState<{ unitId: string | null; unitName: string | null; unitNumber: string | null }>({
    unitId: null,
    unitName: null,
    unitNumber: null,
  });
  const [volunteers, setVolunteers] = useState<VolunteerSummary[]>([]);
  const [events, setEvents] = useState<EventItem[]>([]);
  const [announcements, setAnnouncements] = useState<AnnouncementItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function loadData() {
      setLoading(true);
      try {
        const [summaryData, volsData, eventsData, annData] = await Promise.all([
          apiRequest<any>("/dashboard/summary").catch(() => null),
          apiRequest<{ content: VolunteerSummary[] }>("/volunteers?size=5").catch(() => ({ content: [] })),
          apiRequest<{ content: EventItem[] }>("/events?size=4").catch(() => ({ content: [] })),
          apiRequest<{ content: AnnouncementItem[] }>("/announcements?size=3").catch(() => ({ content: [] })),
        ]);

        if (summaryData?.volunteerData) {
          const vd = summaryData.volunteerData;
          setUnitInfo({
            unitId: vd.activeUnitId || null,
            unitName: vd.activeUnitName || null,
            unitNumber: vd.activeUnitNumber || null,
          });
        }

        const volList = Array.isArray(volsData)
          ? volsData
          : Array.isArray((volsData as any)?.content)
          ? (volsData as any).content
          : [];
        setVolunteers(volList);

        const eventList = Array.isArray(eventsData)
          ? eventsData
          : Array.isArray((eventsData as any)?.content)
          ? (eventsData as any).content
          : [];
        setEvents(eventList);

        const annList = Array.isArray(annData)
          ? annData
          : Array.isArray((annData as any)?.content)
          ? (annData as any).content
          : [];
        setAnnouncements(annList);
      } finally {
        setLoading(false);
      }
    }
    loadData();
  }, []);

  if (loading) {
    return <p className="loading-state">Loading student leadership workspace...</p>;
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>
      {/* Header */}
      <div className="page-header">
        <div>
          <div className="breadcrumbs">
            <span>Raghu Engineering College</span>
            <span>/</span>
            <span className="current">Student Leadership Console</span>
          </div>
          <h1>{unitInfo.unitName ? `${unitInfo.unitName} (Unit ${unitInfo.unitNumber})` : "Unit Leadership Workspace"}</h1>
          <p className="subtitle">
            Welcome back, <strong>{user?.name}</strong> &bull; Supporting unit coordination and attendance operations
          </p>
        </div>
        <div className="card-actions">
          <Link to="/attendance" className="btn-primary">
            Attendance Assistance
          </Link>
          <Link to="/volunteers" className="btn-secondary">
            Unit Volunteers
          </Link>
        </div>
      </div>

      {/* Quick Status Cards */}
      <div className="stats-grid">
        <div className="stat-card">
          <div className="stat-label">Assigned Unit</div>
          <div className="stat-value" style={{ fontSize: "1.25rem" }}>
            {unitInfo.unitNumber ? `Unit ${unitInfo.unitNumber}` : "Assigned"}
          </div>
          <p className="stat-meta">{unitInfo.unitName || "REC NSS Unit"}</p>
        </div>
        <div className="stat-card">
          <div className="stat-label">Active Volunteers</div>
          <div className="stat-value">{volunteers.length > 0 ? `${volunteers.length}+` : "Enrolled"}</div>
          <p className="stat-meta">In your assigned unit</p>
        </div>
        <div className="stat-card">
          <div className="stat-label">Upcoming Programmes</div>
          <div className="stat-value">{events.length}</div>
          <p className="stat-meta">Scheduled activities</p>
        </div>
        <div className="stat-card">
          <div className="stat-label">Recent Notices</div>
          <div className="stat-value">{announcements.length}</div>
          <p className="stat-meta">Campus broadcasts</p>
        </div>
      </div>

      {/* Two-Column Workspace Layout */}
      <div className="dashboard-grid">
        {/* Left Column: Events & Attendance Operations */}
        <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>
          <div className="card">
            <div className="card-header" style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <h2>Upcoming Unit Activities</h2>
              <Link to="/events" className="btn-link">View all</Link>
            </div>
            {events.length === 0 ? (
              <p className="empty-state">No upcoming activities scheduled at this time.</p>
            ) : (
              <div className="activity-list">
                {events.map((ev) => (
                  <div key={ev.eventId} className="activity-item" style={{ padding: "0.75rem 0", borderBottom: "1px solid var(--border-color, #e5e7eb)" }}>
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                      <div>
                        <strong style={{ fontSize: "0.95rem" }}>{ev.title}</strong>
                        <div style={{ fontSize: "0.8rem", color: "var(--text-secondary, #6b7280)", marginTop: "0.2rem" }}>
                          {new Date(ev.startAt).toLocaleDateString()} &bull; {ev.venue || "Campus Venue"}
                        </div>
                      </div>
                      <span className="badge badge-info">{ev.eventType}</span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="card">
            <div className="card-header" style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <h2>Attendance Operations</h2>
              <Link to="/attendance" className="btn-link">Launch Scanner</Link>
            </div>
            <p style={{ fontSize: "0.875rem", color: "var(--text-secondary, #6b7280)", marginBottom: "1rem" }}>
              Assist Programme Officers with on-site volunteer attendance verification and entry checks during active camps and campus events.
            </p>
            <div style={{ display: "flex", gap: "0.75rem" }}>
              <Link to="/attendance" className="btn-secondary" style={{ flex: 1, textAlign: "center" }}>
                Verify Attendance
              </Link>
              <Link to="/service-hours" className="btn-secondary" style={{ flex: 1, textAlign: "center" }}>
                Service Activity
              </Link>
            </div>
          </div>
        </div>

        {/* Right Column: Volunteers & Announcements */}
        <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>
          <div className="card">
            <div className="card-header" style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <h2>Unit Volunteers</h2>
              <Link to="/volunteers" className="btn-link">Directory</Link>
            </div>
            {volunteers.length === 0 ? (
              <p className="empty-state">No volunteer records retrieved.</p>
            ) : (
              <div className="volunteer-list">
                {volunteers.map((vol) => (
                  <div key={vol.volunteerId} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "0.6rem 0", borderBottom: "1px solid var(--border-color, #e5e7eb)" }}>
                    <div>
                      <div style={{ fontWeight: 600, fontSize: "0.9rem" }}>{vol.name}</div>
                      <div style={{ fontSize: "0.75rem", color: "var(--text-secondary, #6b7280)" }}>
                        {vol.collegeId} &bull; {vol.department} (Yr {vol.yearOfStudy})
                      </div>
                    </div>
                    <span className="badge badge-success">{vol.status}</span>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="card">
            <div className="card-header" style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <h2>Announcements</h2>
              <Link to="/announcements" className="btn-link">All notices</Link>
            </div>
            {announcements.length === 0 ? (
              <p className="empty-state">No active announcements.</p>
            ) : (
              <div className="announcement-list">
                {announcements.map((ann) => (
                  <div key={ann.announcementId} style={{ padding: "0.6rem 0", borderBottom: "1px solid var(--border-color, #e5e7eb)" }}>
                    <div style={{ fontWeight: 600, fontSize: "0.875rem" }}>{ann.title}</div>
                    <div style={{ fontSize: "0.75rem", color: "var(--text-secondary, #6b7280)", marginTop: "0.2rem" }}>
                      {new Date(ann.publishedAt).toLocaleDateString()}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
