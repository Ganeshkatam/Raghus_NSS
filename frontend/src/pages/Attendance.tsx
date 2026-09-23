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
          <span className="text-overline">Participation Operations</span>
          <h1 className="page-title">Attendance & QR Verification</h1>
        </div>
      </div>

      {error && <div className="alert alert-danger mb-4">{error}</div>}
      {success && <div className="alert alert-success mb-4">{success}</div>}

      {!isManager ? (
        <div className="card max-w-xl mx-auto p-6">
          <h2 className="card-title">Event QR Check-In</h2>
          <p className="card-description mb-4">
            Scan the live QR code displayed on the auditorium projector or enter the check-in token provided by your Programme Officer.
          </p>
          <form onSubmit={handleVolunteerCheckIn} className="space-y-4">
            <div>
              <label htmlFor="tokenInput" className="form-label">Check-In Token</label>
              <textarea
                id="tokenInput"
                className="form-control"
                rows={3}
                placeholder="Paste or scan QR check-in string..."
                value={manualTokenInput}
                onChange={(e) => setManualTokenInput(e.target.value)}
                required
              />
            </div>
            <button
              type="submit"
              className="btn btn-primary w-full"
              disabled={checkingIn || !manualTokenInput.trim()}
            >
              {checkingIn ? "Verifying Token..." : "Submit Attendance"}
            </button>
          </form>
        </div>
      ) : (
        <div className="space-y-6">
          <div className="card p-4 flex flex-wrap items-center justify-between gap-4">
            <div className="flex items-center gap-4">
              <label htmlFor="eventSelect" className="font-semibold text-slate-700">Select Event:</label>
              <select
                id="eventSelect"
                className="form-control"
                value={selectedEventId || ""}
                onChange={(e) => setSelectedEventId(Number(e.target.value))}
              >
                {events.map((ev) => (
                  <option key={ev.eventId} value={ev.eventId}>
                    {ev.title} ({ev.unitCode}) - {ev.status}
                  </option>
                ))}
              </select>
            </div>

            <div className="flex items-center gap-3">
              {activeSession ? (
                <button onClick={handleCloseSession} className="btn btn-danger">
                  Close Live Session
                </button>
              ) : (
                <button onClick={handleOpenSession} className="btn btn-primary" disabled={!selectedEventId}>
                  Open Attendance Session
                </button>
              )}
            </div>
          </div>

          {activeSession && (
            <div className="card p-6 bg-slate-900 text-white rounded-lg">
              <div className="flex flex-wrap justify-between items-center mb-4">
                <div>
                  <span className="badge badge-success">SESSION ACTIVE</span>
                  <h3 className="text-xl font-bold mt-2 text-white">{activeSession.eventTitle}</h3>
                  <p className="text-slate-400 text-sm">
                    Opened by {activeSession.openedByName} • Expires: {new Date(activeSession.expiresAt).toLocaleTimeString()}
                  </p>
                </div>
                <div className="text-right">
                  <div className="text-3xl font-extrabold text-emerald-400">
                    {activeSession.presentCount} / {activeSession.totalRegistered}
                  </div>
                  <div className="text-xs text-slate-400 uppercase tracking-wider">Verified Present</div>
                </div>
              </div>

              {activeSession.qrToken && (
                <div className="bg-white p-4 rounded text-slate-900 inline-block font-mono text-xs break-all max-w-full">
                  <span className="block font-bold mb-1 text-slate-600">Active Check-In Token:</span>
                  {activeSession.qrToken}
                </div>
              )}
            </div>
          )}

          <div className="card overflow-hidden">
            <div className="p-4 border-b border-slate-200 flex justify-between items-center">
              <h3 className="font-bold text-slate-800">Event Roster & Verification State</h3>
              <span className="text-sm text-slate-500">{roster.length} Total Enrolled</span>
            </div>

            {loading ? (
              <div className="p-8 text-center text-slate-500">Loading roster data...</div>
            ) : roster.length === 0 ? (
              <div className="p-8 text-center text-slate-500">No volunteers registered for this event.</div>
            ) : (
              <div className="table-responsive">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Volunteer</th>
                      <th>Roll Number</th>
                      <th>Dept / Unit</th>
                      <th>Registration State</th>
                      <th>Attendance State</th>
                      <th>Method</th>
                      <th>Timestamp</th>
                      <th>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {roster.map((row) => (
                      <tr key={row.volunteerId}>
                        <td className="font-medium">{row.fullName}</td>
                        <td className="font-mono text-xs">{row.rollNumber}</td>
                        <td>{row.department} ({row.nssUnitCode})</td>
                        <td>
                          <span className={`badge ${row.registrationStatus === "REGISTERED" ? "badge-success" : "badge-secondary"}`}>
                            {row.registrationStatus}
                          </span>
                        </td>
                        <td>
                          <span
                            className={`badge ${
                              row.attendanceStatus === "PRESENT"
                                ? "badge-success"
                                : row.attendanceStatus === "EXCUSED"
                                ? "badge-info"
                                : "badge-warning"
                            }`}
                          >
                            {row.attendanceStatus}
                          </span>
                        </td>
                        <td className="text-xs text-slate-500">{row.checkInMethod || "—"}</td>
                        <td className="text-xs text-slate-500">
                          {row.checkedInAt ? new Date(row.checkedInAt).toLocaleTimeString() : "—"}
                        </td>
                        <td>
                          {row.attendanceId ? (
                            <button
                              onClick={() => {
                                setCorrectingRecord(row);
                                setCorrectionStatus(row.attendanceStatus);
                              }}
                              className="btn btn-secondary btn-sm"
                            >
                              Audit Correct
                            </button>
                          ) : (
                            <span className="text-xs text-slate-400">Unrecorded</span>
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
              <div className="modal-dialog">
                <div className="card p-6">
                  <h3 className="card-title">Audited Attendance Correction</h3>
                  <p className="card-description mb-4">
                    Updating attendance for <strong>{correctingRecord.fullName}</strong> ({correctingRecord.rollNumber}).
                    Every adjustment is recorded in the institutional audit ledger.
                  </p>
                  <form onSubmit={handleAuditCorrection} className="space-y-4">
                    <div>
                      <label htmlFor="statusSelect" className="form-label">New Attendance Status</label>
                      <select
                        id="statusSelect"
                        className="form-control"
                        value={correctionStatus}
                        onChange={(e) => setCorrectionStatus(e.target.value)}
                      >
                        <option value="PRESENT">PRESENT</option>
                        <option value="ABSENT">ABSENT</option>
                        <option value="EXCUSED">EXCUSED</option>
                      </select>
                    </div>
                    <div>
                      <label htmlFor="reasonInput" className="form-label">Audit Justification / Reason *</label>
                      <textarea
                        id="reasonInput"
                        className="form-control"
                        rows={3}
                        required
                        placeholder="e.g., Medical fitness certificate submitted to Unit Officer."
                        value={correctionReason}
                        onChange={(e) => setCorrectionReason(e.target.value)}
                      />
                    </div>
                    <div className="flex justify-end gap-3 pt-2">
                      <button
                        type="button"
                        onClick={() => setCorrectingRecord(null)}
                        className="btn btn-secondary"
                        disabled={submittingCorrection}
                      >
                        Cancel
                      </button>
                      <button
                        type="submit"
                        className="btn btn-primary"
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
