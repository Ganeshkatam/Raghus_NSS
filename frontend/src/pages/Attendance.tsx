import React, { useState, useEffect, useCallback } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { apiRequest } from "../api/client";

interface SessionData {
  sessionId: string;
  eventId: string;
  eventTitle: string;
  openedByName: string;
  startsAt: string;
  expiresAt: string;
  status: string;
  qrToken?: string;
  presentCount: number;
  totalRegistered: number;
}

interface RosterItem {
  volunteerId: string;
  rollNumber: string;
  fullName: string;
  department: string;
  nssUnitCode: string;
  registrationStatus: string;
  attendanceStatus: string;
  checkInMethod: string | null;
  checkedInAt: string | null;
  attendanceId: string | null;
}

interface VolunteerAttendanceRecord {
  entryId: string;
  hours: number;
  status: string;
  eventTitle: string | null;
  description: string;
  approvedByName: string | null;
  createdAt: string;
}

interface PendingCorrection {
  correctionId: string;
  attendanceId: string;
  volunteerId?: string;
  volunteerName?: string;
  rollNumber?: string;
  requestedStatus?: string;
  newStatus?: string;
  previousStatus?: string;
  eventTitle?: string;
  reason: string;
  status: string;
  requestedByName?: string;
  correctedByName?: string;
  createdAt?: string;
  correctedAt?: string;
}

export const Attendance: React.FC = () => {
  const { user, isCoordinatorOrOfficer } = useAuth();
  const isManager = isCoordinatorOrOfficer || Boolean(user?.roles?.some((r) =>
    ["ADMIN", "FACULTY_COORDINATOR", "PROGRAMME_OFFICER"].includes(r)
  ));

  const [events, setEvents] = useState<any[]>([]);
  const [selectedEventId, setSelectedEventId] = useState<string | null>(null);
  const [activeSession, setActiveSession] = useState<SessionData | null>(null);
  const [roster, setRoster] = useState<RosterItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  // Volunteer check-in state
  const [manualTokenInput, setManualTokenInput] = useState("");
  const [checkingIn, setCheckingIn] = useState(false);
  const [volunteerHistory, setVolunteerHistory] = useState<VolunteerAttendanceRecord[]>([]);
  const [checkInResult, setCheckInResult] = useState<any | null>(null);

  // Audited correction modal state
  const [correctingRecord, setCorrectingRecord] = useState<RosterItem | null>(null);
  const [correctionStatus, setCorrectionStatus] = useState("PRESENT");
  const [correctionReason, setCorrectionReason] = useState("");
  const [submittingCorrection, setSubmittingCorrection] = useState(false);

  // Pending Corrections review state
  const [pendingCorrections, setPendingCorrections] = useState<PendingCorrection[]>([]);
  const [attendanceSubTab, setAttendanceSubTab] = useState<"roster" | "corrections">("roster");
  const [reviewModalCorrection, setReviewModalCorrection] = useState<PendingCorrection | null>(null);
  const [reviewAction, setReviewAction] = useState<"APPROVE" | "REJECT">("APPROVE");
  const [reviewRemarks, setReviewRemarks] = useState("");
  const [reviewing, setReviewing] = useState(false);

  const loadEvents = useCallback(async () => {
    try {
      const res = await apiRequest<any>("/events?size=50");
      const safeEvents = Array.isArray(res) ? res : Array.isArray(res?.content) ? res.content : [];
      setEvents(safeEvents);
      if (safeEvents.length > 0 && !selectedEventId) {
        setSelectedEventId(safeEvents[0].eventId);
      }
    } catch (err: any) {
      setError(err.message || "Failed to load events.");
    }
  }, [selectedEventId]);

  const loadVolunteerHistory = useCallback(async () => {
    try {
      const summary = await apiRequest<any>("/service-hours/my").catch(() => null);
      if (summary && summary.entries) {
        setVolunteerHistory(Array.isArray(summary.entries) ? summary.entries : []);
      }
    } catch {
      // Optional history fetch
    }
  }, []);

  const loadSessionAndRoster = useCallback(async (eventId: string) => {
    setLoading(true);
    setError(null);
    try {
      const session = await apiRequest<SessionData | null>(`/events/${eventId}/attendance/sessions/active`).catch(() => null);
      setActiveSession(session && session.status === "OPEN" ? session : null);

      const rosterData = await apiRequest<any>(`/events/${eventId}/attendance/roster`);
      const safeRoster = Array.isArray(rosterData) ? rosterData : Array.isArray(rosterData?.content) ? rosterData.content : [];
      setRoster(safeRoster);
    } catch (err: any) {
      setError(err.message || "Error loading attendance session.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (isManager) {
      loadEvents();
    } else {
      loadVolunteerHistory();
    }
  }, [isManager, loadEvents, loadVolunteerHistory]);

  useEffect(() => {
    if (selectedEventId && isManager) {
      loadSessionAndRoster(selectedEventId);
    }
  }, [selectedEventId, isManager, loadSessionAndRoster]);

  const handleOpenSession = async () => {
    if (!selectedEventId) return;
    setError(null);
    try {
      const now = new Date();
      const expiresAt = new Date(now.getTime() + 45 * 60000);
      const session = await apiRequest<SessionData>(`/events/${selectedEventId}/attendance/sessions`, {
        method: "POST",
        body: JSON.stringify({
          startsAt: now.toISOString(),
          expiresAt: expiresAt.toISOString(),
        }),
      });
      setActiveSession(session);
      setSuccess("Live attendance session opened.");
      loadSessionAndRoster(selectedEventId);
    } catch (err: any) {
      setError(err.message || "Could not open attendance session.");
    }
  };

  const handleCloseSession = async () => {
    if (!activeSession) return;
    try {
      await apiRequest(`/attendance/sessions/${activeSession.sessionId}/close`, { method: "POST" });
      setActiveSession(null);
      setSuccess("Attendance session closed.");
      if (selectedEventId) loadSessionAndRoster(selectedEventId);
    } catch (err: any) {
      setError(err.message || "Could not close session.");
    }
  };

  const handleVolunteerCheckIn = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!manualTokenInput.trim()) return;
    setCheckingIn(true);
    setError(null);
    setSuccess(null);
    setCheckInResult(null);
    try {
      const res = await apiRequest<any>("/attendance/check-in", {
        method: "POST",
        body: JSON.stringify({ token: manualTokenInput.trim() }),
      });
      setCheckInResult(res);
      setSuccess(res?.message || "Attendance recorded successfully! Verified check-in confirmed.");
      setManualTokenInput("");
      loadVolunteerHistory();
    } catch (err: any) {
      setError(err.message || "Check-in failed. Ensure session token is valid and not expired.");
    } finally {
      setCheckingIn(false);
    }
  };

  const handleAuditCorrection = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!correctingRecord || !correctingRecord.attendanceId) return;
    if (!correctionReason.trim()) {
      setError("An audit reason is required for corrections.");
      return;
    }
    setSubmittingCorrection(true);
    try {
      await apiRequest(`/attendance/records/${correctingRecord.attendanceId}/correct`, {
        method: "POST",
        body: JSON.stringify({
          newStatus: correctionStatus,
          reason: correctionReason.trim(),
        }),
      });
      setSuccess(`Record for ${correctingRecord.fullName} corrected to ${correctionStatus}.`);
      setCorrectingRecord(null);
      setCorrectionReason("");
      if (selectedEventId) loadSessionAndRoster(selectedEventId);
    } catch (err: any) {
      setError(err.message || "Attendance correction failed.");
    } finally {
      setSubmittingCorrection(false);
    }
  };

  const loadPendingCorrections = useCallback(async () => {
    if (!isManager) return;
    try {
      const list = await apiRequest<any>("/attendance/corrections/pending");
      const safeList = Array.isArray(list) ? list : Array.isArray(list?.content) ? list.content : [];
      setPendingCorrections(safeList);
    } catch {
      // Handled silently
    }
  }, [isManager]);

  useEffect(() => {
    if (isManager) {
      loadPendingCorrections();
    }
  }, [isManager, loadPendingCorrections]);

  const handleReviewCorrection = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!reviewModalCorrection) return;
    setReviewing(true);
    try {
      await apiRequest(`/attendance/corrections/${reviewModalCorrection.correctionId}/review`, {
        method: "POST",
        body: JSON.stringify({
          action: reviewAction,
          remarks: reviewRemarks.trim() || undefined
        })
      });
      setSuccess(`Correction request ${reviewAction.toLowerCase()}d successfully.`);
      setReviewModalCorrection(null);
      setReviewRemarks("");
      loadPendingCorrections();
      if (selectedEventId) loadSessionAndRoster(selectedEventId);
    } catch (err: any) {
      setError(err.message || `Failed to ${reviewAction.toLowerCase()} correction.`);
    } finally {
      setReviewing(false);
    }
  };

  return (
    <div className="page-container">
      {/* Page Header */}
      <div className="page-header">
        <div>
          <h1>{isManager ? "Attendance & QR Verification Operations" : "Volunteer Attendance Check-In"}</h1>
          <p className="subtitle">
            {isManager
              ? "Manage live check-in sessions, dynamic QR verification, and volunteer attendance rosters."
              : "Verify your participation in ongoing NSS activities using the session code displayed at the venue."}
          </p>
        </div>
      </div>

      {error && (
        <div className="alert alert-error" style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: "0.5rem" }}>
          <div>
            <strong>Notice:</strong> {error}
          </div>
          <button
            type="button"
            className="btn-secondary-sm"
            onClick={() => {
              setError(null);
              loadEvents();
              if (isManager) loadPendingCorrections();
            }}
          >
            Retry Connection
          </button>
        </div>
      )}
      {success && (
        <div className="alert alert-success">
          <strong>Success:</strong> {success}
        </div>
      )}

      {/* ----------------- 1. VOLUNTEER VIEW ----------------- */}
      {!isManager ? (
        <div style={{ display: "flex", flexDirection: "column", gap: "2rem" }}>
          {/* Check-In Card */}
          <div className="section-card" style={{ maxWidth: "680px", margin: "0 auto", width: "100%" }}>
            <div className="section-header">
              <div>
                <h2>Live Event Check-In</h2>
                <p className="subtitle">
                  Enter the 6-character session token displayed on screen by your Programme Officer:
                </p>
              </div>
            </div>

            <form onSubmit={handleVolunteerCheckIn} className="form-stack" style={{ padding: 0 }}>
              <div className="form-group">
                <label htmlFor="tokenInput">Attendance Session Token *</label>
                <input
                  id="tokenInput"
                  type="text"
                  placeholder="e.g. 8A3F9D"
                  value={manualTokenInput}
                  onChange={(e) => setManualTokenInput(e.target.value.toUpperCase())}
                  required
                  style={{
                    fontSize: "1.5rem",
                    letterSpacing: "0.25rem",
                    textAlign: "center",
                    fontWeight: 700,
                    fontFamily: "monospace",
                    padding: "0.75rem",
                    textTransform: "uppercase",
                  }}
                />
                <span className="cell-sub" style={{ textAlign: "center", display: "block", marginTop: "0.25rem" }}>
                  Tokens expire 45 minutes after the session is opened.
                </span>
              </div>

              <button
                type="submit"
                className="btn-primary"
                style={{ width: "100%", justifyContent: "center", padding: "0.75rem", fontSize: "1rem" }}
                disabled={checkingIn || !manualTokenInput.trim()}
              >
                {checkingIn ? "Verifying Attendance..." : "Confirm & Record Attendance"}
              </button>
            </form>

            {/* Check-in receipt card */}
            {checkInResult && (
              <div
                style={{
                  marginTop: "1.5rem",
                  padding: "1rem 1.25rem",
                  backgroundColor: "#f0fdf4",
                  border: "1px solid #bbf7d0",
                  borderRadius: "0.5rem",
                }}
              >
                <div style={{ fontWeight: 700, color: "#166534", marginBottom: "0.25rem" }}>
                  Verified Attendance Confirmed
                </div>
                <div style={{ fontSize: "0.85rem", color: "#15803d" }}>
                  Volunteer: <strong>{checkInResult.volunteerName}</strong> ({checkInResult.rollNumber})
                </div>
                <div style={{ fontSize: "0.85rem", color: "#15803d" }}>
                  Method: <strong>{checkInResult.checkInMethod || "QR / Token"}</strong> &bull; Status: <strong>{checkInResult.status}</strong>
                </div>
              </div>
            )}
          </div>

          {/* My Attendance History Card */}
          <div className="section-card">
            <div className="section-header">
              <div>
                <h2>My Event Attendance &amp; Service Records</h2>
                <p className="subtitle">Verified records accumulating towards your 240-hour certificate.</p>
              </div>
              <Link to="/service-hours" className="btn-secondary-sm">
                View Full Ledger
              </Link>
            </div>

            {volunteerHistory.length === 0 ? (
              <div className="empty-state">
                <h3>No Attendance Records Found</h3>
                <p>When you check in to NSS programmes, verified records will appear here with earned hours.</p>
                <Link to="/events" className="btn-primary-sm" style={{ marginTop: "0.5rem" }}>
                  Browse Upcoming Events
                </Link>
              </div>
            ) : (
              <div className="table-wrapper">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Date</th>
                      <th>Event / Programme</th>
                      <th>Hours Earned</th>
                      <th>Attendance Status</th>
                      <th>Verification Details</th>
                    </tr>
                  </thead>
                  <tbody>
                    {volunteerHistory.map((item) => (
                      <tr key={item.entryId}>
                        <td>{new Date(item.createdAt).toLocaleDateString()}</td>
                        <td>
                          <strong>{item.eventTitle || item.description}</strong>
                          {item.eventTitle && item.description && item.description !== item.eventTitle && (
                            <div className="cell-sub">{item.description}</div>
                          )}
                        </td>
                        <td>
                          <strong style={{ color: "#1e3a8a" }}>+{item.hours} hrs</strong>
                        </td>
                        <td>
                          <span
                            className={`badge ${
                              item.status === "APPROVED"
                                ? "badge-success"
                                : item.status === "PENDING"
                                ? "badge-warning"
                                : "badge-muted"
                            }`}
                          >
                            {item.status === "APPROVED" ? "PRESENT (VERIFIED)" : item.status}
                          </span>
                        </td>
                        <td>
                          <span className="cell-sub">{item.approvedByName || "Audited by NSS Cell"}</span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      ) : (
        /* ----------------- 2. OFFICER / ADMIN OPERATIONS VIEW ----------------- */
        <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>
          <div style={{ display: "flex", gap: "1rem", borderBottom: "1px solid var(--border-color, #e2e8f0)", paddingBottom: "0.5rem" }}>
            <button
              type="button"
              onClick={() => setAttendanceSubTab("roster")}
              style={{
                background: "none",
                border: "none",
                borderBottom: attendanceSubTab === "roster" ? "3px solid #1e40af" : "3px solid transparent",
                padding: "0.5rem 1rem",
                fontWeight: attendanceSubTab === "roster" ? 700 : 500,
                color: attendanceSubTab === "roster" ? "#1e40af" : "#64748b",
                cursor: "pointer"
              }}
            >
              Live Session &amp; Event Roster
            </button>
            <button
              type="button"
              onClick={() => { setAttendanceSubTab("corrections"); loadPendingCorrections(); }}
              style={{
                background: "none",
                border: "none",
                borderBottom: attendanceSubTab === "corrections" ? "3px solid #1e40af" : "3px solid transparent",
                padding: "0.5rem 1rem",
                fontWeight: attendanceSubTab === "corrections" ? 700 : 500,
                color: attendanceSubTab === "corrections" ? "#1e40af" : "#64748b",
                cursor: "pointer",
                display: "flex",
                alignItems: "center",
                gap: "0.5rem"
              }}
            >
              Pending Corrections Review
              {pendingCorrections.length > 0 && (
                <span style={{ backgroundColor: "#ef4444", color: "#ffffff", borderRadius: "9999px", padding: "0.1rem 0.45rem", fontSize: "0.75rem", fontWeight: 700 }}>
                  {pendingCorrections.length}
                </span>
              )}
            </button>
          </div>

          {attendanceSubTab === "corrections" ? (
            <div className="section-card">
              <div className="section-header">
                <div>
                  <h2>Pending Attendance Corrections Queue</h2>
                  <p className="subtitle">Audit and review requested attendance adjustments from event coordinators.</p>
                </div>
                <button type="button" onClick={loadPendingCorrections} className="btn-secondary-sm">
                  Refresh Queue
                </button>
              </div>

              {pendingCorrections.length === 0 ? (
                <div className="empty-state">
                  <h3>No Pending Corrections</h3>
                  <p>All attendance corrections have been reviewed and audited.</p>
                </div>
              ) : (
                <div className="table-wrapper">
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th>Volunteer</th>
                        <th>College ID</th>
                        <th>Event</th>
                        <th>Requested State</th>
                        <th>Justification</th>
                        <th>Submitted</th>
                        <th>Decision</th>
                      </tr>
                    </thead>
                    <tbody>
                      {pendingCorrections.map((pc) => (
                        <tr key={pc.correctionId}>
                          <td><strong>{pc.volunteerName || pc.correctedByName || "Volunteer Record"}</strong></td>
                          <td><span style={{ fontFamily: "monospace", fontSize: "0.85rem" }}>{pc.rollNumber || pc.attendanceId.substring(0, 8)}</span></td>
                          <td>{pc.eventTitle || "Session Record"}</td>
                          <td><span className="badge badge-primary">{pc.requestedStatus || pc.newStatus}</span></td>
                          <td><span className="cell-sub">{pc.reason}</span></td>
                          <td><span className="cell-sub">{new Date(pc.correctedAt || pc.createdAt || Date.now()).toLocaleString()}</span></td>
                          <td>
                            <div style={{ display: "flex", gap: "0.5rem" }}>
                              <button
                                type="button"
                                onClick={() => {
                                  setReviewModalCorrection(pc);
                                  setReviewAction("APPROVE");
                                  setReviewRemarks("");
                                }}
                                className="btn-primary-sm"
                              >
                                Approve
                              </button>
                              <button
                                type="button"
                                onClick={() => {
                                  setReviewModalCorrection(pc);
                                  setReviewAction("REJECT");
                                  setReviewRemarks("");
                                }}
                                className="btn-danger-sm"
                              >
                                Reject
                              </button>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          ) : (
            <>
              <div className="section-card">
            <div className="section-header" style={{ marginBottom: "1rem" }}>
              <div>
                <h2>Session Control &amp; Event Selector</h2>
                <p className="subtitle">Choose an event to manage its live attendance session and roster.</p>
              </div>
            </div>
            <div className="attendance-control-bar">
              <div className="attendance-select-group">
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <label htmlFor="eventSelect" style={{ margin: 0 }}>Select Event</label>
                  <button
                    type="button"
                    onClick={loadEvents}
                    style={{ background: "none", border: "none", color: "#2563eb", fontSize: "0.75rem", cursor: "pointer", fontWeight: 600, padding: 0 }}
                  >
                    Refresh List
                  </button>
                </div>
                <select
                  id="eventSelect"
                  className="attendance-select"
                  value={selectedEventId || ""}
                  onChange={(e) => setSelectedEventId(e.target.value || null)}
                >
                  {events.length === 0 && <option value="">No active events found</option>}
                  {events.map((ev) => (
                    <option key={ev.eventId} value={ev.eventId}>
                      {ev.title} {ev.unitName ? `(${ev.unitName})` : ""} &mdash; {ev.status}
                    </option>
                  ))}
                </select>
              </div>

              <div className="attendance-action-group">
                {activeSession ? (
                  <button type="button" onClick={handleCloseSession} className="btn-danger">
                    Close Attendance Session
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={handleOpenSession}
                    className="btn-primary"
                    disabled={!selectedEventId}
                  >
                    Open Attendance Session
                  </button>
                )}
              </div>
            </div>
          </div>

          {activeSession && (
            <div className="active-session-banner">
              <div className="session-banner-header">
                <div className="session-banner-info">
                  <span className="session-pulse" />
                  <div>
                    <h3>Active Attendance Session: {activeSession.eventTitle}</h3>
                    <p>
                      Opened by <strong>{activeSession.openedByName}</strong> &bull; Valid until:{" "}
                      <strong>{new Date(activeSession.expiresAt).toLocaleTimeString()}</strong>
                    </p>
                  </div>
                </div>

                <div className="session-banner-stats">
                  <div>
                    <span className="session-stat-number">{activeSession.presentCount}</span>
                    <span className="session-stat-divider">/</span>
                    <span>{activeSession.totalRegistered}</span>
                    <span className="session-stat-label">Present</span>
                  </div>
                </div>
              </div>

              {activeSession.qrToken && (
                <div className="session-token-wrapper">
                  <span className="token-label">Live Attendance Session Token (Project on screen for volunteers):</span>
                  <div className="token-code-row">
                    <span className="token-code">{activeSession.qrToken}</span>
                    <button
                      type="button"
                      className="btn-secondary-sm"
                      onClick={() => navigator.clipboard.writeText(activeSession.qrToken || "")}
                    >
                      Copy Token
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}

          <div className="section-card">
            <div className="section-header">
              <div>
                <h2>Event Roster &amp; Verification State</h2>
                <p className="subtitle">
                  {roster.length} Total Enrolled &bull; Filtered by registration
                </p>
              </div>
              <button
                type="button"
                className="btn-secondary-sm"
                onClick={() => selectedEventId && loadSessionAndRoster(selectedEventId)}
              >
                Refresh Roster
              </button>
            </div>

            {loading ? (
              <p className="loading-state">Loading roster records...</p>
            ) : roster.length === 0 ? (
              <div className="empty-state">
                <p>No volunteers registered for this event.</p>
              </div>
            ) : (
              <div className="table-wrapper">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Volunteer</th>
                      <th>College ID</th>
                      <th>Dept &amp; Unit</th>
                      <th>Registration</th>
                      <th>Attendance State</th>
                      <th>Check-in Method</th>
                      <th>Checked-In At</th>
                      <th>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {roster.map((row) => (
                      <tr key={row.volunteerId}>
                        <td>
                          <strong>{row.fullName}</strong>
                        </td>
                        <td>
                          <span style={{ fontFamily: "monospace", fontSize: "0.85rem" }}>{row.rollNumber}</span>
                        </td>
                        <td>
                          {row.department} {row.nssUnitCode ? `(${row.nssUnitCode})` : ""}
                        </td>
                        <td>
                          <span className={`badge ${row.registrationStatus === "REGISTERED" ? "badge-success" : "badge-muted"}`}>
                            {row.registrationStatus}
                          </span>
                        </td>
                        <td>
                          <span
                            className={`badge ${
                              row.attendanceStatus === "PRESENT"
                                ? "badge-success"
                                : row.attendanceStatus === "EXCUSED"
                                ? "badge-primary"
                                : row.attendanceStatus === "ABSENT"
                                ? "badge-danger"
                                : "badge-warning"
                            }`}
                          >
                            {row.attendanceStatus}
                          </span>
                        </td>
                        <td>
                          <span className="cell-sub">{row.checkInMethod || "\u2014"}</span>
                        </td>
                        <td>
                          <span className="cell-sub">
                            {row.checkedInAt ? new Date(row.checkedInAt).toLocaleTimeString() : "\u2014"}
                          </span>
                        </td>
                        <td>
                          {row.attendanceId ? (
                            <button
                              type="button"
                              onClick={() => {
                                setCorrectingRecord(row);
                                setCorrectionStatus(row.attendanceStatus);
                              }}
                              className="btn-secondary-sm"
                            >
                              Audit Correct
                            </button>
                          ) : (
                            <span className="cell-sub">Unrecorded</span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {correctingRecord && (
            <div className="modal-backdrop">
              <div className="modal-card">
                <div className="modal-header">
                  <h2>Audited Attendance Correction</h2>
                  <button
                    type="button"
                    className="btn-close"
                    onClick={() => setCorrectingRecord(null)}
                  >
                    &times;
                  </button>
                </div>
                <div className="modal-body">
                  <p className="subtitle" style={{ marginBottom: "1rem" }}>
                    Updating attendance for <strong>{correctingRecord.fullName}</strong> ({correctingRecord.rollNumber}).
                    Every adjustment is recorded in the institutional audit ledger.
                  </p>
                  <form onSubmit={handleAuditCorrection} className="form-stack" style={{ padding: 0 }}>
                    <div className="form-group">
                      <label htmlFor="statusSelect">New Attendance Status *</label>
                      <select
                        id="statusSelect"
                        value={correctionStatus}
                        onChange={(e) => setCorrectionStatus(e.target.value)}
                      >
                        <option value="PRESENT">PRESENT</option>
                        <option value="ABSENT">ABSENT</option>
                        <option value="EXCUSED">EXCUSED</option>
                      </select>
                    </div>

                    <div className="form-group">
                      <label htmlFor="reasonInput">Audit Justification / Reason *</label>
                      <textarea
                        id="reasonInput"
                        rows={3}
                        required
                        placeholder="e.g., Medical certificate submitted to Programme Officer."
                        value={correctionReason}
                        onChange={(e) => setCorrectionReason(e.target.value)}
                      />
                    </div>

                    <div className="modal-actions">
                      <button
                        type="button"
                        onClick={() => setCorrectingRecord(null)}
                        className="btn-secondary"
                        disabled={submittingCorrection}
                      >
                        Cancel
                      </button>
                      <button
                        type="submit"
                        className="btn-primary"
                        disabled={submittingCorrection || !correctionReason.trim()}
                      >
                        {submittingCorrection ? "Recording Audit..." : "Commit Correction"}
                      </button>
                    </div>
                  </form>
                </div>
              </div>
            </div>
          )}
          </>
          )}

          {reviewModalCorrection && (
            <div className="modal-backdrop">
              <div className="modal-card">
                <div className="modal-header">
                  <h2>Review Correction Request</h2>
                  <button
                    type="button"
                    className="btn-close"
                    onClick={() => setReviewModalCorrection(null)}
                  >
                    &times;
                  </button>
                </div>
                <div className="modal-body">
                  <p className="subtitle" style={{ marginBottom: "1rem" }}>
                    Action: <strong>{reviewAction}</strong> correction for <strong>{reviewModalCorrection.volunteerName || reviewModalCorrection.correctedByName || "Volunteer"}</strong> ({reviewModalCorrection.rollNumber || reviewModalCorrection.attendanceId.substring(0, 8)}) to <strong>{reviewModalCorrection.requestedStatus || reviewModalCorrection.newStatus}</strong>.
                  </p>
                  <div style={{ backgroundColor: "#f8fafc", padding: "0.75rem", borderRadius: "0.375rem", marginBottom: "1rem", fontSize: "0.875rem" }}>
                    <strong>Applicant Reason:</strong> {reviewModalCorrection.reason}
                  </div>
                  <form onSubmit={handleReviewCorrection} className="form-stack" style={{ padding: 0 }}>
                    <div className="form-group">
                      <label htmlFor="reviewRemarksInput">Review Remarks (Optional)</label>
                      <textarea
                        id="reviewRemarksInput"
                        rows={3}
                        placeholder="e.g. Verified with session attendance sheet."
                        value={reviewRemarks}
                        onChange={(e) => setReviewRemarks(e.target.value)}
                      />
                    </div>
                    <div className="modal-actions">
                      <button
                        type="button"
                        onClick={() => setReviewModalCorrection(null)}
                        className="btn-secondary"
                        disabled={reviewing}
                      >
                        Cancel
                      </button>
                      <button
                        type="submit"
                        className={reviewAction === "APPROVE" ? "btn-primary" : "btn-danger"}
                        disabled={reviewing}
                      >
                        {reviewing ? "Processing..." : `Confirm ${reviewAction}`}
                      </button>
                    </div>
                  </form>
                </div>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

