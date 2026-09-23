import React, { useState, useEffect, useCallback } from "react";
import { useAuth } from "../context/AuthContext";
import { apiRequest } from "../api/client";

interface SessionData {
  sessionId: number;
  eventId: number;
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
  volunteerId: number;
  rollNumber: string;
  fullName: string;
  department: string;
  nssUnitCode: string;
  registrationStatus: string;
  attendanceStatus: string;
  checkInMethod: string | null;
  checkedInAt: string | null;
  attendanceId: number | null;
}

export const Attendance: React.FC = () => {
  const { user } = useAuth();
  const isManager = user?.roles.some((r) =>
    ["ADMIN", "ROLE_ADMIN", "FACULTY_COORDINATOR", "ROLE_FACULTY_COORDINATOR", "PROGRAMME_OFFICER", "ROLE_PROGRAMME_OFFICER"].includes(r)
  );

  const [events, setEvents] = useState<any[]>([]);
  const [selectedEventId, setSelectedEventId] = useState<number | null>(null);
  const [activeSession, setActiveSession] = useState<SessionData | null>(null);
  const [roster, setRoster] = useState<RosterItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  // Volunteer scanner state
  const [manualTokenInput, setManualTokenInput] = useState("");
  const [checkingIn, setCheckingIn] = useState(false);

  // Audited correction modal state
  const [correctingRecord, setCorrectingRecord] = useState<RosterItem | null>(null);
  const [correctionStatus, setCorrectionStatus] = useState("PRESENT");
  const [correctionReason, setCorrectionReason] = useState("");
  const [submittingCorrection, setSubmittingCorrection] = useState(false);

  const loadEvents = useCallback(async () => {
    try {
      const res = await apiRequest<{ content: any[] }>("/events?size=50");
      setEvents(res.content || []);
      if (res.content && res.content.length > 0 && !selectedEventId) {
        setSelectedEventId(res.content[0].eventId);
      }
    } catch (err: any) {
      setError(err.message || "Failed to load events.");
    }
  }, [selectedEventId]);

  const loadSessionAndRoster = useCallback(async (eventId: number) => {
    setLoading(true);
    setError(null);
    try {
      const session = await apiRequest<SessionData | null>(`/events/${eventId}/attendance/sessions/active`).catch(() => null);
      setActiveSession(session && session.status === "OPEN" ? session : null);

      const rosterData = await apiRequest<RosterItem[]>(`/events/${eventId}/attendance/roster`);
      setRoster(rosterData);
    } catch (err: any) {
      setError(err.message || "Error loading attendance session.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadEvents();
  }, [loadEvents]);

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
    try {
      const res = await apiRequest<any>("/attendance/check-in", {
        method: "POST",
        body: JSON.stringify({ token: manualTokenInput.trim() }),
      });
      setSuccess(res.message || "Attendance recorded successfully!");
      setManualTokenInput("");
    } catch (err: any) {
      setError(err.message || "Check-in failed. Ensure QR token is valid and not expired.");
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

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h1>Attendance & QR Verification</h1>
          <p className="subtitle">
            Manage live check-in sessions, dynamic QR verification, and volunteer attendance rosters.
          </p>
        </div>
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

      {!isManager ? (
        <div className="section-card" style={{ maxWidth: "620px", margin: "1.5rem auto" }}>
          <div className="section-header">
            <div>
              <h2>Event QR Check-In</h2>
              <p className="subtitle">
                Scan the live QR code or enter the check-in token provided by your Programme Officer.
              </p>
            </div>
          </div>
          <form onSubmit={handleVolunteerCheckIn} className="form-stack" style={{ padding: 0 }}>
            <div className="form-group">
              <label htmlFor="tokenInput">Check-In Token *</label>
              <textarea
                id="tokenInput"
                rows={3}
                placeholder="Paste or enter QR check-in string..."
                value={manualTokenInput}
                onChange={(e) => setManualTokenInput(e.target.value)}
                required
              />
            </div>
            <button
              type="submit"
              className="btn-primary"
              style={{ width: "100%", justifyContent: "center" }}
              disabled={checkingIn || !manualTokenInput.trim()}
            >
              {checkingIn ? "Verifying Token..." : "Submit Attendance"}
            </button>
          </form>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>
          <div className="section-card">
            <div className="section-header" style={{ marginBottom: "1rem" }}>
              <div>
                <h2>Session Control & Event Selector</h2>
                <p className="subtitle">Choose an event to manage its live attendance session and roster.</p>
              </div>
            </div>
            <div className="attendance-control-bar">
              <div className="attendance-select-group">
                <label htmlFor="eventSelect">Select Event</label>
                <select
                  id="eventSelect"
                  className="attendance-select"
                  value={selectedEventId || ""}
                  onChange={(e) => setSelectedEventId(Number(e.target.value))}
                >
                  {events.length === 0 && <option value="">No active events found</option>}
                  {events.map((ev) => (
                    <option key={ev.eventId} value={ev.eventId}>
                      {ev.title} {ev.unitName ? `(${ev.unitName})` : ""} — {ev.status}
                    </option>
                  ))}
                </select>
              </div>

              <div className="attendance-action-group">
                {activeSession ? (
                  <button onClick={handleCloseSession} className="btn-danger">
                    Close Live Session
                  </button>
                ) : (
                  <button
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
                  <span className="badge badge-success session-pulse">LIVE SESSION OPEN</span>
                  <h2>{activeSession.eventTitle}</h2>
                  <p>
                    Opened by <strong>{activeSession.openedByName}</strong> • Expires at {new Date(activeSession.expiresAt).toLocaleTimeString()}
                  </p>
                </div>
                <div className="session-banner-stats">
                  <div className="session-stat-number">
                    {activeSession.presentCount} <span className="session-stat-divider">/</span> {activeSession.totalRegistered}
                  </div>
                  <div className="session-stat-label">Verified Present</div>
                </div>
              </div>

              {activeSession.qrToken && (
                <div className="session-token-wrapper">
                  <div className="token-label">Live Check-In Token (Project or Share with Volunteers)</div>
                  <div className="token-code-row">
                    <code className="token-code">{activeSession.qrToken}</code>
                    <button
                      type="button"
                      className="btn-secondary-sm"
                      onClick={() => {
                        navigator.clipboard.writeText(activeSession.qrToken || "");
                        setSuccess("Check-in token copied to clipboard.");
                      }}
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
                <h2>Event Roster & Verification State</h2>
                <p className="subtitle">
                  Real-time verification log for student volunteers enrolled in this event.
                </p>
              </div>
              <span className="badge badge-muted">
                {roster.length} {roster.length === 1 ? "Volunteer" : "Volunteers"} Enrolled
              </span>
            </div>

            {loading ? (
              <div className="loading-state">
                <p>Loading roster verification data...</p>
              </div>
            ) : roster.length === 0 ? (
              <div className="empty-state">
                <h3>No Volunteers Enrolled</h3>
                <p>No student volunteers are currently registered for this event roster.</p>
              </div>
            ) : (
              <div className="table-wrapper">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Volunteer</th>
                      <th>College ID</th>
                      <th>Dept & Unit</th>
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
                                : "badge-warning"
                            }`}
                          >
                            {row.attendanceStatus}
                          </span>
                        </td>
                        <td>
                          <span className="cell-sub">{row.checkInMethod || "—"}</span>
                        </td>
                        <td>
                          <span className="cell-sub">
                            {row.checkedInAt ? new Date(row.checkedInAt).toLocaleTimeString() : "—"}
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
        </div>
      )}
    </div>
  );
};

