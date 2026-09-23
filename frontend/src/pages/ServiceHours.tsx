import React, { useState, useEffect, useCallback } from "react";
import { useAuth } from "../context/AuthContext";
import { apiRequest } from "../api/client";

interface ServiceHourEntry {
  entryId: number;
  volunteerId: number;
  volunteerName: string;
  rollNumber: string;
  eventId: number | null;
  eventTitle: string | null;
  hours: number;
  status: "APPROVED" | "PENDING" | "REJECTED";
  approvedByName: string | null;
  description: string;
  createdAt: string;
}

interface PersonalSummary {
  volunteerId: number;
  volunteerName: string;
  rollNumber: string;
  totalApprovedHours: number;
  approvedCount: number;
  pendingCount: number;
  entries: ServiceHourEntry[];
}

export const ServiceHours: React.FC = () => {
  const { user, isCoordinatorOrOfficer } = useAuth();
  const isOfficerOrAdmin = isCoordinatorOrOfficer || Boolean(user?.roles?.some((r) =>
    ["ADMIN", "ROLE_ADMIN", "FACULTY_COORDINATOR", "ROLE_FACULTY_COORDINATOR", "PROGRAMME_OFFICER", "ROLE_PROGRAMME_OFFICER"].includes(r)
  ));

  const [activeTab, setActiveTab] = useState<"my" | "pending">(isOfficerOrAdmin ? "pending" : "my");
  const [personalSummary, setPersonalSummary] = useState<PersonalSummary | null>(null);
  const [pendingClaims, setPendingClaims] = useState<ServiceHourEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  // Sync tab if officer or admin role resolves
  useEffect(() => {
    if (isOfficerOrAdmin && !personalSummary) {
      setActiveTab("pending");
    }
  }, [isOfficerOrAdmin]);

  // Claim modal state
  const [showClaimModal, setShowClaimModal] = useState(false);
  const [claimHours, setClaimHours] = useState("");
  const [claimDescription, setClaimDescription] = useState("");
  const [claimEventId, setClaimEventId] = useState("");
  const [events, setEvents] = useState<any[]>([]);
  const [submittingClaim, setSubmittingClaim] = useState(false);

  // Review modal state
  const [rejectingEntry, setRejectingEntry] = useState<ServiceHourEntry | null>(null);
  const [rejectionReason, setRejectionReason] = useState("");
  const [reviewing, setReviewing] = useState(false);

  const loadMyHours = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await apiRequest<PersonalSummary | null>("/service-hours/my");
      setPersonalSummary(data);
    } catch (err: any) {
      const isExpectedNonVolunteer =
        err?.message?.includes("Only enrolled volunteers") ||
        err?.message?.includes("Access is denied") ||
        err?.message?.includes("FORBIDDEN") ||
        err?.code === "FORBIDDEN";
      if (!isExpectedNonVolunteer) {
        setError(err.message || "Failed to load personal service hours.");
      }
    } finally {
      setLoading(false);
    }
  }, []);

  const loadPendingClaims = useCallback(async () => {
    if (!isOfficerOrAdmin) return;
    try {
      setLoading(true);
      setError(null);
      const list = await apiRequest<ServiceHourEntry[]>("/service-hours/pending");
      setPendingClaims(list || []);
    } catch (err: any) {
      setError(err.message || "Failed to load pending claims.");
    } finally {
      setLoading(false);
    }
  }, [isOfficerOrAdmin]);

  const loadEvents = useCallback(async () => {
    try {
      const res = await apiRequest<{ content: any[] }>("/events?size=100");
      setEvents(res.content || []);
    } catch {
      // Optional background fetch
    }
  }, []);

  useEffect(() => {
    if (activeTab === "my") {
      loadMyHours();
      loadEvents();
    } else {
      loadPendingClaims();
    }
  }, [activeTab, loadMyHours, loadPendingClaims, loadEvents]);

  const handleSubmitClaim = async (e: React.FormEvent) => {
    e.preventDefault();
    const hoursNum = parseFloat(claimHours);
    if (isNaN(hoursNum) || hoursNum <= 0 || hoursNum > 24) {
      setError("Please specify hours between 0.25 and 24.0.");
      return;
    }
    if (!claimDescription.trim()) {
      setError("Please provide an activity description.");
      return;
    }

    try {
      setSubmittingClaim(true);
      setError(null);
      await apiRequest<ServiceHourEntry>("/service-hours/claim", {
        method: "POST",
        body: JSON.stringify({
          hours: hoursNum,
          description: claimDescription.trim(),
          eventId: claimEventId ? parseInt(claimEventId, 10) : null
        })
      });
      setSuccess("Service hour claim submitted successfully. Awaiting coordinator review.");
      setShowClaimModal(false);
      setClaimHours("");
      setClaimDescription("");
      setClaimEventId("");
      loadMyHours();
    } catch (err: any) {
      setError(err.message || "Failed to submit claim.");
    } finally {
      setSubmittingClaim(false);
    }
  };

  const handleApproveClaim = async (entryId: number) => {
    try {
      setReviewing(true);
      setError(null);
      await apiRequest(`/service-hours/${entryId}/review`, {
        method: "POST",
        body: JSON.stringify({ action: "APPROVE", reason: "Approved by Unit Coordinator" })
      });
      setSuccess("Service hour claim approved successfully.");
      loadPendingClaims();
    } catch (err: any) {
      setError(err.message || "Failed to approve claim.");
    } finally {
      setReviewing(false);
    }
  };

  const handleRejectClaim = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!rejectingEntry) return;

    try {
      setReviewing(true);
      setError(null);
      await apiRequest(`/service-hours/${rejectingEntry.entryId}/review`, {
        method: "POST",
        body: JSON.stringify({ action: "REJECT", reason: rejectionReason.trim() })
      });
      setSuccess("Service hour claim rejected.");
      setRejectingEntry(null);
      setRejectionReason("");
      loadPendingClaims();
    } catch (err: any) {
      setError(err.message || "Failed to reject claim.");
    } finally {
      setReviewing(false);
    }
  };

  return (
    <div className="container" style={{ padding: "2rem 1rem", maxWidth: "1200px", margin: "0 auto" }}>
      {/* Header */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "2rem", flexWrap: "wrap", gap: "1rem" }}>
        <div>
          <h1 style={{ margin: 0, fontSize: "1.875rem", fontWeight: 700, color: "var(--text-main, #0f172a)" }}>
            Service Hours Ledger
          </h1>
          <p style={{ margin: "0.25rem 0 0", color: "var(--text-muted, #64748b)", fontSize: "0.95rem" }}>
            Institutional verified hours accumulation, milestone certifications, and audit tracking
          </p>
        </div>

        {/* Action button */}
        <div style={{ display: "flex", gap: "0.75rem" }}>
          {user?.roles?.some((r) => ["VOLUNTEER", "ROLE_VOLUNTEER"].includes(r)) && (
            <button
              onClick={() => setShowClaimModal(true)}
              style={{
                backgroundColor: "var(--primary, #1e40af)",
                color: "#ffffff",
                padding: "0.625rem 1.25rem",
                borderRadius: "0.5rem",
                border: "none",
                fontWeight: 600,
                cursor: "pointer",
                boxShadow: "0 1px 3px rgba(0,0,0,0.1)"
              }}
            >
              + Log Service Hours Claim
            </button>
          )}
        </div>
      </div>

      {/* Notifications */}
      {error && (
        <div style={{ padding: "1rem", backgroundColor: "#fef2f2", color: "#991b1b", borderRadius: "0.5rem", marginBottom: "1.5rem", border: "1px solid #fecaca" }}>
          {error}
        </div>
      )}
      {success && (
        <div style={{ padding: "1rem", backgroundColor: "#f0fdf4", color: "#166534", borderRadius: "0.5rem", marginBottom: "1.5rem", border: "1px solid #bbf7d0" }}>
          {success}
        </div>
      )}

      {/* Navigation Tabs */}
      <div style={{ display: "flex", gap: "1rem", borderBottom: "1px solid var(--border-color, #e2e8f0)", marginBottom: "2rem" }}>
        <button
          onClick={() => setActiveTab("my")}
          style={{
            padding: "0.75rem 1.25rem",
            background: "none",
            border: "none",
            borderBottom: activeTab === "my" ? "3px solid var(--primary, #1e40af)" : "3px solid transparent",
            color: activeTab === "my" ? "var(--primary, #1e40af)" : "var(--text-muted, #64748b)",
            fontWeight: activeTab === "my" ? 700 : 500,
            cursor: "pointer"
          }}
        >
          My Service Ledger
        </button>

        {isOfficerOrAdmin && (
          <button
            onClick={() => setActiveTab("pending")}
            style={{
              padding: "0.75rem 1.25rem",
              background: "none",
              border: "none",
              borderBottom: activeTab === "pending" ? "3px solid var(--primary, #1e40af)" : "3px solid transparent",
              color: activeTab === "pending" ? "var(--primary, #1e40af)" : "var(--text-muted, #64748b)",
              fontWeight: activeTab === "pending" ? 700 : 500,
              cursor: "pointer",
              display: "flex",
              alignItems: "center",
              gap: "0.5rem"
            }}
          >
            Review Claims Queue
            {pendingClaims.length > 0 && (
              <span style={{ backgroundColor: "#ef4444", color: "#ffffff", borderRadius: "9999px", padding: "0.15rem 0.5rem", fontSize: "0.75rem", fontWeight: 700 }}>
                {pendingClaims.length}
              </span>
            )}
          </button>
        )}
      </div>

      {loading && (
        <div style={{ textAlign: "center", padding: "3rem", color: "var(--text-muted, #64748b)" }}>
          Loading service hour records...
        </div>
      )}

      {/* TAB 1: My Service Hours */}
      {!loading && activeTab === "my" && (
        <div>
          {personalSummary ? (
            <>
              {/* Stat Cards */}
              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(240px, 1fr))", gap: "1.5rem", marginBottom: "2rem" }}>
                <div style={{ background: "#ffffff", padding: "1.5rem", borderRadius: "0.75rem", border: "1px solid var(--border-color, #e2e8f0)", boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}>
                  <div style={{ fontSize: "0.875rem", color: "var(--text-muted, #64748b)", fontWeight: 600 }}>Total Verified Hours</div>
                  <div style={{ fontSize: "2.25rem", fontWeight: 800, color: "#1e40af", marginTop: "0.25rem" }}>
                    {personalSummary.totalApprovedHours} <span style={{ fontSize: "1rem", fontWeight: 500, color: "#64748b" }}>/ 240 hrs</span>
                  </div>
                  {/* Progress bar towards 240 hrs NSS certificate */}
                  <div style={{ width: "100%", height: "8px", backgroundColor: "#e2e8f0", borderRadius: "4px", marginTop: "0.75rem", overflow: "hidden" }}>
                    <div
                      style={{
                        height: "100%",
                        width: `${Math.min(100, (personalSummary.totalApprovedHours / 240) * 100)}%`,
                        backgroundColor: "#1e40af",
                        borderRadius: "4px"
                      }}
                    />
                  </div>
                  <div style={{ fontSize: "0.75rem", color: "#64748b", marginTop: "0.5rem" }}>
                    {Math.round((personalSummary.totalApprovedHours / 240) * 100)}% of 2-year certification requirement
                  </div>
                </div>

                <div style={{ background: "#ffffff", padding: "1.5rem", borderRadius: "0.75rem", border: "1px solid var(--border-color, #e2e8f0)", boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}>
                  <div style={{ fontSize: "0.875rem", color: "var(--text-muted, #64748b)", fontWeight: 600 }}>Approved Activities</div>
                  <div style={{ fontSize: "2.25rem", fontWeight: 800, color: "#166534", marginTop: "0.25rem" }}>
                    {personalSummary.approvedCount}
                  </div>
                  <div style={{ fontSize: "0.75rem", color: "#64748b", marginTop: "0.5rem" }}>
                    Verified via QR session & coordinator approval
                  </div>
                </div>

                <div style={{ background: "#ffffff", padding: "1.5rem", borderRadius: "0.75rem", border: "1px solid var(--border-color, #e2e8f0)", boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}>
                  <div style={{ fontSize: "0.875rem", color: "var(--text-muted, #64748b)", fontWeight: 600 }}>Pending Review</div>
                  <div style={{ fontSize: "2.25rem", fontWeight: 800, color: "#d97706", marginTop: "0.25rem" }}>
                    {personalSummary.pendingCount}
                  </div>
                  <div style={{ fontSize: "0.75rem", color: "#64748b", marginTop: "0.5rem" }}>
                    Manual claims under evaluation by officer
                  </div>
                </div>
              </div>

              {/* Entries Table */}
              <div style={{ background: "#ffffff", borderRadius: "0.75rem", border: "1px solid var(--border-color, #e2e8f0)", overflow: "hidden", boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}>
                <div style={{ padding: "1rem 1.5rem", borderBottom: "1px solid var(--border-color, #e2e8f0)", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <h3 style={{ margin: 0, fontSize: "1.125rem", fontWeight: 700 }}>Activity Ledger Entries</h3>
                  <span style={{ fontSize: "0.875rem", color: "#64748b" }}>{personalSummary.entries.length} Total records</span>
                </div>

                {personalSummary.entries.length === 0 ? (
                  <div style={{ padding: "3rem", textAlign: "center", color: "#64748b" }}>
                    No service hour entries found on your ledger yet. Attend events or submit manual claims to accrue hours.
                  </div>
                ) : (
                  <div style={{ overflowX: "auto" }}>
                    <table style={{ width: "100%", borderCollapse: "collapse", textAlign: "left", fontSize: "0.875rem" }}>
                      <thead>
                        <tr style={{ backgroundColor: "#f8fafc", borderBottom: "1px solid #e2e8f0", color: "#475569" }}>
                          <th style={{ padding: "0.75rem 1rem" }}>Date</th>
                          <th style={{ padding: "0.75rem 1rem" }}>Activity / Event</th>
                          <th style={{ padding: "0.75rem 1rem" }}>Hours</th>
                          <th style={{ padding: "0.75rem 1rem" }}>Status</th>
                          <th style={{ padding: "0.75rem 1rem" }}>Approver / Reason</th>
                        </tr>
                      </thead>
                      <tbody>
                        {personalSummary.entries.map((entry) => (
                          <tr key={entry.entryId} style={{ borderBottom: "1px solid #f1f5f9" }}>
                            <td style={{ padding: "0.75rem 1rem", color: "#64748b", whiteSpace: "nowrap" }}>
                              {new Date(entry.createdAt).toLocaleDateString()}
                            </td>
                            <td style={{ padding: "0.75rem 1rem", fontWeight: 600, color: "#1e293b" }}>
                              {entry.eventTitle || entry.description}
                              {entry.eventTitle && entry.description && entry.description !== entry.eventTitle && (
                                <div style={{ fontSize: "0.75rem", fontWeight: 400, color: "#64748b" }}>{entry.description}</div>
                              )}
                            </td>
                            <td style={{ padding: "0.75rem 1rem", fontWeight: 700, color: "#0f172a" }}>
                              {entry.hours} hrs
                            </td>
                            <td style={{ padding: "0.75rem 1rem" }}>
                              <span
                                style={{
                                  padding: "0.25rem 0.625rem",
                                  borderRadius: "9999px",
                                  fontSize: "0.75rem",
                                  fontWeight: 700,
                                  backgroundColor:
                                    entry.status === "APPROVED"
                                      ? "#dcfce7"
                                      : entry.status === "PENDING"
                                      ? "#fef3c7"
                                      : "#fee2e2",
                                  color:
                                    entry.status === "APPROVED"
                                      ? "#15803d"
                                      : entry.status === "PENDING"
                                      ? "#b45309"
                                      : "#b91c1c"
                                }}
                              >
                                {entry.status}
                              </span>
                            </td>
                            <td style={{ padding: "0.75rem 1rem", color: "#64748b" }}>
                              {entry.approvedByName || "Pending review"}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </>
          ) : (
            <div style={{ padding: "3rem", textAlign: "center", color: "#64748b" }}>
              Sign in with an active volunteer account to view your personal service hours ledger.
            </div>
          )}
        </div>
      )}

      {/* TAB 2: Pending Claims Review Queue */}
      {!loading && activeTab === "pending" && isOfficerOrAdmin && (
        <div style={{ background: "#ffffff", borderRadius: "0.75rem", border: "1px solid var(--border-color, #e2e8f0)", overflow: "hidden", boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}>
          <div style={{ padding: "1rem 1.5rem", borderBottom: "1px solid var(--border-color, #e2e8f0)", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <h3 style={{ margin: 0, fontSize: "1.125rem", fontWeight: 700 }}>Pending Review Queue</h3>
            <span style={{ fontSize: "0.875rem", color: "#64748b" }}>{pendingClaims.length} Claims awaiting action</span>
          </div>

          {pendingClaims.length === 0 ? (
            <div style={{ padding: "3rem", textAlign: "center", color: "#64748b" }}>
              No pending service hour claims to review at this time.
            </div>
          ) : (
            <div style={{ overflowX: "auto" }}>
              <table style={{ width: "100%", borderCollapse: "collapse", textAlign: "left", fontSize: "0.875rem" }}>
                <thead>
                  <tr style={{ backgroundColor: "#f8fafc", borderBottom: "1px solid #e2e8f0", color: "#475569" }}>
                    <th style={{ padding: "0.75rem 1rem" }}>Volunteer</th>
                    <th style={{ padding: "0.75rem 1rem" }}>College ID</th>
                    <th style={{ padding: "0.75rem 1rem" }}>Activity Description</th>
                    <th style={{ padding: "0.75rem 1rem" }}>Hours</th>
                    <th style={{ padding: "0.75rem 1rem" }}>Submitted At</th>
                    <th style={{ padding: "0.75rem 1rem", textAlign: "right" }}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {pendingClaims.map((claim) => (
                    <tr key={claim.entryId} style={{ borderBottom: "1px solid #f1f5f9" }}>
                      <td style={{ padding: "0.75rem 1rem", fontWeight: 600, color: "#1e293b" }}>
                        {claim.volunteerName}
                      </td>
                      <td style={{ padding: "0.75rem 1rem", color: "#64748b" }}>
                        {claim.rollNumber}
                      </td>
                      <td style={{ padding: "0.75rem 1rem", color: "#334155" }}>
                        {claim.description}
                        {claim.eventTitle && (
                          <div style={{ fontSize: "0.75rem", color: "#1e40af" }}>Event: {claim.eventTitle}</div>
                        )}
                      </td>
                      <td style={{ padding: "0.75rem 1rem", fontWeight: 700, color: "#0f172a" }}>
                        {claim.hours} hrs
                      </td>
                      <td style={{ padding: "0.75rem 1rem", color: "#64748b", whiteSpace: "nowrap" }}>
                        {new Date(claim.createdAt).toLocaleDateString()}
                      </td>
                      <td style={{ padding: "0.75rem 1rem", textAlign: "right" }}>
                        <div style={{ display: "flex", gap: "0.5rem", justifyContent: "flex-end" }}>
                          <button
                            onClick={() => handleApproveClaim(claim.entryId)}
                            disabled={reviewing}
                            style={{
                              padding: "0.375rem 0.75rem",
                              backgroundColor: "#16a34a",
                              color: "#ffffff",
                              borderRadius: "0.375rem",
                              border: "none",
                              fontWeight: 600,
                              fontSize: "0.8125rem",
                              cursor: "pointer"
                            }}
                          >
                            Approve
                          </button>
                          <button
                            onClick={() => setRejectingEntry(claim)}
                            disabled={reviewing}
                            style={{
                              padding: "0.375rem 0.75rem",
                              backgroundColor: "#dc2626",
                              color: "#ffffff",
                              borderRadius: "0.375rem",
                              border: "none",
                              fontWeight: 600,
                              fontSize: "0.8125rem",
                              cursor: "pointer"
                            }}
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
      )}

      {/* Claim Submission Modal */}
      {showClaimModal && (
        <div style={{ position: "fixed", inset: 0, backgroundColor: "rgba(0,0,0,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#ffffff", padding: "2rem", borderRadius: "0.75rem", width: "90%", maxWidth: "500px", boxShadow: "0 20px 25px -5px rgba(0,0,0,0.1)" }}>
            <h3 style={{ margin: "0 0 1rem", fontSize: "1.25rem", fontWeight: 700 }}>Log Service Hours Claim</h3>
            <form onSubmit={handleSubmitClaim}>
              <div style={{ marginBottom: "1rem" }}>
                <label style={{ display: "block", fontSize: "0.875rem", fontWeight: 600, marginBottom: "0.25rem" }}>
                  Hours to Claim *
                </label>
                <input
                  type="number"
                  step="0.25"
                  min="0.25"
                  max="24.0"
                  value={claimHours}
                  onChange={(e) => setClaimHours(e.target.value)}
                  placeholder="e.g. 4.00"
                  required
                  style={{ width: "100%", padding: "0.5rem", borderRadius: "0.375rem", border: "1px solid #cbd5e1" }}
                />
              </div>

              <div style={{ marginBottom: "1rem" }}>
                <label style={{ display: "block", fontSize: "0.875rem", fontWeight: 600, marginBottom: "0.25rem" }}>
                  Associated Event (Optional)
                </label>
                <select
                  value={claimEventId}
                  onChange={(e) => setClaimEventId(e.target.value)}
                  style={{ width: "100%", padding: "0.5rem", borderRadius: "0.375rem", border: "1px solid #cbd5e1" }}
                >
                  <option value="">Independent Community Service Activity</option>
                  {events.map((ev) => (
                    <option key={ev.eventId} value={ev.eventId}>
                      {ev.title} ({new Date(ev.startAt).toLocaleDateString()})
                    </option>
                  ))}
                </select>
              </div>

              <div style={{ marginBottom: "1.5rem" }}>
                <label style={{ display: "block", fontSize: "0.875rem", fontWeight: 600, marginBottom: "0.25rem" }}>
                  Activity Description & Role *
                </label>
                <textarea
                  value={claimDescription}
                  onChange={(e) => setClaimDescription(e.target.value)}
                  rows={3}
                  placeholder="Describe your role and activities undertaken during this service..."
                  required
                  style={{ width: "100%", padding: "0.5rem", borderRadius: "0.375rem", border: "1px solid #cbd5e1" }}
                />
              </div>

              <div style={{ display: "flex", justifyContent: "flex-end", gap: "0.75rem" }}>
                <button
                  type="button"
                  onClick={() => setShowClaimModal(false)}
                  style={{ padding: "0.5rem 1rem", borderRadius: "0.375rem", border: "1px solid #cbd5e1", background: "#f8fafc", cursor: "pointer" }}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={submittingClaim}
                  style={{ padding: "0.5rem 1rem", borderRadius: "0.375rem", border: "none", background: "#1e40af", color: "#ffffff", fontWeight: 600, cursor: "pointer" }}
                >
                  {submittingClaim ? "Submitting..." : "Submit Claim"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Reject Modal */}
      {rejectingEntry && (
        <div style={{ position: "fixed", inset: 0, backgroundColor: "rgba(0,0,0,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#ffffff", padding: "2rem", borderRadius: "0.75rem", width: "90%", maxWidth: "450px", boxShadow: "0 20px 25px -5px rgba(0,0,0,0.1)" }}>
            <h3 style={{ margin: "0 0 0.5rem", fontSize: "1.25rem", fontWeight: 700, color: "#991b1b" }}>
              Reject Claim
            </h3>
            <p style={{ margin: "0 0 1rem", fontSize: "0.875rem", color: "#64748b" }}>
              Reject claim for <strong>{rejectingEntry.volunteerName}</strong> ({rejectingEntry.hours} hrs). Please state the audit rationale.
            </p>
            <form onSubmit={handleRejectClaim}>
              <div style={{ marginBottom: "1.5rem" }}>
                <label style={{ display: "block", fontSize: "0.875rem", fontWeight: 600, marginBottom: "0.25rem" }}>
                  Rejection Reason *
                </label>
                <textarea
                  value={rejectionReason}
                  onChange={(e) => setRejectionReason(e.target.value)}
                  rows={3}
                  placeholder="e.g. Activity not eligible under NSS guidelines..."
                  required
                  style={{ width: "100%", padding: "0.5rem", borderRadius: "0.375rem", border: "1px solid #cbd5e1" }}
                />
              </div>

              <div style={{ display: "flex", justifyContent: "flex-end", gap: "0.75rem" }}>
                <button
                  type="button"
                  onClick={() => setRejectingEntry(null)}
                  style={{ padding: "0.5rem 1rem", borderRadius: "0.375rem", border: "1px solid #cbd5e1", background: "#f8fafc", cursor: "pointer" }}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={reviewing}
                  style={{ padding: "0.5rem 1rem", borderRadius: "0.375rem", border: "none", background: "#dc2626", color: "#ffffff", fontWeight: 600, cursor: "pointer" }}
                >
                  {reviewing ? "Processing..." : "Confirm Rejection"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
