import React, { useState, useEffect } from "react";
import { apiRequest, apiRequestBlob, ApiError } from "../api/client";
import { CustomSelect } from "../components/CustomSelect";
import { SearchBar } from "../components/SearchBar";
import { CustomDatePicker } from "../components/CustomDatePicker";
import { useAuth } from "../context/AuthContext";


interface UnitPerformance {
  unitId: string;
  unitName: string;
  unitNumber: string;
  officerName: string;
  volunteerCount: number;
  eventCount: number;
  totalServiceHours: number;
}

interface InstitutionalMetrics {
  totalVolunteers: number;
  activeVolunteers: number;
  totalUnits: number;
  totalEvents: number;
  completedEvents: number;
  totalServiceHours: number;
  unitPerformance: UnitPerformance[];
}

interface Unit {
  unitId: string;
  unitName: string;
  unitNumber: string;
}

interface VolunteerPreviewItem {
  volunteerId: string;
  name: string;
  collegeId: string;
  department: string;
  yearOfStudy: number;
  status: string;
  unitName?: string | null;
}

interface EventPreviewItem {
  eventId: string;
  title: string;
  eventType: string;
  unitName: string;
  startAt: string;
  endAt: string;
  venue: string;
  capacity: number;
  registeredCount: number;
  status: string;
}

type ReportTab = "overview" | "volunteers" | "events" | "serviceHours";

export const Reports: React.FC = () => {
  const { hasCapability, isCoordinatorOrOfficer } = useAuth();
  const canExport = isCoordinatorOrOfficer || hasCapability("REPORTS_EXPORT");

  const [metrics, setMetrics] = useState<InstitutionalMetrics | null>(null);
  const [units, setUnits] = useState<Unit[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Filters
  const [activeTab, setActiveTab] = useState<ReportTab>("overview");
  const [unitId, setUnitId] = useState("");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [searchQuery, setSearchQuery] = useState("");

  // Preview Data
  const [volunteerPreviews, setVolunteerPreviews] = useState<VolunteerPreviewItem[]>([]);
  const [eventPreviews, setEventPreviews] = useState<EventPreviewItem[]>([]);
  const [previewLoading, setPreviewLoading] = useState(false);

  // Export State
  const [downloading, setDownloading] = useState<string | null>(null);
  const [exportNotice, setExportNotice] = useState<string | null>(null);

  const fetchMetricsAndUnits = async () => {
    try {
      setLoading(true);
      setError(null);
      const [metricsData, unitsData] = await Promise.all([
        apiRequest<InstitutionalMetrics>("/reports/metrics"),
        apiRequest<Unit[]>("/units"),
      ]);
      setMetrics(metricsData);
      setUnits(unitsData || []);
    } catch (err: unknown) {
      const apiErr = err as ApiError;
      setError(apiErr.message || "Failed to load institutional analytics.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchMetricsAndUnits();
  }, []);

  // Fetch previews when tab changes
  useEffect(() => {
    const fetchPreviews = async () => {
      if (activeTab === "volunteers") {
        setPreviewLoading(true);
        try {
          let url = "/volunteers?size=50";
          if (unitId) url += "&unitId=" + encodeURIComponent(unitId);
          if (statusFilter) url += "&status=" + encodeURIComponent(statusFilter);
          const page = await apiRequest<{ content: VolunteerPreviewItem[] }>(url);
          setVolunteerPreviews(page.content || []);
        } catch {
          setVolunteerPreviews([]);
        } finally {
          setPreviewLoading(false);
        }
      } else if (activeTab === "events") {
        setPreviewLoading(true);
        try {
          let url = "/events?size=50";
          if (unitId) url += "&unitId=" + encodeURIComponent(unitId);
          if (statusFilter) url += "&status=" + encodeURIComponent(statusFilter);
          const page = await apiRequest<{ content: EventPreviewItem[] }>(url);
          setEventPreviews(page.content || []);
        } catch {
          setEventPreviews([]);
        } finally {
          setPreviewLoading(false);
        }
      }
    };

    fetchPreviews();
  }, [activeTab, unitId, statusFilter]);

  const applyPreset = (preset: "ALL_TIME" | "AY_2025" | "LAST_90" | "LAST_30") => {
    const now = new Date();
    if (preset === "ALL_TIME") {
      setFromDate("");
      setToDate("");
    } else if (preset === "AY_2025") {
      setFromDate("2025-06-01");
      setToDate("2026-05-31");
    } else if (preset === "LAST_90") {
      const d = new Date();
      d.setDate(d.getDate() - 90);
      setFromDate(d.toISOString().split("T")[0]);
      setToDate(now.toISOString().split("T")[0]);
    } else if (preset === "LAST_30") {
      const d = new Date();
      d.setDate(d.getDate() - 30);
      setFromDate(d.toISOString().split("T")[0]);
      setToDate(now.toISOString().split("T")[0]);
    }
  };

  const handleDownloadCsv = async (endpoint: string, baseFilename: string) => {
    try {
      setDownloading(baseFilename);
      setExportNotice(null);

      const params = new URLSearchParams();
      if (unitId) params.append("unitId", unitId);
      if (statusFilter) params.append("status", statusFilter);
      if (fromDate) params.append("startDate", new Date(fromDate).toISOString());
      if (toDate) params.append("endDate", new Date(toDate).toISOString());

      const queryString = params.toString() ? `?${params.toString()}` : "";

      const blob = await apiRequestBlob(`/reports/export/${endpoint}${queryString}`);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = baseFilename;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);

      setExportNotice(`Export generated successfully: ${baseFilename}`);
    } catch (err: unknown) {
      const apiErr = err as ApiError;
      alert("Failed to export report: " + (apiErr.message || "Network error"));
    } finally {
      setDownloading(null);
    }
  };

  // Client search filtering on previews
  const filteredVolunteers = volunteerPreviews.filter((v) => {
    if (!searchQuery.trim()) return true;
    const q = searchQuery.toLowerCase();
    return (
      v.name.toLowerCase().includes(q) ||
      v.collegeId.toLowerCase().includes(q) ||
      v.department.toLowerCase().includes(q)
    );
  });

  const filteredEvents = eventPreviews.filter((e) => {
    if (!searchQuery.trim()) return true;
    const q = searchQuery.toLowerCase();
    return (
      e.title.toLowerCase().includes(q) ||
      e.venue.toLowerCase().includes(q) ||
      e.unitName.toLowerCase().includes(q)
    );
  });

  return (
    <div className="page-container">
      {/* Page Header */}
      <div className="page-header">
        <div>
          <h1>Reports &amp; Institutional Analytics</h1>
          <p className="subtitle">
            Accreditation compliance lists, NSS unit comparative matrices, and filtered audit exports.
          </p>
        </div>
      </div>

      {error && (
        <div
          style={{
            padding: "1rem",
            backgroundColor: "#fef2f2",
            color: "#991b1b",
            borderRadius: "0.5rem",
            marginBottom: "1.5rem",
            border: "1px solid #fecaca",
          }}
        >
          {error}
        </div>
      )}

      {exportNotice && (
        <div
          style={{
            padding: "0.75rem 1rem",
            backgroundColor: "#f0fdf4",
            color: "#166534",
            borderRadius: "0.5rem",
            marginBottom: "1.5rem",
            border: "1px solid #bbf7d0",
            fontWeight: 600,
            fontSize: "0.9rem",
          }}
        >
          {exportNotice}
        </div>
      )}

      {/* Filter Bar */}
      <div className="section-card" style={{ marginBottom: "1.5rem", padding: "1.25rem" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1rem", flexWrap: "wrap", gap: "0.5rem" }}>
          <strong style={{ fontSize: "0.95rem", color: "#1e293b" }}>Report Scope &amp; Date Range Filters</strong>
          {/* Preset Buttons */}
          <div style={{ display: "flex", gap: "0.35rem", flexWrap: "wrap" }}>
            <button
              type="button"
              onClick={() => applyPreset("ALL_TIME")}
              className="btn-secondary-sm"
              style={{ fontSize: "0.75rem", padding: "0.25rem 0.5rem" }}
            >
              All Time
            </button>
            <button
              type="button"
              onClick={() => applyPreset("AY_2025")}
              className="btn-secondary-sm"
              style={{ fontSize: "0.75rem", padding: "0.25rem 0.5rem" }}
            >
              AY 2025-26
            </button>
            <button
              type="button"
              onClick={() => applyPreset("LAST_90")}
              className="btn-secondary-sm"
              style={{ fontSize: "0.75rem", padding: "0.25rem 0.5rem" }}
            >
              Past 90 Days
            </button>
            <button
              type="button"
              onClick={() => applyPreset("LAST_30")}
              className="btn-secondary-sm"
              style={{ fontSize: "0.75rem", padding: "0.25rem 0.5rem" }}
            >
              Past 30 Days
            </button>
          </div>
        </div>

        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: "1rem" }}>
          <div className="form-group" style={{ margin: 0 }}>
            <label style={{ fontSize: "0.8rem", fontWeight: 600, color: "#64748b" }}>NSS Operational Unit</label>
            <CustomSelect
              value={unitId}
              onChange={setUnitId}
              options={[
                { value: "", label: "All Institutional Units" },
                ...units.map((u) => ({
                  value: u.unitId,
                  label: `${u.unitNumber} - ${u.unitName}`,
                })),
              ]}
              placeholder="All Institutional Units"
            />
          </div>

          <div className="form-group" style={{ margin: 0 }}>
            <label style={{ fontSize: "0.8rem", fontWeight: 600, color: "#64748b", marginBottom: "0.35rem", display: "block" }}>From Date</label>
            <CustomDatePicker
              value={fromDate}
              onChange={setFromDate}
              placeholder="From Date"
            />
          </div>

          <div className="form-group" style={{ margin: 0 }}>
            <label style={{ fontSize: "0.8rem", fontWeight: 600, color: "#64748b", marginBottom: "0.35rem", display: "block" }}>To Date</label>
            <CustomDatePicker
              value={toDate}
              onChange={setToDate}
              placeholder="To Date"
            />
          </div>

          <div className="form-group" style={{ margin: 0 }}>
            <label style={{ fontSize: "0.8rem", fontWeight: 600, color: "#64748b" }}>Status Filter</label>
            <CustomSelect
              value={statusFilter}
              onChange={setStatusFilter}
              options={[
                { value: "", label: "All Statuses" },
                { value: "ACTIVE", label: "ACTIVE" },
                { value: "PENDING_APPROVAL", label: "PENDING_APPROVAL" },
                { value: "COMPLETED", label: "COMPLETED" },
                { value: "OPEN", label: "OPEN" },
              ]}
              placeholder="All Statuses"
            />
          </div>
        </div>
      </div>

      {/* Export Action Cards */}
      {canExport && (
        <div style={{ background: "#f8fafc", padding: "1.5rem", borderRadius: "0.75rem", border: "1px solid #e2e8f0", marginBottom: "2rem" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1rem", flexWrap: "wrap", gap: "0.5rem" }}>
            <div>
              <h3 style={{ margin: 0, fontSize: "1.125rem", fontWeight: 700, color: "#1e293b" }}>
                Accreditation Data Export Center
              </h3>
              <p style={{ margin: "0.2rem 0 0", fontSize: "0.85rem", color: "#64748b" }}>
                Generates audit-ready CSV datasets applying active unit and date filters.
              </p>
            </div>
            {downloading && (
              <span style={{ fontSize: "0.85rem", color: "#1e40af", fontWeight: 600 }}>
                Generating and streaming CSV download...
              </span>
            )}
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))", gap: "1rem" }}>
            <div style={{ background: "#ffffff", padding: "1.25rem", borderRadius: "0.5rem", border: "1px solid #cbd5e1", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <div>
                <div style={{ fontWeight: 700, fontSize: "0.95rem", color: "#0f172a" }}>Volunteers Directory</div>
                <div style={{ fontSize: "0.75rem", color: "#64748b", marginTop: "0.2rem" }}>Enrollment, department &amp; status</div>
              </div>
              <button
                onClick={() => handleDownloadCsv("volunteers", "nss_volunteers_lists.csv")}
                disabled={downloading === "nss_volunteers_lists.csv"}
                className="btn-primary-sm"
              >
                {downloading === "nss_volunteers_lists.csv" ? "Exporting..." : "Export CSV"}
              </button>
            </div>

            <div style={{ background: "#ffffff", padding: "1.25rem", borderRadius: "0.5rem", border: "1px solid #cbd5e1", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <div>
                <div style={{ fontWeight: 700, fontSize: "0.95rem", color: "#0f172a" }}>Events &amp; Turnout</div>
                <div style={{ fontSize: "0.75rem", color: "#64748b", marginTop: "0.2rem" }}>Schedule, capacity &amp; registered counts</div>
              </div>
              <button
                onClick={() => handleDownloadCsv("events", "nss_events_lists.csv")}
                disabled={downloading === "nss_events_lists.csv"}
                className="btn-primary-sm"
              >
                {downloading === "nss_events_lists.csv" ? "Exporting..." : "Export CSV"}
              </button>
            </div>

            <div style={{ background: "#ffffff", padding: "1.25rem", borderRadius: "0.5rem", border: "1px solid #cbd5e1", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <div>
                <div style={{ fontWeight: 700, fontSize: "0.95rem", color: "#0f172a" }}>Service Hours Ledger</div>
                <div style={{ fontSize: "0.75rem", color: "#64748b", marginTop: "0.2rem" }}>Verified 120-hour accreditation credit</div>
              </div>
              <button
                onClick={() => handleDownloadCsv("service-hours", "nss_service_hours_accreditation.csv")}
                disabled={downloading === "nss_service_hours_accreditation.csv"}
                className="btn-primary-sm"
              >
                {downloading === "nss_service_hours_accreditation.csv" ? "Exporting..." : "Export CSV"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Preview Section Tabs */}
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
          Institutional Overview
        </button>
        <button
          type="button"
          onClick={() => setActiveTab("volunteers")}
          style={{
            padding: "0.75rem 1.25rem",
            border: "none",
            background: "none",
            borderBottom: activeTab === "volunteers" ? "2px solid #1e40af" : "2px solid transparent",
            color: activeTab === "volunteers" ? "#1e40af" : "#64748b",
            fontWeight: activeTab === "volunteers" ? 700 : 500,
            fontSize: "0.95rem",
            cursor: "pointer",
            whiteSpace: "nowrap",
          }}
        >
          Volunteers List Preview
        </button>
        <button
          type="button"
          onClick={() => setActiveTab("events")}
          style={{
            padding: "0.75rem 1.25rem",
            border: "none",
            background: "none",
            borderBottom: activeTab === "events" ? "2px solid #1e40af" : "2px solid transparent",
            color: activeTab === "events" ? "#1e40af" : "#64748b",
            fontWeight: activeTab === "events" ? 700 : 500,
            fontSize: "0.95rem",
            cursor: "pointer",
            whiteSpace: "nowrap",
          }}
        >
          Events Registry Preview
        </button>
      </div>

      {loading ? (
        <div style={{ textAlign: "center", padding: "3rem", color: "#64748b" }}>
          Compiling institutional metrics...
        </div>
      ) : activeTab === "overview" && metrics ? (
        <div>
          {/* Institutional KPI Cards */}
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(240px, 1fr))", gap: "1.5rem", marginBottom: "2.5rem" }}>
            <div style={{ background: "#ffffff", padding: "1.5rem", borderRadius: "0.75rem", border: "1px solid var(--border-color, #e2e8f0)", boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}>
              <div style={{ fontSize: "0.875rem", color: "var(--text-muted, #64748b)", fontWeight: 600 }}>Total Volunteers</div>
              <div style={{ fontSize: "2.25rem", fontWeight: 800, color: "#1e40af", marginTop: "0.25rem" }}>
                {metrics.totalVolunteers}
              </div>
              <div style={{ fontSize: "0.75rem", color: "#16a34a", marginTop: "0.5rem", fontWeight: 600 }}>
                {metrics.activeVolunteers} active in community units
              </div>
            </div>

            <div style={{ background: "#ffffff", padding: "1.5rem", borderRadius: "0.75rem", border: "1px solid var(--border-color, #e2e8f0)", boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}>
              <div style={{ fontSize: "0.875rem", color: "var(--text-muted, #64748b)", fontWeight: 600 }}>NSS Operational Units</div>
              <div style={{ fontSize: "2.25rem", fontWeight: 800, color: "#0f766e", marginTop: "0.25rem" }}>
                {metrics.totalUnits}
              </div>
              <div style={{ fontSize: "0.75rem", color: "#64748b", marginTop: "0.5rem" }}>
                Active faculty officer assignments
              </div>
            </div>

            <div style={{ background: "#ffffff", padding: "1.5rem", borderRadius: "0.75rem", border: "1px solid var(--border-color, #e2e8f0)", boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}>
              <div style={{ fontSize: "0.875rem", color: "var(--text-muted, #64748b)", fontWeight: 600 }}>Programmes Conducted</div>
              <div style={{ fontSize: "2.25rem", fontWeight: 800, color: "#7c3aed", marginTop: "0.25rem" }}>
                {metrics.totalEvents}
              </div>
              <div style={{ fontSize: "0.75rem", color: "#64748b", marginTop: "0.5rem" }}>
                {metrics.completedEvents} successfully completed
              </div>
            </div>

            <div style={{ background: "#ffffff", padding: "1.5rem", borderRadius: "0.75rem", border: "1px solid var(--border-color, #e2e8f0)", boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}>
              <div style={{ fontSize: "0.875rem", color: "var(--text-muted, #64748b)", fontWeight: 600 }}>Total Service Hours</div>
              <div style={{ fontSize: "2.25rem", fontWeight: 800, color: "#ea580c", marginTop: "0.25rem" }}>
                {metrics.totalServiceHours} <span style={{ fontSize: "1rem", fontWeight: 500 }}>hrs</span>
              </div>
              <div style={{ fontSize: "0.75rem", color: "#64748b", marginTop: "0.5rem" }}>
                Institutional accreditation verified
              </div>
            </div>
          </div>

          {/* Unit Performance Breakdown Table */}
          <div style={{ background: "#ffffff", borderRadius: "0.75rem", border: "1px solid var(--border-color, #e2e8f0)", overflow: "hidden", boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}>
            <div style={{ padding: "1rem 1.5rem", borderBottom: "1px solid var(--border-color, #e2e8f0)", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <h3 style={{ margin: 0, fontSize: "1.125rem", fontWeight: 700 }}>NSS Unit Details</h3>
              <span style={{ fontSize: "0.875rem", color: "#64748b" }}>{(metrics?.unitPerformance || []).length} Units Active</span>
            </div>

            <div style={{ overflowX: "auto" }}>
              <table style={{ width: "100%", borderCollapse: "collapse", textAlign: "left", fontSize: "0.875rem" }}>
                <thead>
                  <tr style={{ backgroundColor: "#f8fafc", borderBottom: "1px solid #e2e8f0", color: "#475569" }}>
                    <th style={{ padding: "0.75rem 1rem" }}>Unit Code</th>
                    <th style={{ padding: "0.75rem 1rem" }}>Unit Name</th>
                    <th style={{ padding: "0.75rem 1rem" }}>Programme Officer</th>
                    <th style={{ padding: "0.75rem 1rem", textAlign: "right" }}>Volunteers</th>
                    <th style={{ padding: "0.75rem 1rem", textAlign: "right" }}>Events</th>
                    <th style={{ padding: "0.75rem 1rem", textAlign: "right" }}>Approved Hours</th>
                  </tr>
                </thead>
                <tbody>
                  {(metrics?.unitPerformance || []).map((u) => (
                    <tr key={u.unitId} style={{ borderBottom: "1px solid #f1f5f9" }}>
                      <td style={{ padding: "0.75rem 1rem", fontWeight: 700, color: "#1e40af" }}>
                        {u.unitNumber}
                      </td>
                      <td style={{ padding: "0.75rem 1rem", fontWeight: 600, color: "#1e293b" }}>
                        {u.unitName}
                      </td>
                      <td style={{ padding: "0.75rem 1rem", color: "#475569" }}>
                        {u.officerName}
                      </td>
                      <td style={{ padding: "0.75rem 1rem", textAlign: "right", fontWeight: 600 }}>
                        {u.volunteerCount}
                      </td>
                      <td style={{ padding: "0.75rem 1rem", textAlign: "right", fontWeight: 600 }}>
                        {u.eventCount}
                      </td>
                      <td style={{ padding: "0.75rem 1rem", textAlign: "right", fontWeight: 800, color: "#0f766e" }}>
                        {u.totalServiceHours} hrs
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      ) : activeTab === "volunteers" ? (
        <div className="section-card">
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1rem", flexWrap: "wrap", gap: "0.75rem" }}>
            <div>
              <h3 style={{ margin: 0 }}>Volunteers List Preview</h3>
              <p style={{ margin: "0.2rem 0 0", fontSize: "0.85rem", color: "#64748b" }}>
                Showing up to 50 records matching current filter scope.
              </p>
            </div>
            <SearchBar
              placeholder="Search by name, college ID, department..."
              value={searchQuery}
              onChange={setSearchQuery}
              style={{ maxWidth: "340px", minWidth: "260px" }}
            />
          </div>

          {previewLoading ? (
            <p className="loading-state">Loading volunteers preview...</p>
          ) : filteredVolunteers.length === 0 ? (
            <div className="empty-state">
              <p>No volunteers match the selected filter criteria.</p>
            </div>
          ) : (
            <div className="units-table-wrapper">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>College ID</th>
                    <th>Full Name</th>
                    <th>Department</th>
                    <th>Year</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredVolunteers.map((v) => (
                    <tr key={v.volunteerId}>
                      <td style={{ fontWeight: 700 }}>{v.collegeId}</td>
                      <td>{v.name}</td>
                      <td>{v.department}</td>
                      <td>Year {v.yearOfStudy}</td>
                      <td>
                        <span
                          className={`badge ${v.status === "ACTIVE"
                            ? "badge-success"
                            : v.status === "PENDING_APPROVAL"
                              ? "badge-warning"
                              : "badge-muted"
                            }`}
                        >
                          {v.status}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      ) : activeTab === "events" ? (
        <div className="section-card">
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1rem", flexWrap: "wrap", gap: "0.75rem" }}>
            <div>
              <h3 style={{ margin: 0 }}>Events Registry Preview</h3>
              <p style={{ margin: "0.2rem 0 0", fontSize: "0.85rem", color: "#64748b" }}>
                Showing scheduled and completed activities matching current filter scope.
              </p>
            </div>
            <SearchBar
              placeholder="Search by title, venue, unit..."
              value={searchQuery}
              onChange={setSearchQuery}
              style={{ maxWidth: "340px", minWidth: "260px" }}
            />
          </div>

          {previewLoading ? (
            <p className="loading-state">Loading events preview...</p>
          ) : filteredEvents.length === 0 ? (
            <div className="empty-state">
              <p>No events match the selected filter criteria.</p>
            </div>
          ) : (
            <div className="units-table-wrapper">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Title</th>
                    <th>Type</th>
                    <th>Unit</th>
                    <th>Date &amp; Time</th>
                    <th>Venue</th>
                    <th>Registrations</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredEvents.map((e) => (
                    <tr key={e.eventId}>
                      <td>
                        <strong>{e.title}</strong>
                      </td>
                      <td>
                        <span className="badge badge-primary">{e.eventType}</span>
                      </td>
                      <td>{e.unitName}</td>
                      <td>{new Date(e.startAt).toLocaleDateString()}</td>
                      <td>{e.venue}</td>
                      <td>
                        {e.registeredCount} / {e.capacity}
                      </td>
                      <td>
                        <span className="badge badge-muted">{e.status}</span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      ) : null}
    </div>
  );
};
