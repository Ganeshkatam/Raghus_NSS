import React, { useEffect, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { apiRequest, ApiError } from "../api/client";

interface EventItem {
  eventId: string;
  unitId: string;
  unitName: string;
  title: string;
  description: string | null;
  eventType: string;
  startAt: string;
  endAt: string;
  registrationOpenAt: string | null;
  registrationCloseAt: string | null;
  venue: string;
  capacity: number;
  registeredCount: number;
  remainingCapacity: number;
  status: string;
}

interface Registration {
  registrationId: string;
  volunteerId: string;
  volunteerName: string;
  collegeId: string;
  status: string;
  waitlistPosition?: number | null;
  cancellationReason?: string | null;
  registeredAt: string;
}

interface Volunteer {
  volunteerId: string;
  name: string;
  status: string;
  activeUnitId: string | null;
}

interface EventStats {
  eventId: string;
  title: string;
  capacity: number;
  registeredCount: number;
  waitlistCount: number;
  presentCount: number;
  absentCount: number;
  attendanceRatePercentage: number;
}

interface ActiveSession {
  sessionId: string;
  eventId: string;
  eventTitle: string;
  openedByName: string;
  startsAt: string;
  expiresAt: string;
  status: string;
  qrToken: string;
  presentCount: number;
  totalRegistered: number;
}

interface AttendanceRosterItem {
  volunteerId: string;
  rollNumber: string;
  fullName: string;
  department: string;
  nssUnitCode: string;
  registrationStatus: string;
  attendanceStatus: string;
  checkInMethod: string;
  checkedInAt: string | null;
  attendanceId: string | null;
}

type TabType = "overview" | "registration" | "attendance" | "serviceHours" | "statistics";

const transitions: Record<string, string[]> = {
  DRAFT: ["publish", "cancel"],
  PUBLISHED: ["open", "cancel"],
  OPEN: ["close", "cancel"],
  CLOSED: ["complete", "cancel"],
};

export const EventDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const activeTab = (searchParams.get("tab") as TabType) || "overview";
  const setActiveTab = (tab: TabType) => setSearchParams({ tab });

  const { isCoordinatorOrOfficer } = useAuth();
  const [event, setEvent] = useState<EventItem | null>(null);
  const [stats, setStats] = useState<EventStats | null>(null);
  const [registrations, setRegistrations] = useState<Registration[]>([]);
  const [volunteer, setVolunteer] = useState<Volunteer | null>(null);
  const [myRegistration, setMyRegistration] = useState<Registration | null>(null);
  const [activeSession, setActiveSession] = useState<ActiveSession | null>(null);
  const [roster, setRoster] = useState<AttendanceRosterItem[]>([]);

  // Registration sub-filter
  const [regFilter, setRegFilter] = useState<"ALL" | "CONFIRMED" | "WAITLIST" | "CANCELLED">("ALL");
  const [regSearch, setRegSearch] = useState("");

  // Cancellation modal state
  const [showCancelModal, setShowCancelModal] = useState(false);
  const [cancelCategory, setCancelCategory] = useState("Academic schedule conflict");
  const [cancelRemarks, setCancelRemarks] = useState("");
  const [cancelling, setCancelling] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);

  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    if (!id) return;
    setLoading(true);
    setError(null);
    try {
      const e = await apiRequest<EventItem>("/events/" + id);
      setEvent(e);

      try {
        const s = await apiRequest<EventStats>("/events/" + id + "/stats");
        setStats(s);
      } catch {
        // Non-blocking
      }

      if (isCoordinatorOrOfficer) {
        try {
          const regList = await apiRequest<Registration[]>("/events/" + id + "/registrations");
          setRegistrations(regList || []);
        } catch {
          // Non-blocking
        }
        try {
          const sess = await apiRequest<ActiveSession>("/events/" + id + "/attendance/sessions/active");
          setActiveSession(sess);
        } catch {
          setActiveSession(null);
        }
        try {
          const ros = await apiRequest<AttendanceRosterItem[]>("/events/" + id + "/attendance/roster");
          setRoster(ros || []);
        } catch {
          // Non-blocking
        }
      } else {
        try {
          const v = await apiRequest<Volunteer>("/volunteers/me");
          setVolunteer(v);
          try {
            const myReg = await apiRequest<Registration>("/events/" + id + "/registrations/me");
            setMyRegistration(myReg);
          } catch {
            setMyRegistration(null);
          }
        } catch {
          // Non-blocking
        }
      }
    } catch (e) {
      const err = e as ApiError;
      setError(err.message || "Failed to load event.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, [id, isCoordinatorOrOfficer]);

  const transition = async (action: string) => {
    if (!id) return;
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      await apiRequest("/events/" + id + "/" + action, { method: "POST" });
      setMessage("Event transition applied: " + action);
      await load();
    } catch (e) {
      const err = e as ApiError;
      setError(err.message || "Unable to " + action + " event.");
    } finally {
      setBusy(false);
    }
  };

  const register = async (joinWaitlist: boolean = false) => {
    if (!id || !volunteer) return;
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const endpoint = joinWaitlist ? "/events/" + id + "/waitlist" : "/events/" + id + "/registrations";
      await apiRequest(endpoint, {
        method: "POST",
        body: JSON.stringify({ volunteerId: volunteer.volunteerId, joinWaitlist }),
      });
      setMessage(joinWaitlist ? "Added to event waitlist." : "Registration confirmed.");
      await load();
    } catch (e) {
      const err = e as ApiError;
      setError(err.message || "Registration failed.");
    } finally {
      setBusy(false);
    }
  };

  const handleCancelRegistration = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!id) return;
    setCancelling(true);
    setCancelError(null);
    try {
      const finalReason = cancelRemarks.trim()
        ? `${cancelCategory}: ${cancelRemarks.trim()}`
        : cancelCategory;
      await apiRequest("/events/" + id + "/registrations", {
        method: "DELETE",
        body: JSON.stringify({ reason: finalReason }),
      });
      setShowCancelModal(false);
      setMessage(
        "Registration cancelled successfully. A notification has been recorded and any waitlisted volunteer promoted."
      );
      setCancelRemarks("");
      await load();
    } catch (e) {
      const err = e as ApiError;
      setCancelError(err.message || "Unable to cancel registration.");
    } finally {
      setCancelling(false);
    }
  };

  const cloneEvent = async () => {
    if (!id) return;
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const res = await apiRequest<EventItem>("/events/" + id + "/clone", { method: "POST" });
      setMessage("Event cloned successfully as DRAFT: " + res.title);
    } catch (e) {
      const err = e as ApiError;
      setError(err.message || "Unable to clone event.");
    } finally {
      setBusy(false);
    }
  };

  const closeActiveSession = async () => {
    if (!activeSession) return;
    setBusy(true);
    try {
      await apiRequest(`/attendance/sessions/${activeSession.sessionId}/close`, { method: "POST" });
      setMessage("Attendance session closed.");
      await load();
    } catch (e) {
      const err = e as ApiError;
      setError(err.message || "Failed to close attendance session.");
    } finally {
      setBusy(false);
    }
  };

  if (loading) {
    return (
      <div className="page-container">
        <p className="loading-state">Loading event details...</p>
      </div>
    );
  }

  if (error && !event) {
    return (
      <div className="page-container">
        <div className="alert alert-error">
          <strong>Error:</strong> {error}
        </div>
        <Link className="btn-secondary" to="/events">
          Back to Events
        </Link>
      </div>
    );
  }

  if (!event) return null;

  const actions = transitions[event.status] || [];
  const volunteerIsEligible =
    !!volunteer && volunteer.status === "ACTIVE" && volunteer.activeUnitId === event.unitId;

  // Window evaluation
  const now = new Date();
  const isWindowOpen =
    event.status === "OPEN" &&
    (!event.registrationOpenAt || new Date(event.registrationOpenAt) <= now) &&
    (!event.registrationCloseAt || new Date(event.registrationCloseAt) >= now);

  const windowLabel =
    event.status === "CLOSED" || event.status === "COMPLETED" || event.status === "CANCELLED"
      ? "Registration Closed"
      : event.status === "DRAFT"
        ? "Draft - Window Not Opened"
        : event.status === "PUBLISHED"
          ? event.registrationOpenAt && new Date(event.registrationOpenAt) > now
            ? `Opens ${new Date(event.registrationOpenAt).toLocaleString()}`
            : "Not Opened"
          : isWindowOpen
            ? "Registration Window Open"
            : "Registration Closed";

  // Filtered registrations
  const filteredRegistrations = registrations.filter((r) => {
    if (regFilter === "CONFIRMED" && r.status !== "CONFIRMED" && r.status !== "REGISTERED") {
      return false;
    }
    if (regFilter === "WAITLIST" && r.status !== "WAITLISTED") {
      return false;
    }
    if (regFilter === "CANCELLED" && r.status !== "CANCELLED") {
      return false;
    }
    if (!regSearch.trim()) return true;
    const q = regSearch.toLowerCase();
    return r.volunteerName.toLowerCase().includes(q) || r.collegeId.toLowerCase().includes(q);
  });

  const confirmedCount = registrations.filter(
    (r) => r.status === "CONFIRMED" || r.status === "REGISTERED"
  ).length;
  const waitlistCount = registrations.filter((r) => r.status === "WAITLISTED").length;
  const cancelledCount = registrations.filter((r) => r.status === "CANCELLED").length;

  // Event Duration calculation in hours
  const startMs = new Date(event.startAt).getTime();
  const endMs = new Date(event.endAt).getTime();
  const durationHours = Math.max(0.5, Math.round(((endMs - startMs) / (1000 * 60 * 60)) * 10) / 10);

  return (
    <div className="page-container">
      <Link to="/events" className="back-link">
        &larr; Back to Events
      </Link>

      <div className="page-header" style={{ alignItems: "flex-start", gap: "1rem" }}>
        <div>
          <div style={{ display: "flex", gap: "0.5rem", alignItems: "center", marginBottom: "0.5rem" }}>
            <span className="badge badge-primary">{event.eventType}</span>
            <span className="badge badge-muted">{event.status}</span>
            <span
              style={{
                fontSize: "0.8rem",
                fontWeight: 600,
                padding: "0.2rem 0.6rem",
                borderRadius: "6px",
                backgroundColor: isWindowOpen ? "#dcfce7" : "#f1f5f9",
                color: isWindowOpen ? "#15803d" : "#475569",
              }}
            >
              {windowLabel}
            </span>
          </div>
          <h1 style={{ marginBottom: "0.25rem" }}>{event.title}</h1>
          <p className="subtitle">
            {event.unitName} &bull; Venue: {event.venue}
          </p>
        </div>

        {isCoordinatorOrOfficer && (
          <div className="card-actions" style={{ marginTop: "0.5rem" }}>
            <button className="btn-secondary-sm" disabled={busy} onClick={cloneEvent}>
              Clone Event
            </button>
            {actions.map((a) => (
              <button
                key={a}
                className={a === "cancel" ? "btn-danger-sm" : "btn-primary-sm"}
                disabled={busy}
                onClick={() => transition(a)}
              >
                {a === "publish"
                  ? "Publish"
                  : a === "open"
                    ? "Open Registration"
                    : a === "close"
                      ? "Close Registration"
                      : a === "complete"
                        ? "Mark Completed"
                        : "Cancel Event"}
              </button>
            ))}
          </div>
        )}
      </div>

      {error && (
        <div className="alert alert-error">
          <strong>Error:</strong> {error}
        </div>
      )}
      {message && (
        <div className="alert alert-success">
          <strong>Notice:</strong> {message}
        </div>
      )}

      {/* Navigation Tabs */}
      <div
        style={{
          display: "flex",
          gap: "0.5rem",
          borderBottom: "1px solid #e2e8f0",
          marginBottom: "1.5rem",
          overflowX: "auto",
        }}
      >
        <button
          type="button"
          onClick={() => setActiveTab("overview")}
          style={{
            padding: "0.75rem 1.25rem",
            border: "none",
            background: "none",
            borderBottom: activeTab === "overview" ? "2px solid #1e40af" : "2px solid transparent",
            color: activeTab === "overview" ? "#1e40af" : "#64748b",
            fontWeight: activeTab === "overview" ? 700 : 500,
            fontSize: "0.95rem",
            cursor: "pointer",
            whiteSpace: "nowrap",
          }}
        >
          Overview
        </button>
        <button
          type="button"
          onClick={() => setActiveTab("registration")}
          style={{
            padding: "0.75rem 1.25rem",
            border: "none",
            background: "none",
            borderBottom:
              activeTab === "registration" ? "2px solid #1e40af" : "2px solid transparent",
            color: activeTab === "registration" ? "#1e40af" : "#64748b",
            fontWeight: activeTab === "registration" ? 700 : 500,
            fontSize: "0.95rem",
            cursor: "pointer",
            whiteSpace: "nowrap",
          }}
        >
          Registration {isCoordinatorOrOfficer ? `(${registrations.length})` : ""}
        </button>
        <button
          type="button"
          onClick={() => setActiveTab("attendance")}
          style={{
            padding: "0.75rem 1.25rem",
            border: "none",
            background: "none",
            borderBottom:
              activeTab === "attendance" ? "2px solid #1e40af" : "2px solid transparent",
            color: activeTab === "attendance" ? "#1e40af" : "#64748b",
            fontWeight: activeTab === "attendance" ? 700 : 500,
            fontSize: "0.95rem",
            cursor: "pointer",
            whiteSpace: "nowrap",
          }}
        >
          Attendance
        </button>
        <button
          type="button"
          onClick={() => setActiveTab("serviceHours")}
          style={{
            padding: "0.75rem 1.25rem",
            border: "none",
            background: "none",
            borderBottom:
              activeTab === "serviceHours" ? "2px solid #1e40af" : "2px solid transparent",
            color: activeTab === "serviceHours" ? "#1e40af" : "#64748b",
            fontWeight: activeTab === "serviceHours" ? 700 : 500,
            fontSize: "0.95rem",
            cursor: "pointer",
            whiteSpace: "nowrap",
          }}
        >
          Service Hours
        </button>
        <button
          type="button"
          onClick={() => setActiveTab("statistics")}
          style={{
            padding: "0.75rem 1.25rem",
            border: "none",
            background: "none",
            borderBottom:
              activeTab === "statistics" ? "2px solid #1e40af" : "2px solid transparent",
            color: activeTab === "statistics" ? "#1e40af" : "#64748b",
            fontWeight: activeTab === "statistics" ? 700 : 500,
            fontSize: "0.95rem",
            cursor: "pointer",
            whiteSpace: "nowrap",
          }}
        >
          Statistics
        </button>
      </div>

      {/* TAB 1: OVERVIEW */}
      {activeTab === "overview" && (
        <div className="detail-grid">
          <section className="section-card">
            <h3>Event Overview</h3>
            <p>{event.description || "No description provided."}</p>
            <dl className="detail-list" style={{ marginTop: "1rem" }}>
              <div>
                <dt>Starts</dt>
                <dd>{new Date(event.startAt).toLocaleString()}</dd>
              </div>
              <div>
                <dt>Ends</dt>
                <dd>{new Date(event.endAt).toLocaleString()}</dd>
              </div>
              <div>
                <dt>Registration Opens</dt>
                <dd>
                  {event.registrationOpenAt
                    ? new Date(event.registrationOpenAt).toLocaleString()
                    : "Immediately"}
                </dd>
              </div>
              <div>
                <dt>Registration Closes</dt>
                <dd>
                  {event.registrationCloseAt
                    ? new Date(event.registrationCloseAt).toLocaleString()
                    : "No explicit close time"}
                </dd>
              </div>
              <div>
                <dt>Venue</dt>
                <dd>{event.venue}</dd>
              </div>
              <div>
                <dt>Capacity</dt>
                <dd>
                  {event.registeredCount}/{event.capacity} registered &mdash;{" "}
                  {event.remainingCapacity} remaining
                </dd>
              </div>
              {stats && (
                <div>
                  <dt>Waitlist Queue</dt>
                  <dd>{stats.waitlistCount} volunteers queued</dd>
                </div>
              )}
            </dl>

            <div style={{ marginTop: "1.25rem" }}>
              <div
                style={{
                  display: "flex",
                  justifyContent: "space-between",
                  fontSize: "0.85rem",
                  marginBottom: "0.35rem",
                }}
              >
                <span>Capacity Saturation</span>
                <span>
                  {Math.round((event.registeredCount / Math.max(1, event.capacity)) * 100)}%
                </span>
              </div>
              <div className="capacity-bar">
                <span
                  style={{
                    width:
                      Math.min(100, (event.registeredCount / Math.max(1, event.capacity)) * 100) +
                      "%",
                    backgroundColor:
                      event.registeredCount >= event.capacity ? "#d97706" : "#2563eb",
                  }}
                />
              </div>
            </div>
          </section>

          {/* Volunteer Registration State Block */}
          {!isCoordinatorOrOfficer && (
            <section className="section-card">
              <h3>Your Registration</h3>
              {!volunteer ? (
                <p>Loading your volunteer profile...</p>
              ) : myRegistration &&
                (myRegistration.status === "REGISTERED" ||
                  myRegistration.status === "CONFIRMED") ? (
                <div>
                  <div
                    style={{
                      display: "flex",
                      alignItems: "center",
                      gap: "0.5rem",
                      marginBottom: "1rem",
                    }}
                  >
                    <span className="badge badge-success" style={{ fontWeight: 700 }}>
                      CONFIRMED
                    </span>
                    <span style={{ fontSize: "0.9rem", color: "#16a34a", fontWeight: 600 }}>
                      Registered on {new Date(myRegistration.registeredAt).toLocaleDateString()}
                    </span>
                  </div>
                  <p style={{ fontSize: "0.9rem", color: "#475569", marginBottom: "1.25rem" }}>
                    Your seat is secured for this NSS event. Please remember to arrive at the venue
                    on time for attendance check-in.
                  </p>
                  {event.status === "OPEN" && (
                    <button
                      className="btn-danger-sm"
                      disabled={busy}
                      onClick={() => setShowCancelModal(true)}
                    >
                      Cancel Registration
                    </button>
                  )}
                </div>
              ) : myRegistration && myRegistration.status === "WAITLISTED" ? (
                <div>
                  <div
                    style={{
                      display: "flex",
                      alignItems: "center",
                      gap: "0.5rem",
                      marginBottom: "1rem",
                    }}
                  >
                    <span className="badge badge-warning" style={{ fontWeight: 700 }}>
                      WAITLISTED #{myRegistration.waitlistPosition || 1}
                    </span>
                  </div>
                  <p style={{ fontSize: "0.9rem", color: "#475569", marginBottom: "1.25rem" }}>
                    You are currently queued in the automated waitlist. If a confirmed participant
                    cancels, you will automatically be promoted to Confirmed status and receive a
                    system notification.
                  </p>
                  {event.status === "OPEN" && (
                    <button
                      className="btn-danger-sm"
                      disabled={busy}
                      onClick={() => setShowCancelModal(true)}
                    >
                      Leave Waitlist
                    </button>
                  )}
                </div>
              ) : myRegistration && myRegistration.status === "CANCELLED" ? (
                <div>
                  <div
                    style={{
                      display: "flex",
                      alignItems: "center",
                      gap: "0.5rem",
                      marginBottom: "0.75rem",
                    }}
                  >
                    <span className="badge badge-muted">CANCELLED</span>
                  </div>
                  <p style={{ fontSize: "0.9rem", color: "#64748b", marginBottom: "1rem" }}>
                    Reason: {myRegistration.cancellationReason || "Cancelled by volunteer"}
                  </p>
                  {volunteerIsEligible && isWindowOpen && (
                    <button
                      className="btn-primary-sm"
                      disabled={busy}
                      onClick={() => register(event.remainingCapacity <= 0)}
                    >
                      {event.remainingCapacity > 0 ? "Re-Register for Event" : "Join Waitlist"}
                    </button>
                  )}
                </div>
              ) : volunteerIsEligible ? (
                isWindowOpen ? (
                  event.remainingCapacity > 0 ? (
                    <div>
                      <p style={{ marginBottom: "1rem" }}>
                        Seats are available. Click below to register for this event.
                      </p>
                      <button className="btn-primary" disabled={busy} onClick={() => register(false)}>
                        Register for Event
                      </button>
                    </div>
                  ) : (
                    <div>
                      <p style={{ color: "#d97706", fontWeight: 600, marginBottom: "0.5rem" }}>
                        Event is currently at maximum capacity.
                      </p>
                      <p style={{ fontSize: "0.9rem", color: "#475569", marginBottom: "1rem" }}>
                        You can join the FIFO waitlist. If an existing attendee cancels, you will be
                        promoted automatically.
                      </p>
                      <button className="btn-secondary" disabled={busy} onClick={() => register(true)}>
                        Join Event Waitlist
                      </button>
                    </div>
                  )
                ) : (
                  <p style={{ color: "#64748b" }}>{windowLabel}</p>
                )
              ) : (
                <p style={{ color: "#dc2626" }}>
                  You must be an active enrolled member of {event.unitName} to register.
                </p>
              )}
            </section>
          )}

          {isCoordinatorOrOfficer && (
            <section className="section-card">
              <h3>Officer Control Summary</h3>
              <p style={{ fontSize: "0.9rem", color: "#475569", marginBottom: "1rem" }}>
                Manage registration lifecycle, capacity, attendance sessions, and event status.
              </p>
              <div style={{ display: "flex", flexDirection: "column", gap: "0.5rem" }}>
                <Link
                  to={`/events/${id}?tab=registration`}
                  className="table-action-link"
                  style={{ fontWeight: 600 }}
                >
                  View Registration List ({registrations.length} total)
                </Link>
                <Link
                  to={`/events/${id}?tab=attendance`}
                  className="table-action-link"
                  style={{ fontWeight: 600 }}
                >
                  Manage Attendance List &amp; Sessions
                </Link>
                <Link
                  to={`/events/${id}?tab=statistics`}
                  className="table-action-link"
                  style={{ fontWeight: 600 }}
                >
                  View Real-Time Statistics &amp; Metrics
                </Link>
              </div>
            </section>
          )}
        </div>
      )}

      {/* TAB 2: REGISTRATION */}
      {activeTab === "registration" && (
        <div>
          {isCoordinatorOrOfficer ? (
            <section className="section-card">
              <div
                className="section-header"
                style={{
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "center",
                  flexWrap: "wrap",
                  gap: "0.75rem",
                  marginBottom: "1rem",
                }}
              >
                <div>
                  <h3 style={{ margin: 0 }}>Registered Volunteers List</h3>
                  <p style={{ margin: 0, fontSize: "0.85rem", color: "#64748b" }}>
                    {confirmedCount} Confirmed &bull; {waitlistCount} Waitlisted &bull;{" "}
                    {cancelledCount} Cancelled
                  </p>
                </div>

                {/* Sub filter pills */}
                <div style={{ display: "flex", gap: "0.4rem", flexWrap: "wrap" }}>
                  {(["ALL", "CONFIRMED", "WAITLIST", "CANCELLED"] as const).map((filter) => (
                    <button
                      key={filter}
                      type="button"
                      onClick={() => setRegFilter(filter)}
                      style={{
                        padding: "0.3rem 0.75rem",
                        borderRadius: "9999px",
                        border: regFilter === filter ? "1px solid #1e40af" : "1px solid #cbd5e1",
                        backgroundColor: regFilter === filter ? "#1e40af" : "#ffffff",
                        color: regFilter === filter ? "#ffffff" : "#475569",
                        fontSize: "0.8rem",
                        fontWeight: regFilter === filter ? 700 : 500,
                        cursor: "pointer",
                      }}
                    >
                      {filter}
                    </button>
                  ))}
                </div>
              </div>

              {/* Roster Search */}
              <div style={{ marginBottom: "1rem" }}>
                <input
                  type="text"
                  placeholder="Filter roster by volunteer name or college ID..."
                  value={regSearch}
                  onChange={(e) => setRegSearch(e.target.value)}
                  style={{ width: "100%", maxWidth: "400px" }}
                />
              </div>

              {filteredRegistrations.length === 0 ? (
                <div className="empty-state">
                  <p>No volunteer records match the selected registration filter.</p>
                </div>
              ) : (
                <div className="units-table-wrapper">
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th>College ID</th>
                        <th>Name</th>
                        <th>Status</th>
                        <th>Waitlist #</th>
                        <th>Cancellation Reason</th>
                        <th>Registered Date</th>
                      </tr>
                    </thead>
                    <tbody>
                      {filteredRegistrations.map((r) => (
                        <tr key={r.registrationId}>
                          <td style={{ fontWeight: 600 }}>{r.collegeId}</td>
                          <td>{r.volunteerName}</td>
                          <td>
                            <span
                              className={`badge ${r.status === "WAITLISTED"
                                ? "badge-warning"
                                : r.status === "CANCELLED"
                                  ? "badge-muted"
                                  : "badge-success"
                                }`}
                            >
                              {r.status}
                            </span>
                          </td>
                          <td>{r.waitlistPosition ? `#${r.waitlistPosition}` : "-"}</td>
                          <td style={{ fontSize: "0.85rem", color: "#64748b" }}>
                            {r.cancellationReason || "-"}
                          </td>
                          <td>{new Date(r.registeredAt).toLocaleString()}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>
          ) : (
            <section className="section-card">
              <h3>Volunteer Registration Summary</h3>
              {myRegistration ? (
                <div style={{ maxWidth: "600px" }}>
                  <div
                    style={{
                      display: "flex",
                      alignItems: "center",
                      gap: "0.5rem",
                      marginBottom: "1rem",
                    }}
                  >
                    <span
                      className={`badge ${myRegistration.status === "WAITLISTED"
                        ? "badge-warning"
                        : myRegistration.status === "CANCELLED"
                          ? "badge-muted"
                          : "badge-success"
                        }`}
                    >
                      {myRegistration.status}
                    </span>
                    {myRegistration.waitlistPosition && (
                      <span style={{ fontWeight: 600, color: "#d97706" }}>
                        Position #{myRegistration.waitlistPosition}
                      </span>
                    )}
                  </div>
                  <dl className="detail-list">
                    <div>
                      <dt>Volunteer</dt>
                      <dd>{volunteer?.name || "Self"}</dd>
                    </div>
                    <div>
                      <dt>Registered Timestamp</dt>
                      <dd>{new Date(myRegistration.registeredAt).toLocaleString()}</dd>
                    </div>
                    {myRegistration.cancellationReason && (
                      <div>
                        <dt>Cancellation Reason</dt>
                        <dd>{myRegistration.cancellationReason}</dd>
                      </div>
                    )}
                  </dl>

                  {event.status === "OPEN" &&
                    (myRegistration.status === "CONFIRMED" ||
                      myRegistration.status === "REGISTERED" ||
                      myRegistration.status === "WAITLISTED") && (
                      <button
                        className="btn-danger-sm"
                        style={{ marginTop: "1rem" }}
                        onClick={() => setShowCancelModal(true)}
                      >
                        Cancel Registration
                      </button>
                    )}
                </div>
              ) : (
                <div>
                  <p>You have not registered for this event yet.</p>
                  <button
                    className="btn-primary-sm"
                    onClick={() => setActiveTab("overview")}
                    style={{ marginTop: "0.5rem" }}
                  >
                    Go to Overview to Register
                  </button>
                </div>
              )}
            </section>
          )}
        </div>
      )}

      {/* TAB 3: ATTENDANCE */}
      {activeTab === "attendance" && (
        <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>
          {isCoordinatorOrOfficer ? (
            <>
              {/* Active Session Card */}
              <section className="section-card">
                <div
                  style={{
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                    flexWrap: "wrap",
                    gap: "1rem",
                  }}
                >
                  <div>
                    <h3 style={{ margin: 0 }}>Attendance Session Status</h3>
                    {activeSession ? (
                      <p style={{ margin: "0.25rem 0 0", color: "#16a34a", fontWeight: 600 }}>
                        Active Session opened by {activeSession.openedByName} &bull; Check-ins:{" "}
                        {activeSession.presentCount} / {activeSession.totalRegistered}
                      </p>
                    ) : (
                      <p style={{ margin: "0.25rem 0 0", color: "#64748b" }}>
                        No attendance session is currently open for this event.
                      </p>
                    )}
                  </div>

                  <div style={{ display: "flex", gap: "0.5rem" }}>
                    <Link to="/attendance" className="btn-secondary-sm">
                      Go to Live Attendance Console
                    </Link>
                    {activeSession && (
                      <button
                        className="btn-danger-sm"
                        disabled={busy}
                        onClick={closeActiveSession}
                      >
                        Close Session
                      </button>
                    )}
                  </div>
                </div>

                {activeSession && (
                  <div
                    style={{
                      marginTop: "1rem",
                      padding: "1rem",
                      backgroundColor: "#f8fafc",
                      borderRadius: "8px",
                      border: "1px solid #e2e8f0",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "space-between",
                    }}
                  >
                    <div>
                      <span style={{ fontSize: "0.8rem", color: "#64748b", textTransform: "uppercase" }}>
                        Session Check-in Token
                      </span>
                      <div
                        style={{
                          fontSize: "1.5rem",
                          fontWeight: 800,
                          letterSpacing: "3px",
                          color: "#1e40af",
                        }}
                      >
                        {activeSession.qrToken}
                      </div>
                    </div>
                    <div style={{ textAlign: "right", fontSize: "0.85rem", color: "#64748b" }}>
                      <div>Expires: {new Date(activeSession.expiresAt).toLocaleTimeString()}</div>
                    </div>
                  </div>
                )}
              </section>

              {/* Roster Table */}
              <section className="section-card">
                <div className="section-header">
                  <h3>Attendance Roster</h3>
                  <span className="results-count">{roster.length} participants</span>
                </div>

                {roster.length === 0 ? (
                  <div className="empty-state">
                    <p>No attendance records logged for this event yet.</p>
                  </div>
                ) : (
                  <div className="units-table-wrapper">
                    <table className="data-table">
                      <thead>
                        <tr>
                          <th>Roll Number</th>
                          <th>Full Name</th>
                          <th>Department</th>
                          <th>Registration</th>
                          <th>Attendance</th>
                          <th>Method</th>
                          <th>Checked In</th>
                        </tr>
                      </thead>
                      <tbody>
                        {roster.map((item) => (
                          <tr key={item.volunteerId}>
                            <td style={{ fontWeight: 600 }}>{item.rollNumber}</td>
                            <td>{item.fullName}</td>
                            <td>{item.department}</td>
                            <td>
                              <span className="badge badge-muted">{item.registrationStatus}</span>
                            </td>
                            <td>
                              <span
                                className={`badge ${item.attendanceStatus === "PRESENT"
                                  ? "badge-success"
                                  : item.attendanceStatus === "ABSENT"
                                    ? "badge-danger"
                                    : "badge-muted"
                                  }`}
                              >
                                {item.attendanceStatus}
                              </span>
                            </td>
                            <td>{item.checkInMethod || "-"}</td>
                            <td>
                              {item.checkedInAt ? new Date(item.checkedInAt).toLocaleTimeString() : "-"}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </section>
            </>
          ) : (
            <section className="section-card">
              <h3>Attendance Verification</h3>
              <p style={{ color: "#475569", marginBottom: "1rem" }}>
                During the event, your Programme Officer will display the attendance QR token on the
                projector. Enter the token in the Attendance Console to record your check-in.
              </p>
              <Link to="/attendance" className="btn-primary-sm">
                Open Check-In Console
              </Link>
            </section>
          )}
        </div>
      )}

      {/* TAB 4: SERVICE HOURS */}
      {activeTab === "serviceHours" && (
        <div className="detail-grid">
          <section className="section-card">
            <h3>NSS Accreditation Credit</h3>
            <p style={{ color: "#475569", marginBottom: "1rem" }}>
              Active participation in this event grants NSS service hour credits toward the 120-hour
              requirement for official institutional certification.
            </p>
            <dl className="detail-list">
              <div>
                <dt>Event Duration</dt>
                <dd>{durationHours} hours</dd>
              </div>
              <div>
                <dt>Activity Classification</dt>
                <dd>{event.eventType}</dd>
              </div>
              <div>
                <dt>Total Participant Potential</dt>
                <dd>{Math.round(event.registeredCount * durationHours)} student-hours</dd>
              </div>
            </dl>
          </section>

          <section className="section-card">
            <h3>Service Hours Logging</h3>
            {isCoordinatorOrOfficer ? (
              <div>
                <p style={{ color: "#475569", marginBottom: "1rem" }}>
                  Programme Officers can review volunteer hours claims and verify accredited logs in
                  the Service Hours Ledger.
                </p>
                <Link to="/service-hours" className="btn-secondary-sm">
                  Open Service Hours Ledger
                </Link>
              </div>
            ) : (
              <div>
                <p style={{ color: "#475569", marginBottom: "1rem" }}>
                  If you attended this event and verified your attendance, ensure your hours are
                  credited or submit a claim if manual adjustment is required.
                </p>
                <Link to="/service-hours" className="btn-primary-sm">
                  View My Service Hours
                </Link>
              </div>
            )}
          </section>
        </div>
      )}

      {/* TAB 5: STATISTICS */}
      {activeTab === "statistics" && (
        <section className="section-card">
          <div className="section-header" style={{ marginBottom: "1.5rem" }}>
            <div>
              <h3 style={{ margin: 0 }}>Event Operational Analytics</h3>
              <p style={{ margin: "0.25rem 0 0", color: "#64748b", fontSize: "0.85rem" }}>
                Real-time turnout, waitlist saturation, and attendance efficiency metrics
              </p>
            </div>
          </div>

          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))",
              gap: "1rem",
              marginBottom: "1.5rem",
            }}
          >
            <div
              style={{
                padding: "1rem",
                borderRadius: "8px",
                border: "1px solid #e2e8f0",
                backgroundColor: "#f8fafc",
              }}
            >
              <div style={{ fontSize: "0.8rem", color: "#64748b", fontWeight: 600 }}>
                Total Registered
              </div>
              <div style={{ fontSize: "1.75rem", fontWeight: 700, color: "#1e40af" }}>
                {stats?.registeredCount ?? event.registeredCount}
              </div>
              <div style={{ fontSize: "0.75rem", color: "#64748b" }}>
                Capacity: {stats?.capacity ?? event.capacity} seats
              </div>
            </div>

            <div
              style={{
                padding: "1rem",
                borderRadius: "8px",
                border: "1px solid #e2e8f0",
                backgroundColor: "#f8fafc",
              }}
            >
              <div style={{ fontSize: "0.8rem", color: "#64748b", fontWeight: 600 }}>
                Waitlist Queue
              </div>
              <div style={{ fontSize: "1.75rem", fontWeight: 700, color: "#d97706" }}>
                {stats?.waitlistCount ?? 0}
              </div>
              <div style={{ fontSize: "0.75rem", color: "#64748b" }}>Automated FIFO queue</div>
            </div>

            <div
              style={{
                padding: "1rem",
                borderRadius: "8px",
                border: "1px solid #e2e8f0",
                backgroundColor: "#f8fafc",
              }}
            >
              <div style={{ fontSize: "0.8rem", color: "#64748b", fontWeight: 600 }}>
                Attended (Present)
              </div>
              <div style={{ fontSize: "1.75rem", fontWeight: 700, color: "#16a34a" }}>
                {stats?.presentCount ?? 0}
              </div>
              <div style={{ fontSize: "0.75rem", color: "#64748b" }}>Verified via token</div>
            </div>

            <div
              style={{
                padding: "1rem",
                borderRadius: "8px",
                border: "1px solid #e2e8f0",
                backgroundColor: "#f8fafc",
              }}
            >
              <div style={{ fontSize: "0.8rem", color: "#64748b", fontWeight: 600 }}>
                Absent Count
              </div>
              <div style={{ fontSize: "1.75rem", fontWeight: 700, color: "#dc2626" }}>
                {stats?.absentCount ?? 0}
              </div>
              <div style={{ fontSize: "0.75rem", color: "#64748b" }}>Registered but missed</div>
            </div>
          </div>

          <div>
            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                fontSize: "0.9rem",
                fontWeight: 600,
                marginBottom: "0.4rem",
              }}
            >
              <span>Attendance Rate</span>
              <span>{(stats?.attendanceRatePercentage ?? 0).toFixed(1)}%</span>
            </div>
            <div className="capacity-bar" style={{ height: "10px" }}>
              <span
                style={{
                  width: `${Math.min(100, Math.max(0, stats?.attendanceRatePercentage ?? 0))}%`,
                  backgroundColor: "#16a34a",
                }}
              />
            </div>
          </div>
        </section>
      )}

      {/* CANCELLATION REASON MODAL */}
      {showCancelModal && (
        <div className="modal-backdrop">
          <div className="modal-card">
            <div className="modal-header">
              <h3>Cancel Event Registration</h3>
              <button className="btn-close" onClick={() => setShowCancelModal(false)}>
                &times;
              </button>
            </div>
            {cancelError && (
              <div className="alert alert-error">
                <strong>Error:</strong> {cancelError}
              </div>
            )}
            <form className="form-stack" onSubmit={handleCancelRegistration}>
              <p style={{ fontSize: "0.9rem", color: "#475569" }}>
                Are you sure you want to cancel your registration for <strong>{event.title}</strong>
                ? If you cancel, your reserved spot will automatically be granted to the next
                volunteer on the waitlist.
              </p>
              <div className="form-group">
                <label>Cancellation Reason *</label>
                <select
                  value={cancelCategory}
                  onChange={(e) => setCancelCategory(e.target.value)}
                  required
                >
                  <option value="Academic schedule conflict">Academic schedule conflict</option>
                  <option value="Exam / Internal assessment preparation">
                    Exam / Internal assessment preparation
                  </option>
                  <option value="Health / Medical reason">Health / Medical reason</option>
                  <option value="Family or personal emergency">Family or personal emergency</option>
                  <option value="Transportation issue">Transportation issue</option>
                  <option value="Other">Other</option>
                </select>
              </div>
              <div className="form-group">
                <label>Additional Remarks (Optional)</label>
                <textarea
                  rows={2}
                  placeholder="Provide any additional context for your Programme Officer..."
                  value={cancelRemarks}
                  onChange={(e) => setCancelRemarks(e.target.value)}
                  maxLength={200}
                />
              </div>
              <div className="modal-actions">
                <button
                  type="button"
                  className="btn-secondary"
                  onClick={() => setShowCancelModal(false)}
                >
                  Keep Registration
                </button>
                <button className="btn-danger" disabled={cancelling}>
                  {cancelling ? "Cancelling..." : "Confirm Cancellation"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
