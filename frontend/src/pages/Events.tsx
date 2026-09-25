import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { apiRequest, ApiError } from "../api/client";
import { CustomSelect } from "../components/CustomSelect";
import { SearchBar } from "../components/SearchBar";
import { CustomDatePicker } from "../components/CustomDatePicker";


interface Unit {
  unitId: string;
  unitName: string;
  unitNumber: string;
}

interface EventUnitItem {
  unitId: string;
  unitName: string;
  unitNumber: string;
}

interface EventItem {
  eventId: string;
  unitId: string;
  unitName: string;
  organizingUnitId?: string;
  organizingUnitName?: string;
  eventScope?: "UNIT" | "MULTI_UNIT" | "COLLEGE_WIDE";
  participatingUnits?: EventUnitItem[];
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

interface EventPage {
  content: EventItem[];
  totalPages: number;
  totalElements: number;
  number: number;
}

interface VolunteerRegistration {
  registrationId: string;
  eventId: string;
  volunteerId: string;
  volunteerName: string;
  collegeId: string;
  status: string;
  waitlistPosition?: number | null;
  cancellationReason?: string | null;
  registeredAt: string;
}

const toIso = (value: string) => (value ? new Date(value).toISOString() : null);

export const Events: React.FC = () => {
  const { isCoordinatorOrOfficer, hasCapability } = useAuth();
  const canManageEvents = isCoordinatorOrOfficer || hasCapability("EVENTS_MANAGE");
  const [events, setEvents] = useState<EventItem[]>([]);
  const [units, setUnits] = useState<Unit[]>([]);
  const [status, setStatus] = useState("");
  const [unitId, setUnitId] = useState("");
  const [searchQuery, setSearchQuery] = useState("");
  const [myRegs, setMyRegs] = useState<Record<string, VolunteerRegistration>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showModal, setShowModal] = useState(false);
  const [saving, setSaving] = useState(false);
  const [modalError, setModalError] = useState<string | null>(null);

  const [form, setForm] = useState({
    unitId: "",
    eventScope: "UNIT" as "UNIT" | "MULTI_UNIT" | "COLLEGE_WIDE",
    participatingUnitIds: [] as string[],
    title: "",
    description: "",
    eventType: "SERVICE",
    startAt: "",
    endAt: "",
    registrationOpenAt: "",
    registrationCloseAt: "",
    venue: "",
    capacity: "",
  });

  const filterPills = [
    { label: "All", value: "" },
    ...(canManageEvents ? [{ label: "Draft", value: "DRAFT" }] : []),
    { label: "Published", value: "PUBLISHED" },
    { label: "Ongoing", value: "ONGOING" },
    { label: "Completed", value: "COMPLETED" },
    ...(canManageEvents ? [{ label: "Cancelled", value: "CANCELLED" }] : []),
  ];

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      let query = "/events?size=50";
      if (status) query += "&status=" + encodeURIComponent(status);
      if (unitId) query += "&unitId=" + unitId;

      const [page, unitData] = await Promise.all([
        apiRequest<EventPage>(query),
        apiRequest<Unit[]>("/units"),
      ]);

      setEvents(page.content || []);
      setUnits(unitData || []);

      if (!isCoordinatorOrOfficer) {
        try {
          const regs = await apiRequest<VolunteerRegistration[]>("/events/registrations/me");
          const regMap: Record<string, VolunteerRegistration> = {};
          (regs || []).forEach((r) => {
            regMap[r.eventId] = r;
          });
          setMyRegs(regMap);
        } catch {
          // Non-blocking if volunteer record not yet created
        }
      }
    } catch (e) {
      const err = e as ApiError;
      setError(err.message || "Failed to load events.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, [status, unitId]);

  const create = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setModalError(null);

    const currentTime = new Date();
    const fiveMinutesAgo = new Date(currentTime.getTime() - 5 * 60 * 1000);
    const startDate = new Date(form.startAt);
    const endDate = new Date(form.endAt);

    if (isNaN(startDate.getTime())) {
      setModalError("Please select a valid event start date & time.");
      setSaving(false);
      return;
    }

    if (isNaN(endDate.getTime())) {
      setModalError("Please select a valid event end date & time.");
      setSaving(false);
      return;
    }

    if (startDate < fiveMinutesAgo) {
      setModalError("Event start date & time cannot be in the past.");
      setSaving(false);
      return;
    }

    if (endDate <= startDate) {
      setModalError("Event end date & time must be strictly after the start time.");
      setSaving(false);
      return;
    }

    if (form.registrationOpenAt) {
      const regOpenDate = new Date(form.registrationOpenAt);
      if (isNaN(regOpenDate.getTime())) {
        setModalError("Invalid registration opening date & time.");
        setSaving(false);
        return;
      }
      if (regOpenDate < fiveMinutesAgo) {
        setModalError("Registration opening time cannot be in the past.");
        setSaving(false);
        return;
      }
      if (regOpenDate > startDate) {
        setModalError("Registration opening time cannot be after the event start time.");
        setSaving(false);
        return;
      }
    }

    if (form.registrationCloseAt) {
      const regCloseDate = new Date(form.registrationCloseAt);
      if (isNaN(regCloseDate.getTime())) {
        setModalError("Invalid registration closing date & time.");
        setSaving(false);
        return;
      }
      if (regCloseDate > startDate) {
        setModalError("Registration must close on or before the event start time.");
        setSaving(false);
        return;
      }
    }

    if (form.registrationOpenAt && form.registrationCloseAt) {
      const regOpenDate = new Date(form.registrationOpenAt);
      const regCloseDate = new Date(form.registrationCloseAt);
      if (regCloseDate < regOpenDate) {
        setModalError("Registration closing time must be on or after registration opening time.");
        setSaving(false);
        return;
      }
    }

    if (!form.unitId) {
      setModalError("Please select the organizing NSS unit.");
      setSaving(false);
      return;
    }

    if (form.eventScope === "MULTI_UNIT") {
      const parts = Array.from(new Set([form.unitId, ...form.participatingUnitIds])).filter(Boolean);
      if (parts.length < 2) {
        setModalError("Please select at least 2 participating NSS units for a multi-unit event.");
        setSaving(false);
        return;
      }
    }

    try {
      await apiRequest<EventItem>("/events", {
        method: "POST",
        body: JSON.stringify({
          unitId: form.unitId,
          organizingUnitId: form.unitId,
          eventScope: form.eventScope,
          participatingUnitIds: form.eventScope === "MULTI_UNIT"
            ? Array.from(new Set([form.unitId, ...form.participatingUnitIds])).filter(Boolean)
            : form.eventScope === "UNIT" ? [form.unitId] : [],
          title: form.title,
          description: form.description || null,
          eventType: form.eventType,
          startAt: toIso(form.startAt),
          endAt: toIso(form.endAt),
          registrationOpenAt: toIso(form.registrationOpenAt),
          registrationCloseAt: toIso(form.registrationCloseAt),
          venue: form.venue,
          capacity: Number(form.capacity),
        }),
      });
      setShowModal(false);
      setForm({
        unitId: "",
        eventScope: "UNIT",
        participatingUnitIds: [],
        title: "",
        description: "",
        eventType: "SERVICE",
        startAt: "",
        endAt: "",
        registrationOpenAt: "",
        registrationCloseAt: "",
        venue: "",
        capacity: "",
      });
      load();
    } catch (e) {
      const err = e as ApiError;
      setModalError(err.message || "Failed to create event.");
    } finally {
      setSaving(false);
    }
  };

  const getRegistrationWindowState = (event: EventItem) => {
    const now = new Date();
    if (event.status === "CLOSED" || event.status === "COMPLETED" || event.status === "CANCELLED") {
      return { label: "Registration Closed", state: "CLOSED", color: "#64748b", bg: "#f1f5f9" };
    }
    if (event.status === "DRAFT") {
      return { label: "Draft - Not Opened", state: "NOT_OPENED", color: "#64748b", bg: "#f1f5f9" };
    }
    if (event.status === "PUBLISHED") {
      if (event.registrationOpenAt && new Date(event.registrationOpenAt) > now) {
        return {
          label: `Opens ${new Date(event.registrationOpenAt).toLocaleDateString([], {
            month: "short",
            day: "numeric",
            hour: "2-digit",
            minute: "2-digit",
          })}`,
          state: "NOT_OPENED",
          color: "#0369a1",
          bg: "#e0f2fe",
        };
      }
      return { label: "Not Opened", state: "NOT_OPENED", color: "#0369a1", bg: "#e0f2fe" };
    }
    if (event.status === "OPEN") {
      if (event.registrationCloseAt && new Date(event.registrationCloseAt) < now) {
        return { label: "Registration Closed", state: "CLOSED", color: "#b45309", bg: "#fef3c7" };
      }
      if (event.registrationOpenAt && new Date(event.registrationOpenAt) > now) {
        return { label: "Not Opened", state: "NOT_OPENED", color: "#0369a1", bg: "#e0f2fe" };
      }
      return { label: "Registration Open", state: "OPEN", color: "#15803d", bg: "#dcfce7" };
    }
    return { label: "Closed", state: "CLOSED", color: "#64748b", bg: "#f1f5f9" };
  };

  const renderEventUnitScope = (event: EventItem) => {
    if (event.eventScope === "COLLEGE_WIDE") {
      return (
        <span style={{ display: "inline-flex", alignItems: "center", gap: "0.4rem", flexWrap: "wrap" }}>
          <span className="badge badge-info" style={{ fontWeight: 700 }}>College-wide Event</span>
          <span style={{ fontSize: "0.82rem", color: "var(--text-secondary, #64748b)" }}>
            Organized by {event.organizingUnitName || event.unitName}
          </span>
        </span>
      );
    }
    if (event.eventScope === "MULTI_UNIT" && event.participatingUnits && event.participatingUnits.length > 1) {
      const unitNumbers = event.participatingUnits.map((u) => u.unitNumber).filter(Boolean);
      const label = unitNumbers.length > 0 ? `Units ${unitNumbers.join(", ")}` : "Multi-Unit";
      return (
        <span style={{ display: "inline-flex", alignItems: "center", gap: "0.4rem", flexWrap: "wrap" }}>
          <span className="badge badge-success" style={{ fontWeight: 700 }}>{label}</span>
          <span style={{ fontSize: "0.82rem", color: "var(--text-secondary, #64748b)" }}>
            Organized by {event.organizingUnitName || event.unitName}
          </span>
        </span>
      );
    }
    return <span style={{ fontWeight: 600 }}>{event.unitName}</span>;
  };

  const filteredEvents = events.filter((ev) => {
    if (!searchQuery.trim()) return true;
    const q = searchQuery.toLowerCase();
    const matchesScope = ev.eventScope === "COLLEGE_WIDE" && "college-wide".includes(q);
    const matchesParts = ev.participatingUnits?.some(
      (u) => u.unitName.toLowerCase().includes(q) || u.unitNumber.toLowerCase().includes(q)
    );
    return (
      ev.title.toLowerCase().includes(q) ||
      ev.venue.toLowerCase().includes(q) ||
      ev.unitName.toLowerCase().includes(q) ||
      matchesScope ||
      Boolean(matchesParts)
    );
  });

  const now = new Date();
  const padZero = (n: number) => (n < 10 ? `0${n}` : `${n}`);
  const todayIso = `${now.getFullYear()}-${padZero(now.getMonth() + 1)}-${padZero(now.getDate())}`;

  // Max horizon: 1 academic year (365 days)
  const maxHorizonDate = new Date(now.getFullYear() + 1, now.getMonth(), now.getDate());
  const maxHorizonIso = `${maxHorizonDate.getFullYear()}-${padZero(maxHorizonDate.getMonth() + 1)}-${padZero(maxHorizonDate.getDate())}`;

  const eventStartMin = todayIso;
  const eventStartMax = maxHorizonIso;

  const eventEndMin = form.startAt ? form.startAt.slice(0, 10) : todayIso;
  const eventEndMax = maxHorizonIso;

  const regOpenMin = todayIso;
  const regOpenMax = form.startAt ? form.startAt.slice(0, 10) : maxHorizonIso;

  const regCloseMin = form.registrationOpenAt
    ? form.registrationOpenAt.slice(0, 10)
    : todayIso;
  const regCloseMax = form.startAt ? form.startAt.slice(0, 10) : maxHorizonIso;

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h1>Events &amp; Registration</h1>
          <p className="subtitle">
            Plan NSS activities, manage registration windows, and monitor volunteer allocations.
          </p>
        </div>
        {canManageEvents && (
          <button className="btn-primary" onClick={() => setShowModal(true)}>
            + Create Event
          </button>
        )}
      </div>

      {error && (
        <div className="alert alert-error">
          <strong>Error:</strong> {error}
        </div>
      )}

      {/* Filter Pills */}
      <div style={{ display: "flex", flexWrap: "wrap", gap: "0.5rem", marginBottom: "1rem" }}>
        {filterPills.map((pill) => {
          const isSelected = status === pill.value;
          return (
            <button
              key={pill.value}
              type="button"
              onClick={() => setStatus(pill.value)}
              style={{
                padding: "0.45rem 1rem",
                borderRadius: "9999px",
                border: isSelected ? "1px solid #1e40af" : "1px solid #cbd5e1",
                backgroundColor: isSelected ? "#1e40af" : "#ffffff",
                color: isSelected ? "#ffffff" : "#475569",
                fontWeight: isSelected ? 700 : 500,
                fontSize: "0.85rem",
                cursor: "pointer",
                transition: "all 0.15s ease-in-out",
              }}
            >
              {pill.label}
            </button>
          );
        })}
      </div>

      {/* Secondary Controls: Search and Unit Filter */}
      <div className="filter-bar section-card" style={{ marginBottom: "1.5rem" }}>
        <div className="form-group" style={{ flex: 1 }}>
          <label htmlFor="eventSearch">Search Events</label>
          <SearchBar
            id="eventSearch"
            placeholder="Search by title, venue, or NSS unit..."
            value={searchQuery}
            onChange={setSearchQuery}
          />
        </div>
        <div className="form-group" style={{ minWidth: "240px" }}>
          <label htmlFor="eventUnit">NSS Unit</label>
          <CustomSelect
            id="eventUnit"
            value={unitId}
            onChange={setUnitId}
            options={[
              { value: "", label: "All NSS Units" },
              ...units.map((u) => ({
                value: u.unitId,
                label: `${u.unitNumber} - ${u.unitName}`,
              })),
            ]}
            placeholder="All NSS Units"
          />
        </div>
      </div>

      {loading ? (
        <p className="loading-state">Loading events...</p>
      ) : filteredEvents.length === 0 ? (
        <div className="section-card empty-state">
          <p>No events match the selected filters.</p>
        </div>
      ) : (
        <div className="event-grid">
          {filteredEvents.map((event) => {
            const windowState = getRegistrationWindowState(event);
            const myReg = myRegs[event.eventId];

            return (
              <article className="event-card" key={event.eventId}>
                <div className="event-card-top" style={{ alignItems: "center", gap: "0.5rem" }}>
                  <div style={{ display: "flex", gap: "0.4rem", flexWrap: "wrap" }}>
                    <span className="badge badge-primary">{event.eventType}</span>
                    <span className="badge badge-muted">{event.status}</span>
                  </div>
                  {/* Registration Window Badge */}
                  <span
                    style={{
                      fontSize: "0.75rem",
                      fontWeight: 600,
                      padding: "0.2rem 0.6rem",
                      borderRadius: "6px",
                      color: windowState.color,
                      backgroundColor: windowState.bg,
                      whiteSpace: "nowrap",
                    }}
                  >
                    {windowState.label}
                  </span>
                </div>

                <h2>{event.title}</h2>
                <p className="cell-sub">{renderEventUnitScope(event)}</p>
                <p>{event.description || "No description provided."}</p>

                <div className="event-meta">
                  <span>
                    <strong>When:</strong> {new Date(event.startAt).toLocaleString()}
                  </span>
                  <span>
                    <strong>Venue:</strong> {event.venue}
                  </span>
                  <span>
                    <strong>Capacity:</strong> {event.registeredCount}/{event.capacity} registered
                  </span>
                </div>

                {/* Capacity Progress Bar */}
                <div className="capacity-bar">
                  <span
                    style={{
                      width: Math.min(100, (event.registeredCount / event.capacity) * 100) + "%",
                      backgroundColor:
                        event.registeredCount >= event.capacity ? "#d97706" : "#2563eb",
                    }}
                  />
                </div>

                {/* Footer Actions & Registration State */}
                <div
                  className="card-actions"
                  style={{
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                    flexWrap: "wrap",
                    gap: "0.5rem",
                  }}
                >
                  <Link className="btn-secondary-sm" to={"/events/" + event.eventId}>
                    View Details
                  </Link>

                  {/* Volunteer Registration State indicator */}
                  {!isCoordinatorOrOfficer && (
                    <div style={{ display: "flex", alignItems: "center", gap: "0.4rem" }}>
                      {myReg ? (
                        myReg.status === "CONFIRMED" || myReg.status === "REGISTERED" ? (
                          <span
                            className="badge badge-success"
                            style={{ fontWeight: 700, padding: "0.35rem 0.65rem" }}
                          >
                            Registered
                          </span>
                        ) : myReg.status === "WAITLISTED" ? (
                          <span
                            className="badge badge-warning"
                            style={{ fontWeight: 700, padding: "0.35rem 0.65rem" }}
                          >
                            Waitlist #{myReg.waitlistPosition || 1}
                          </span>
                        ) : myReg.status === "CANCELLED" ? (
                          <span className="badge badge-muted">Cancelled</span>
                        ) : null
                      ) : windowState.state === "OPEN" ? (
                        event.remainingCapacity > 0 ? (
                          <Link to={"/events/" + event.eventId} className="btn-primary-sm">
                            Register
                          </Link>
                        ) : (
                          <Link
                            to={"/events/" + event.eventId}
                            className="btn-secondary-sm"
                            style={{ borderColor: "#f59e0b", color: "#b45309" }}
                          >
                            Waitlist
                          </Link>
                        )
                      ) : (
                        <span className="availability-text" style={{ fontSize: "0.8rem" }}>
                          {event.status === "OPEN" && event.remainingCapacity === 0
                            ? "Capacity Full"
                            : windowState.label}
                        </span>
                      )}
                    </div>
                  )}

                  {isCoordinatorOrOfficer && (
                    <div>
                      {event.status === "OPEN" && event.remainingCapacity > 0 && (
                        <span className="availability-text">
                          {event.remainingCapacity} seat{event.remainingCapacity === 1 ? "" : "s"}{" "}
                          left
                        </span>
                      )}
                      {event.status === "OPEN" && event.remainingCapacity === 0 && (
                        <span className="availability-text" style={{ color: "#d97706" }}>
                          Waitlist Active
                        </span>
                      )}
                    </div>
                  )}
                </div>
              </article>
            );
          })}
        </div>
      )}

      {/* Create Event Modal */}
      {showModal && canManageEvents && (
        <div className="modal-backdrop">
          <div className="modal-card modal-card-wide">
            <div className="modal-header">
              <h3>Create NSS Event</h3>
              <button className="btn-close" onClick={() => setShowModal(false)}>
                &times;
              </button>
            </div>
            {modalError && (
              <div className="alert alert-error">
                <strong>Error:</strong> {modalError}
              </div>
            )}
            <form className="form-stack" onSubmit={create}>
              {/* Event Scope Selection */}
              <div className="form-group" style={{ marginBottom: "1rem" }}>
                <label style={{ fontWeight: 600, display: "block", marginBottom: "0.5rem" }}>Event Scope *</label>
                <div style={{ display: "flex", gap: "1.25rem", flexWrap: "wrap", padding: "0.25rem 0" }}>
                  <label style={{ display: "flex", alignItems: "center", gap: "0.45rem", cursor: "pointer", fontSize: "0.9rem", fontWeight: form.eventScope === "UNIT" ? 600 : 400 }}>
                    <input
                      type="radio"
                      name="eventScope"
                      value="UNIT"
                      checked={form.eventScope === "UNIT"}
                      onChange={() => setForm({ ...form, eventScope: "UNIT", participatingUnitIds: form.unitId ? [form.unitId] : [] })}
                    />
                    <span>Single NSS Unit</span>
                  </label>
                  <label style={{ display: "flex", alignItems: "center", gap: "0.45rem", cursor: "pointer", fontSize: "0.9rem", fontWeight: form.eventScope === "MULTI_UNIT" ? 600 : 400 }}>
                    <input
                      type="radio"
                      name="eventScope"
                      value="MULTI_UNIT"
                      checked={form.eventScope === "MULTI_UNIT"}
                      onChange={() => setForm({ ...form, eventScope: "MULTI_UNIT", participatingUnitIds: form.unitId ? [form.unitId] : [] })}
                    />
                    <span>Multiple NSS Units</span>
                  </label>
                  <label style={{ display: "flex", alignItems: "center", gap: "0.45rem", cursor: "pointer", fontSize: "0.9rem", fontWeight: form.eventScope === "COLLEGE_WIDE" ? 600 : 400 }}>
                    <input
                      type="radio"
                      name="eventScope"
                      value="COLLEGE_WIDE"
                      checked={form.eventScope === "COLLEGE_WIDE"}
                      onChange={() => setForm({ ...form, eventScope: "COLLEGE_WIDE", participatingUnitIds: [] })}
                    />
                    <span>College-wide Event</span>
                  </label>
                </div>
              </div>

              <div className="form-grid">
                <div className="form-group">
                  <label>Organizing NSS Unit *</label>
                  <CustomSelect
                    value={form.unitId}
                    onChange={(val) => {
                      let updatedParts = form.participatingUnitIds;
                      if (form.eventScope === "MULTI_UNIT") {
                        updatedParts = Array.from(new Set([val, ...form.participatingUnitIds])).filter(Boolean);
                      } else if (form.eventScope === "UNIT") {
                        updatedParts = val ? [val] : [];
                      }
                      setForm({ ...form, unitId: val, participatingUnitIds: updatedParts });
                    }}
                    options={[
                      { value: "", label: "Choose organizing unit" },
                      ...units.map((u) => ({
                        value: u.unitId,
                        label: `${u.unitNumber} - ${u.unitName}`,
                      })),
                    ]}
                    placeholder="Choose organizing unit"
                  />
                </div>
                <div className="form-group">
                  <label>Event Type *</label>
                  <CustomSelect
                    value={form.eventType}
                    onChange={(val) => setForm({ ...form, eventType: val })}
                    options={[
                      { value: "SERVICE", label: "General Community Service" },
                      { value: "COMMUNITY_OUTREACH", label: "Community Outreach & Field Work" },
                      { value: "CAMPUS_DRIVE", label: "Campus Cleanliness & Drive" },
                      { value: "BLOOD_DONATION", label: "Blood Donation Camp" },
                      { value: "TREE_PLANTATION", label: "Tree Plantation & Environment" },
                      { value: "AWARENESS_WORKSHOP", label: "Awareness Workshop & Seminar" },
                      { value: "SPECIAL_CAMP", label: "7-Day Special Annual Camp" },
                      { value: "NATIONAL_OBSERVANCE", label: "National Day Observance" },
                      { value: "OTHER", label: "Other Institutional Activity" },
                    ]}
                    placeholder="Select event type"
                  />
                </div>
              </div>

              {form.eventScope === "MULTI_UNIT" && (
                <div className="form-group" style={{ backgroundColor: "var(--bg-muted, #f8fafc)", padding: "0.85rem 1rem", borderRadius: "6px", border: "1px solid var(--border-color, #e2e8f0)", marginBottom: "1rem" }}>
                  <label style={{ fontWeight: 600, display: "block", marginBottom: "0.5rem" }}>
                    Participating NSS Units * (Select at least 2 units)
                  </label>
                  <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: "0.6rem" }}>
                    {units.map((u) => {
                      const isOrg = u.unitId === form.unitId;
                      const isChecked = isOrg || form.participatingUnitIds.includes(u.unitId);
                      return (
                        <label key={u.unitId} style={{ display: "flex", alignItems: "center", gap: "0.5rem", fontSize: "0.85rem", cursor: isOrg ? "default" : "pointer" }}>
                          <input
                            type="checkbox"
                            checked={isChecked}
                            disabled={isOrg}
                            onChange={(e) => {
                              if (isOrg) return;
                              const newIds = e.target.checked
                                ? [...form.participatingUnitIds, u.unitId]
                                : form.participatingUnitIds.filter((id) => id !== u.unitId);
                              setForm({ ...form, participatingUnitIds: newIds });
                            }}
                          />
                          <span>{u.unitNumber} - {u.unitName} {isOrg ? "(Organizer)" : ""}</span>
                        </label>
                      );
                    })}
                  </div>
                </div>
              )}

              {form.eventScope === "COLLEGE_WIDE" && (
                <div style={{ backgroundColor: "#eff6ff", border: "1px solid #bfdbfe", padding: "0.75rem 1rem", borderRadius: "6px", marginBottom: "1rem", fontSize: "0.85rem", color: "#1e40af" }}>
                  <strong>College-wide Event:</strong> Open to enrolled NSS volunteers across all active college units.
                </div>
              )}

              <div className="form-group">
                <label>Title *</label>
                <input
                  required
                  maxLength={200}
                  value={form.title}
                  onChange={(e) => setForm({ ...form, title: e.target.value })}
                />
              </div>
              <div className="form-group">
                <label>Description</label>
                <textarea
                  rows={3}
                  value={form.description}
                  onChange={(e) => setForm({ ...form, description: e.target.value })}
                />
              </div>
              <div className="form-grid">
                <div className="form-group">
                  <label style={{ display: "block", marginBottom: "0.35rem" }}>Start *</label>
                  <CustomDatePicker
                    required
                    includeTime
                    minDate={eventStartMin}
                    maxDate={eventStartMax}
                    value={form.startAt}
                    onChange={(val) => setForm({ ...form, startAt: val })}
                    placeholder="Select start date & time"
                  />
                </div>
                <div className="form-group">
                  <label style={{ display: "block", marginBottom: "0.35rem" }}>End *</label>
                  <CustomDatePicker
                    required
                    includeTime
                    minDate={eventEndMin}
                    maxDate={eventEndMax}
                    value={form.endAt}
                    onChange={(val) => setForm({ ...form, endAt: val })}
                    placeholder="Select end date & time"
                  />
                </div>
                <div className="form-group">
                  <label style={{ display: "block", marginBottom: "0.35rem" }}>Registration opens</label>
                  <CustomDatePicker
                    includeTime
                    minDate={regOpenMin}
                    maxDate={regOpenMax}
                    value={form.registrationOpenAt}
                    onChange={(val) => setForm({ ...form, registrationOpenAt: val })}
                    placeholder="Select opening date & time"
                  />
                </div>
                <div className="form-group">
                  <label style={{ display: "block", marginBottom: "0.35rem" }}>Registration closes</label>
                  <CustomDatePicker
                    includeTime
                    minDate={regCloseMin}
                    maxDate={regCloseMax}
                    value={form.registrationCloseAt}
                    onChange={(val) => setForm({ ...form, registrationCloseAt: val })}
                    placeholder="Select closing date & time"
                  />
                </div>
              </div>
              <div className="form-grid">
                <div className="form-group">
                  <label>Venue *</label>
                  <input
                    required
                    value={form.venue}
                    onChange={(e) => setForm({ ...form, venue: e.target.value })}
                  />
                </div>
                <div className="form-group">
                  <label>Capacity *</label>
                  <input
                    required
                    type="number"
                    min="1"
                    value={form.capacity}
                    onChange={(e) => setForm({ ...form, capacity: e.target.value })}
                  />
                </div>
              </div>
              <div className="modal-actions">
                <button
                  type="button"
                  className="btn-secondary"
                  onClick={() => setShowModal(false)}
                >
                  Cancel
                </button>
                <button className="btn-primary" disabled={saving}>
                  {saving ? "Creating..." : "Create Draft Event"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
