import React, { useState, useEffect } from "react";
import { apiRequest } from "../api/client";

interface UnitPerformance {
  unitId: number;
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

export const Reports: React.FC = () => {
  const [metrics, setMetrics] = useState<InstitutionalMetrics | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [downloading, setDownloading] = useState<string | null>(null);

  useEffect(() => {
    const fetchMetrics = async () => {
      try {
        setLoading(true);
        setError(null);
        const data = await apiRequest<InstitutionalMetrics>("/reports/metrics");
        setMetrics(data);
      } catch (err: any) {
        setError(err.message || "Failed to load institutional analytics.");
      } finally {
        setLoading(false);
      }
    };
    fetchMetrics();
  }, []);

  const handleDownloadCsv = async (endpoint: string, filename: string) => {
    try {
      setDownloading(filename);
      const token = localStorage.getItem("nss_token");
      const res = await fetch(`/api/v1/reports/export/${endpoint}`, {
        headers: {
          Authorization: `Bearer ${token}`
        }
      });
      if (!res.ok) {
        throw new Error(`Export failed with status ${res.status}`);
      }
      const blob = await res.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
    } catch (err: any) {
      alert("Failed to export report: " + err.message);
    } finally {
      setDownloading(null);
    }
  };

  return (
    <div className="container" style={{ padding: "2rem 1rem", maxWidth: "1200px", margin: "0 auto" }}>
      {/* Page Header */}
      <div style={{ marginBottom: "2rem" }}>
        <h1 style={{ margin: 0, fontSize: "1.875rem", fontWeight: 700, color: "var(--text-main, #0f172a)" }}>
          Reports & Institutional Analytics
        </h1>
        <p style={{ margin: "0.25rem 0 0", color: "var(--text-muted, #64748b)", fontSize: "0.95rem" }}>
          Accreditation performance metrics, NSS unit comparisons, and institutional CSV data exports
        </p>
      </div>

      {error && (
        <div style={{ padding: "1rem", backgroundColor: "#fef2f2", color: "#991b1b", borderRadius: "0.5rem", marginBottom: "1.5rem", border: "1px solid #fecaca" }}>
          {error}
        </div>
      )}

      {loading && (
        <div style={{ textAlign: "center", padding: "4rem", color: "#64748b" }}>
          Compiling institutional metrics and accreditation rosters...
        </div>
      )}

      {!loading && metrics && (
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

          {/* Export Center Cards */}
          <div style={{ background: "#f8fafc", padding: "1.5rem", borderRadius: "0.75rem", border: "1px solid #e2e8f0", marginBottom: "2.5rem" }}>
            <h3 style={{ margin: "0 0 1rem", fontSize: "1.125rem", fontWeight: 700, color: "#1e293b" }}>
              Accreditation Data Export Center
            </h3>
            <p style={{ margin: "0 0 1.25rem", fontSize: "0.875rem", color: "#64748b" }}>
              Download complete, timestamped CSV rosters for university audits, NAAC/NIRF reporting, and compliance documentation.
            </p>

            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))", gap: "1rem" }}>
              <div style={{ background: "#ffffff", padding: "1.25rem", borderRadius: "0.5rem", border: "1px solid #cbd5e1", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <div>
                  <div style={{ fontWeight: 700, fontSize: "0.95rem", color: "#0f172a" }}>Volunteers Directory</div>
                  <div style={{ fontSize: "0.75rem", color: "#64748b", marginTop: "0.2rem" }}>Full student enrollment & unit roster</div>
                </div>
                <button
                  onClick={() => handleDownloadCsv("volunteers", "nss_volunteers_roster.csv")}
                  disabled={downloading === "nss_volunteers_roster.csv"}
                  style={{
                    backgroundColor: "#1e40af",
                    color: "#ffffff",
                    border: "none",
                    borderRadius: "0.375rem",
                    padding: "0.5rem 1rem",
                    fontSize: "0.8125rem",
                    fontWeight: 600,
                    cursor: "pointer"
                  }}
                >
                  {downloading === "nss_volunteers_roster.csv" ? "Exporting..." : "Download CSV"}
                </button>
              </div>

              <div style={{ background: "#ffffff", padding: "1.25rem", borderRadius: "0.5rem", border: "1px solid #cbd5e1", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <div>
                  <div style={{ fontWeight: 700, fontSize: "0.95rem", color: "#0f172a" }}>Events & Attendance</div>
                  <div style={{ fontSize: "0.75rem", color: "#64748b", marginTop: "0.2rem" }}>Event schedule & registrations report</div>
                </div>
                <button
                  onClick={() => handleDownloadCsv("events", "nss_events_roster.csv")}
                  disabled={downloading === "nss_events_roster.csv"}
                  style={{
                    backgroundColor: "#1e40af",
                    color: "#ffffff",
                    border: "none",
                    borderRadius: "0.375rem",
                    padding: "0.5rem 1rem",
                    fontSize: "0.8125rem",
                    fontWeight: 600,
                    cursor: "pointer"
                  }}
                >
                  {downloading === "nss_events_roster.csv" ? "Exporting..." : "Download CSV"}
                </button>
              </div>

              <div style={{ background: "#ffffff", padding: "1.25rem", borderRadius: "0.5rem", border: "1px solid #cbd5e1", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <div>
                  <div style={{ fontWeight: 700, fontSize: "0.95rem", color: "#0f172a" }}>Service Hours Ledger</div>
                  <div style={{ fontSize: "0.75rem", color: "#64748b", marginTop: "0.2rem" }}>Verified accreditation hours records</div>
                </div>
                <button
                  onClick={() => handleDownloadCsv("service-hours", "nss_service_hours_accreditation.csv")}
                  disabled={downloading === "nss_service_hours_accreditation.csv"}
                  style={{
                    backgroundColor: "#1e40af",
                    color: "#ffffff",
                    border: "none",
                    borderRadius: "0.375rem",
                    padding: "0.5rem 1rem",
                    fontSize: "0.8125rem",
                    fontWeight: 600,
                    cursor: "pointer"
                  }}
                >
                  {downloading === "nss_service_hours_accreditation.csv" ? "Exporting..." : "Download CSV"}
                </button>
              </div>
            </div>
          </div>

          {/* Unit Performance Breakdown Table */}
          <div style={{ background: "#ffffff", borderRadius: "0.75rem", border: "1px solid var(--border-color, #e2e8f0)", overflow: "hidden", boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}>
            <div style={{ padding: "1rem 1.5rem", borderBottom: "1px solid var(--border-color, #e2e8f0)", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <h3 style={{ margin: 0, fontSize: "1.125rem", fontWeight: 700 }}>NSS Unit Comparative Matrix</h3>
              <span style={{ fontSize: "0.875rem", color: "#64748b" }}>{metrics.unitPerformance.length} Units Active</span>
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
                  {metrics.unitPerformance.map((u) => (
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
      )}
    </div>
  );
};
