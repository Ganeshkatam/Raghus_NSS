import React, { useState, useEffect, useCallback } from "react";
import { useAuth } from "../context/AuthContext";
import { apiRequest } from "../api/client";
import { CustomSelect } from "../components/CustomSelect";
import { SearchBar } from "../components/SearchBar";
import { CustomDatePicker } from "../components/CustomDatePicker";

interface ServiceHourEntry {
  entryId: string;
  volunteerId: string;
  volunteerName: string;
  rollNumber: string;
  eventId: string | null;
  eventTitle: string | null;
  hours: number;
  status: "APPROVED" | "PENDING" | "REJECTED";
  approvedByName: string | null;
  description: string;
  category?: string;
  evidenceNote?: string | null;
  activityDate?: string | null;
  createdAt: string;
}

interface PersonalSummary {
  volunteerId: string;
  volunteerName: string;
  rollNumber: string;
  totalApprovedHours: number;
  progressPercentage: number;
  regularHours: number;
  communityHours: number;
  otherHours: number;
  approvedCount: number;
  pendingCount: number;
  entries: ServiceHourEntry[];
}

export const ServiceHours: React.FC = () => {
  const { user, isCoordinatorOrOfficer, hasCapability } = useAuth();
  const isOfficerOrAdmin = isCoordinatorOrOfficer || hasCapability("SERVICE_HOURS_MANAGE") || Boolean(user?.roles?.some((r: string) =>
    ["ADMIN", "FACULTY_COORDINATOR", "PROGRAMME_OFFICER"].includes(r)
  ));

  const [activeTab, setActiveTab] = useState<"my" | "pending">(isOfficerOrAdmin ? "pending" : "my");
  const [personalSummary, setPersonalSummary] = useState<PersonalSummary | null>(null);
  const [pendingClaims, setPendingClaims] = useState<ServiceHourEntry[]>([]);
  const [pendingSearch, setPendingSearch] = useState("");
  const [ledgerSearch, setLedgerSearch] = useState("");
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
  const [claimCategory, setClaimCategory] = useState("REGULAR_ACTIVITY");
  const [claimActivityDate, setClaimActivityDate] = useState("");
  const [claimEvidenceNote, setClaimEvidenceNote] = useState("");
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
      if (data) {
        setPersonalSummary({
          ...data,
          entries: Array.isArray(data.entries) ? data.entries : [],
        });
      } else {
        setPersonalSummary(null);
      }
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
      const list = await apiRequest<any>("/service-hours/pending");
      const safeList = Array.isArray(list)
        ? list
        : Array.isArray(list?.content)
          ? list.content
          : [];
      setPendingClaims(safeList);
    } catch (err: any) {
      setError(err.message || "Failed to load pending claims.");
    } finally {
      setLoading(false);
    }
  }, [isOfficerOrAdmin]);

  const loadEvents = useCallback(async () => {
    try {
      const res = await apiRequest<any>("/events?size=100");
      const safeEvents = Array.isArray(res)
        ? res
        : Array.isArray(res?.content)
          ? res.content
          : [];
      setEvents(safeEvents);
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
          eventId: claimEventId ? claimEventId : null,
          category: claimCategory,
          activityDate: claimActivityDate ? claimActivityDate : null,
          evidenceNote: claimEvidenceNote.trim() ? claimEvidenceNote.trim() : null
        })
      });
      setSuccess("Service hour claim submitted successfully. Awaiting coordinator review.");
      setShowClaimModal(false);
      setClaimHours("");
      setClaimDescription("");
      setClaimEventId("");
      setClaimCategory("REGULAR_ACTIVITY");
      setClaimActivityDate("");
      setClaimEvidenceNote("");
      loadMyHours();
    } catch (err: any) {
      setError(err.message || "Failed to submit claim.");
    } finally {
      setSubmittingClaim(false);
    }
  };

  const handleApproveClaim = async (entryId: string) => {
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

  const entriesList = Array.isArray(personalSummary?.entries) ? personalSummary.entries : [];
  const claimsList = Array.isArray(pendingClaims) ? pendingClaims : [];

  const filteredEntries = entriesList.filter((entry) => {
    if (!ledgerSearch.trim()) return true;
    const q = ledgerSearch.toLowerCase();
    return (
      (entry.eventTitle && entry.eventTitle.toLowerCase().includes(q)) ||
      (entry.description && entry.description.toLowerCase().includes(q)) ||
      (entry.category && entry.category.toLowerCase().includes(q)) ||
      (entry.approvedByName && entry.approvedByName.toLowerCase().includes(q))
    );
  });

  const filteredClaims = claimsList.filter((claim) => {
    if (!pendingSearch.trim()) return true;
    const q = pendingSearch.toLowerCase();
    return (
      claim.volunteerName.toLowerCase().includes(q) ||
      claim.rollNumber.toLowerCase().includes(q) ||
      (claim.eventTitle && claim.eventTitle.toLowerCase().includes(q)) ||
      (claim.description && claim.description.toLowerCase().includes(q))
    );
  });

  return (
    <div className="page-container">
      {/* Header */}
      <div className="page-header">
        <div>
          <h1>Service Hours Ledger</h1>
          <p className="subtitle">
            Institutional verified hours accumulation, milestone certifications, and audit tracking
          </p>
        </div>

        {/* Action button */}
        <div style={{ display: "flex", gap: "0.75rem", flexWrap: "wrap", alignItems: "center" }}>
          {(user?.roles?.includes("VOLUNTEER") || hasCapability("SERVICE_HOURS_LOG")) && (
            <button
              onClick={() => setShowClaimModal(true)}
              className="btn-primary"
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
            {claimsList.length > 0 && (
              <span style={{ backgroundColor: "#ef4444", color: "#ffffff", borderRadius: "9999px", padding: "0.15rem 0.5rem", fontSize: "0.75rem", fontWeight: 700 }}>
                {claimsList.length}
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
              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: "1.25rem", marginBottom: "2rem" }}>
                <div style={{ background: "#ffffff", padding: "1.5rem", borderRadius: "0.75rem", border: "1px solid var(--border-color, #e2e8f0)", boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}>
                  <div style={{ fontSize: "0.875rem", color: "var(--text-muted, #64748b)", fontWeight: 600 }}>Total Verified Hours</div>
                  <div style={{ fontSize: "2rem", fontWeight: 800, color: "#1e40af", marginTop: "0.25rem" }}>
                    {personalSummary.totalApprovedHours} <span style={{ fontSize: "1rem", fontWeight: 500, color: "#64748b" }}>/ 120 hrs</span>
                  </div>
                  {/* Progress bar towards 120 hrs NSS certificate */}
                  <div style={{ width: "100%", height: "8px", backgroundColor: "#e2e8f0", borderRadius: "4px", marginTop: "0.75rem", overflow: "hidden" }}>
                    <div
                      style={{
                        height: "100%",
                        width: `${personalSummary.progressPercentage || Math.min(100, (personalSummary.totalApprovedHours / 120) * 100)}%`,
                        backgroundColor: "#1e40af",
                        borderRadius: "4px"
                      }}
                    />
                  </div>
                  <div style={{ fontSize: "0.75rem", color: "#64748b", marginTop: "0.5rem" }}>
                    {personalSummary.progressPercentage}% of 120-hr NSS accreditation requirement
                  </div>
                </div>

                <div style={{ background: "#ffffff", padding: "1.5rem", borderRadius: "0.75rem", border: "1px solid var(--border-color, #e2e8f0)", boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}>
                  <div style={{ fontSize: "0.875rem", color: "var(--text-muted, #64748b)", fontWeight: 600 }}>Regular Activity</div>
                  <div style={{ fontSize: "2rem", fontWeight: 800, color: "#0284c7", marginTop: "0.25rem" }}>
                    {personalSummary.regularHours || 0} <span style={{ fontSize: "0.875rem", fontWeight: 500, color: "#64748b" }}>hrs</span>
                  </div>
                  <div style={{ fontSize: "0.75rem", color: "#64748b", marginTop: "0.5rem" }}>
                    Campus & institutional drives
                  </div>
                </div>

                <div style={{ background: "#ffffff", padding: "1.5rem", borderRadius: "0.75rem", border: "1px solid var(--border-color, #e2e8f0)", boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}>
                  <div style={{ fontSize: "0.875rem", color: "var(--text-muted, #64748b)", fontWeight: 600 }}>Community Outreach</div>
                  <div style={{ fontSize: "2rem", fontWeight: 800, color: "#166534", marginTop: "0.25rem" }}>
                    {personalSummary.communityHours || 0} <span style={{ fontSize: "0.875rem", fontWeight: 500, color: "#64748b" }}>hrs</span>
                  </div>
                  <div style={{ fontSize: "0.75rem", color: "#64748b", marginTop: "0.5rem" }}>
                    Village immersion & social work
                  </div>
                </div>

                <div style={{ background: "#ffffff", padding: "1.5rem", borderRadius: "0.75rem", border: "1px solid var(--border-color, #e2e8f0)", boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}>
                  <div style={{ fontSize: "0.875rem", color: "var(--text-muted, #64748b)", fontWeight: 600 }}>Blood & Special Projects</div>
                  <div style={{ fontSize: "2rem", fontWeight: 800, color: "#7c3aed", marginTop: "0.25rem" }}>
                    {personalSummary.otherHours || 0} <span style={{ fontSize: "0.875rem", fontWeight: 500, color: "#64748b" }}>hrs</span>
                  </div>
                  <div style={{ fontSize: "0.75rem", color: "#64748b", marginTop: "0.5rem" }}>
                    {personalSummary.approvedCount} approved, {personalSummary.pendingCount} pending
                  </div>
                </div>
              </div>

              {/* Entries Table */}
              <div style={{ background: "#ffffff", borderRadius: "0.75rem", border: "1px solid var(--border-color, #e2e8f0)", overflow: "hidden", boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}>
                <div style={{ padding: "1rem 1.5rem", borderBottom: "1px solid var(--border-color, #e2e8f0)", display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: "0.75rem" }}>
                  <div>
                    <h3 style={{ margin: 0, fontSize: "1.125rem", fontWeight: 700 }}>Activity Ledger Entries</h3>
                    <span style={{ fontSize: "0.85rem", color: "#64748b" }}>
                      {filteredEntries.length} of {entriesList.length} Total records
                    </span>
                  </div>
                  {entriesList.length > 0 && (
                    <div style={{ minWidth: "240px", maxWidth: "340px" }}>
                      <SearchBar
                        value={ledgerSearch}
                        onChange={setLedgerSearch}
                        placeholder="Search entries by title, note..."
                      />
                    </div>
                  )}
                </div>

                {entriesList.length === 0 ? (
                  <div style={{ padding: "3rem", textAlign: "center", color: "#64748b" }}>
                    No service hour entries found on your ledger yet. Attend events or submit manual claims to accrue hours.
                  </div>
                ) : filteredEntries.length === 0 ? (
                  <div style={{ padding: "3rem", textAlign: "center", color: "#64748b" }}>
                    No ledger entries match your search query.
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
                        {filteredEntries.map((entry) => (
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
          <div style={{ padding: "1rem 1.5rem", borderBottom: "1px solid var(--border-color, #e2e8f0)", display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: "0.75rem" }}>
            <div>
              <h3 style={{ margin: 0, fontSize: "1.125rem", fontWeight: 700 }}>Pending Review Queue</h3>
              <span style={{ fontSize: "0.85rem", color: "#64748b" }}>
                {filteredClaims.length} of {claimsList.length} Claims awaiting action
              </span>
            </div>
            {claimsList.length > 0 && (
              <div style={{ minWidth: "260px", maxWidth: "360px" }}>
                <SearchBar
                  value={pendingSearch}
                  onChange={setPendingSearch}
                  placeholder="Search claims by volunteer, roll no..."
                />
              </div>
            )}
          </div>

          {claimsList.length === 0 ? (
            <div style={{ padding: "3rem", textAlign: "center", color: "#64748b" }}>
              No pending service hour claims to review at this time.
            </div>
          ) : filteredClaims.length === 0 ? (
            <div style={{ padding: "3rem", textAlign: "center", color: "#64748b" }}>
              No claims match your search query.
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
                  {filteredClaims.map((claim) => (
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
                            className="btn-success-sm"
                          >
                            Approve
                          </button>
                          <button
                            onClick={() => setRejectingEntry(claim)}
                            disabled={reviewing}
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
      )}

      {/* Claim Submission Modal */}
      {showClaimModal && (
        <div className="modal-backdrop">
          <div className="modal-card">
            <div className="modal-header">
              <h2>Log Service Hours Claim</h2>
              <button
                type="button"
                className="btn-close"
                onClick={() => setShowClaimModal(false)}
                aria-label="Close modal"
              >
                &times;
              </button>
            </div>
            <div className="modal-body">
              <form onSubmit={handleSubmitClaim} className="form-stack">
                <div className="form-group">
                  <label htmlFor="claimHours">Hours to Claim *</label>
                  <input
                    id="claimHours"
                    type="number"
                    step="0.25"
                    min="0.25"
                    max="24.0"
                    value={claimHours}
                    onChange={(e) => setClaimHours(e.target.value)}
                    placeholder="e.g. 4.00"
                    required
                  />
                </div>

                <div className="form-group">
                  <label htmlFor="claimCategory">Hour Category *</label>
                  <CustomSelect
                    id="claimCategory"
                    value={claimCategory}
                    onChange={setClaimCategory}
                    options={[
                      { value: "REGULAR_ACTIVITY", label: "Regular Activity (Campus & Institutional Drives)" },
                      { value: "COMMUNITY_OUTREACH", label: "Community Outreach (Village & Field Work)" },
                      { value: "BLOOD_DONATION", label: "Blood Donation Camp" },
                      { value: "SPECIAL_PROJECT", label: "Special Project / State Initiative" },
                    ]}
                    placeholder="Select category"
                  />
                </div>

                <div className="form-group">
                  <label htmlFor="claimActivityDate" style={{ display: "block", marginBottom: "0.35rem" }}>Activity Date</label>
                  <CustomDatePicker
                    id="claimActivityDate"
                    value={claimActivityDate}
                    onChange={setClaimActivityDate}
                    placeholder="Select activity date..."
                  />
                </div>

                <div className="form-group">
                  <label htmlFor="claimEventId">Associated Event (Optional)</label>
                  <CustomSelect
                    id="claimEventId"
                    value={claimEventId}
                    onChange={setClaimEventId}
                    options={[
                      { value: "", label: "Independent Community Service Activity" },
                      ...events.map((ev) => ({
                        value: ev.eventId,
                        label: `${ev.title} (${new Date(ev.startAt).toLocaleDateString()})`,
                      })),
                    ]}
                    placeholder="Independent Community Service Activity"
                  />
                </div>

                <div className="form-group">
                  <label htmlFor="claimDescription">Activity Description &amp; Role *</label>
                  <textarea
                    id="claimDescription"
                    value={claimDescription}
                    onChange={(e) => setClaimDescription(e.target.value)}
                    rows={3}
                    placeholder="Describe your role and activities undertaken during this service..."
                    required
                  />
                </div>

                <div className="form-group">
                  <label htmlFor="claimEvidenceNote">Evidence / Verification Note (Optional)</label>
                  <textarea
                    id="claimEvidenceNote"
                    value={claimEvidenceNote}
                    onChange={(e) => setClaimEvidenceNote(e.target.value)}
                    rows={2}
                    placeholder="Reference contact, certificate ID, or supporting details..."
                  />
                </div>

                <div className="modal-actions">
                  <button
                    type="button"
                    onClick={() => setShowClaimModal(false)}
                    className="btn-secondary"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={submittingClaim}
                    className="btn-primary"
                  >
                    {submittingClaim ? "Submitting..." : "Submit Claim"}
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      )}

      {/* Reject Modal */}
      {rejectingEntry && (
        <div className="modal-backdrop">
          <div className="modal-card">
            <div className="modal-header">
              <h2 style={{ color: "#991b1b" }}>Reject Claim</h2>
              <button
                type="button"
                className="btn-close"
                onClick={() => setRejectingEntry(null)}
                aria-label="Close modal"
              >
                &times;
              </button>
            </div>
            <div className="modal-body">
              <p style={{ margin: "0 0 1rem", fontSize: "0.875rem", color: "#64748b" }}>
                Reject claim for <strong>{rejectingEntry.volunteerName}</strong> ({rejectingEntry.hours} hrs). Please state the audit rationale.
              </p>
              <form onSubmit={handleRejectClaim} className="form-stack">
                <div className="form-group">
                  <label htmlFor="rejectionReason">Rejection Reason *</label>
                  <textarea
                    id="rejectionReason"
                    value={rejectionReason}
                    onChange={(e) => setRejectionReason(e.target.value)}
                    rows={3}
                    placeholder="e.g. Activity not eligible under NSS guidelines..."
                    required
                  />
                </div>

                <div className="modal-actions">
                  <button
                    type="button"
                    onClick={() => setRejectingEntry(null)}
                    className="btn-secondary"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={reviewing}
                    className="btn-danger"
                  >
                    {reviewing ? "Processing..." : "Confirm Rejection"}
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
