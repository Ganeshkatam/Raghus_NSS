import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { apiRequest } from "../../api/client";

interface UnitSummary {
  unitId: string;
  unitName: string;
  unitNumber: string;
  officerEmail: string | null;
  officerId: string | null;
  activeMemberCount: number;
  capacity?: number | null;
}

interface EventItem {
  eventId: string;
  unitId: string;
  unitName: string;
  title: string;
  eventType: string;
  startAt: string;
  venue: string;
  capacity: number;
  registeredCount: number;
  remainingCapacity: number;
  status: string;
}

export const ProgrammeOfficerDashboard: React.FC = () => {
  const { user } = useAuth();
  const [assignedUnit, setAssignedUnit] = useState<UnitSummary | null>(null);
  const [events, setEvents] = useState<EventItem[]>([]);
  const [pendingApprovalsCount, setPendingApprovalsCount] = useState<number>(0);
  const [pendingClaimsCount, setPendingClaimsCount] = useState<number>(0);
  const [pendingCorrectionsCount, setPendingCorrectionsCount] = useState<number>(0);
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    async function loadOfficerData() {
      setLoading(true);
      try {
        const [summaryData, eventsData] = await Promise.all([
          apiRequest<any>("/dashboard/summary").catch(() => null),
          apiRequest<{ content: EventItem[] }>("/events?size=10").catch(() => ({ content: [] })),
        ]);

        if (summaryData?.officerData) {
          const od = summaryData.officerData;
          if (od.unitId) {
            setAssignedUnit({
              unitId: od.unitId,
              unitName: od.unitName,
              unitNumber: od.unitNumber,
              officerEmail: user?.email || null,
              officerId: null,
              activeMemberCount: od.activeMemberCount || 0,
              capacity: od.capacity ?? null,
            });
          }
          setPendingApprovalsCount(od.pendingApprovals || 0);
          setPendingClaimsCount(od.pendingClaims || 0);
          setPendingCorrectionsCount(od.pendingCorrections || 0);
        }

        const eventList = Array.isArray(eventsData)
          ? eventsData
          : Array.isArray((eventsData as any)?.content)
          ? (eventsData as any).content
          : [];
        setEvents(eventList);
      } finally {
        setLoading(false);
      }
    }
    loadOfficerData();
  }, [user]);

  const unitCapacity = assignedUnit?.capacity ?? null;
  const enrolledCount = assignedUnit?.activeMemberCount || 0;
  const capacityPercent = unitCapacity && unitCapacity > 0
    ? Math.min(100, Math.round((enrolledCount / unitCapacity) * 100))
    : null;

  if (loading) {
    return <p className="loading-state">Loading operational officer dashboard...</p>;
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>
      {/* Header */}
      <div className="page-header">
        <div>
          <div className="breadcrumbs">
            <span>Raghu Engineering College</span>
            <span>/</span>
            <span className="current">Programme Officer Console</span>
          </div>
          <h1>{assignedUnit ? `${assignedUnit.unitName} (Unit ${assignedUnit.unitNumber})` : "Programme Operations"}</h1>
          <p className="subtitle">
            Officer in Charge: <strong>{user?.name}</strong>
            {unitCapacity ? (
              <>
                {" "}&bull; Capacity: <strong>{enrolledCount} / {unitCapacity} Enrolled Volunteers</strong> ({capacityPercent}%)
              </>
            ) : (
              <>
                {" "}&bull; Active Roster: <strong>{enrolledCount} Enrolled Volunteers</strong>
              </>
            )}
          </p>
        </div>
        <div className="card-actions">
          <Link to="/events" className="btn-primary">
            + Create Event
          </Link>
          <Link to="/attendance" className="btn-secondary">
            Attendance Console
          </Link>
          <Link to="/service-hours" className="btn-secondary">
            Review Claims
          </Link>
        </div>
      </div>

      {/* KPI Cards: Unit Capacity & Pending Action Queues */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: "1rem" }}>
        {/* Card 1: Unit Capacity */}
        <div className="section-card" style={{ padding: "1.25rem", borderLeft: "4px solid #1e40af" }}>
          <div className="cell-sub" style={{ textTransform: "uppercase", fontSize: "0.75rem", letterSpacing: "0.05em" }}>
            Unit Enrolled Members
          </div>
          <div style={{ fontSize: "2rem", fontWeight: 800, color: "#1e40af", marginTop: "0.25rem" }}>
            {enrolledCount} {unitCapacity ? <span style={{ fontSize: "1rem", color: "#64748b", fontWeight: 500 }}>/ {unitCapacity}</span> : null}
          </div>
          {unitCapacity && capacityPercent !== null ? (
            <>
              <div style={{ height: "6px", backgroundColor: "#e2e8f0", borderRadius: "9999px", overflow: "hidden", marginTop: "0.5rem" }}>
                <div
                  style={{
                    width: `${capacityPercent}%`,
                    height: "100%",
                    backgroundColor: capacityPercent >= 90 ? "#e11d48" : "#2563eb",
                  }}
                />
              </div>
              <span style={{ fontSize: "0.75rem", color: "#64748b", marginTop: "0.25rem", display: "block" }}>
                {capacityPercent}% institutional quota occupied
              </span>
            </>
          ) : (
            <span style={{ fontSize: "0.75rem", color: "#64748b", marginTop: "0.25rem", display: "block" }}>
              No maximum capacity limit configured
            </span>
          )}
        </div>

        {/* Card 2: Pending Volunteer Onboarding */}
        <div className="section-card" style={{ padding: "1.25rem", borderLeft: "4px solid #f59e0b" }}>
          <div className="cell-sub" style={{ textTransform: "uppercase", fontSize: "0.75rem", letterSpacing: "0.05em" }}>
            Pending Volunteer Approvals
          </div>
          <div style={{ fontSize: "2rem", fontWeight: 800, color: "#d97706", marginTop: "0.25rem" }}>
            {pendingApprovalsCount}
          </div>
          <Link to="/volunteers?status=PENDING_APPROVAL" className="table-action-link" style={{ fontSize: "0.8rem", marginTop: "0.5rem", display: "inline-block" }}>
            Open Onboarding Queue &rarr;
          </Link>
        </div>

        {/* Card 3: Pending Service-Hour Claims */}
        <div className="section-card" style={{ padding: "1.25rem", borderLeft: "4px solid #10b981" }}>
          <div className="cell-sub" style={{ textTransform: "uppercase", fontSize: "0.75rem", letterSpacing: "0.05em" }}>
            Pending Service Hour Claims
          </div>
          <div style={{ fontSize: "2rem", fontWeight: 800, color: "#059669", marginTop: "0.25rem" }}>
            {pendingClaimsCount}
          </div>
          <Link to="/service-hours" className="table-action-link" style={{ fontSize: "0.8rem", marginTop: "0.5rem", display: "inline-block" }}>
            Audit &amp; Approve Claims &rarr;
          </Link>
        </div>

        {/* Card 4: Attendance Corrections */}
        <div className="section-card" style={{ padding: "1.25rem", borderLeft: "4px solid #8b5cf6" }}>
          <div className="cell-sub" style={{ textTransform: "uppercase", fontSize: "0.75rem", letterSpacing: "0.05em" }}>
            Attendance Corrections
          </div>
          <div style={{ fontSize: "2rem", fontWeight: 800, color: "#7c3aed", marginTop: "0.25rem" }}>
            {pendingCorrectionsCount}
          </div>
          <Link to="/attendance" className="table-action-link" style={{ fontSize: "0.8rem", marginTop: "0.5rem", display: "inline-block" }}>
            Audit Corrections Queue &rarr;
          </Link>
        </div>
      </div>

      {/* Operational Workflows & Events */}
      <div className="grid-2-col">
        {/* Unit Events & Program Roster */}
        <div className="section-card">
          <div className="section-header">
            <div>
              <h3>Scheduled Unit Programmes</h3>
              <p className="subtitle">Live activities, seat reservations, and registration capacity.</p>
            </div>
            <Link to="/events" className="btn-secondary-sm">
              Event Manager
            </Link>
          </div>

          {events.length === 0 ? (
            <div className="empty-state">
              <p>No programmes scheduled for this unit.</p>
            </div>
          ) : (
            <div className="table-wrapper">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Programme</th>
                    <th>Date</th>
                    <th>Enrolled</th>
                    <th>State</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {events.slice(0, 5).map((ev) => (
                    <tr key={ev.eventId}>
                      <td>
                        <strong>{ev.title}</strong>
                        <div className="cell-sub">{ev.venue}</div>
                      </td>
                      <td>{new Date(ev.startAt).toLocaleDateString()}</td>
                      <td>
                        <strong>{ev.registeredCount}</strong> / {ev.capacity}
                      </td>
                      <td>
                        <span className={`badge ${ev.status === "OPEN" ? "badge-success" : "badge-muted"}`}>
                          {ev.status}
                        </span>
                      </td>
                      <td>
                        <Link to={`/events/${ev.eventId}`} className="table-action-link">
                          Manage
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Action Center Card */}
        <div className="section-card">
          <div className="section-header">
            <div>
              <h3>Officer Action Center</h3>
              <p className="subtitle">Immediate operational directives requiring Programme Officer sign-off.</p>
            </div>
          </div>

          <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
            <div style={{ padding: "0.85rem 1rem", borderRadius: "0.5rem", border: "1px solid #e2e8f0", backgroundColor: "#f8fafc" }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <div>
                  <strong>Volunteer Onboarding Approvals</strong>
                  <div className="cell-sub">{pendingApprovalsCount} student candidates awaiting enrollment verification.</div>
                </div>
                <Link to="/volunteers?status=PENDING_APPROVAL" className="btn-primary-sm">
                  Review
                </Link>
              </div>
            </div>

            <div style={{ padding: "0.85rem 1rem", borderRadius: "0.5rem", border: "1px solid #e2e8f0", backgroundColor: "#f8fafc" }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <div>
                  <strong>Service Hour Accreditation Claims</strong>
                  <div className="cell-sub">{pendingClaimsCount} activity reports submitted with evidence notes.</div>
                </div>
                <Link to="/service-hours" className="btn-primary-sm">
                  Audit
                </Link>
              </div>
            </div>

            <div style={{ padding: "0.85rem 1rem", borderRadius: "0.5rem", border: "1px solid #e2e8f0", backgroundColor: "#f8fafc" }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <div>
                  <strong>Attendance Session &amp; QR Control</strong>
                  <div className="cell-sub">Project active tokens or audit requested attendance adjustments.</div>
                </div>
                <Link to="/attendance" className="btn-primary-sm">
                  Console
                </Link>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
