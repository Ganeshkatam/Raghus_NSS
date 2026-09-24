import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { apiRequest, ApiError } from "../api/client";

interface Unit {
  unitId: string;
  unitName: string;
  unitNumber: string;
}

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
  const { isCoordinatorOrOfficer } = useAuth();
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
    ...(isCoordinatorOrOfficer ? [{ label: "Draft", value: "DRAFT" }] : []),
    { label: "Published", value: "PUBLISHED" },
    { label: "Ongoing", value: "ONGOING" },
    { label: "Completed", value: "COMPLETED" },
    ...(isCoordinatorOrOfficer ? [{ label: "Cancelled", value: "CANCELLED" }] : []),
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
    try {
      await apiRequest<EventItem>("/events", {
        method: "POST",
        body: JSON.stringify({
          unitId: form.unitId,
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

  const filteredEvents = events.filter((ev) => {
    if (!searchQuery.trim()) return true;
    const q = searchQuery.toLowerCase();
    return (
      ev.title.toLowerCase().includes(q) ||
      ev.venue.toLowerCase().includes(q) ||
      ev.unitName.toLowerCase().includes(q)
    );
  });

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h1>Events &amp; Registration</h1>
          <p className="subtitle">
            Plan NSS activities, manage registration windows, and monitor volunteer allocations.
          </p>
        </div>
        {isCoordinatorOrOfficer && (
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
          <input
            id="eventSearch"
            type="text"
            placeholder="Search by title, venue, or NSS unit..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>
        <div className="form-group" style={{ minWidth: "240px" }}>
          <label htmlFor="eventUnit">NSS Unit</label>
          <select id="eventUnit" value={unitId} onChange={(e) => setUnitId(e.target.value)}>
            <option value="">All NSS Units</option>
            {units.map((u) => (
              <option key={u.unitId} value={u.unitId}>
                {u.unitNumber} - {u.unitName}
              </option>
            ))}
          </select>
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
                <p className="cell-sub">{event.unitName}</p>
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
      {showModal && (
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
              <div className="form-grid">
                <div className="form-group">
                  <label>NSS Unit *</label>
                  <select
                    required
                    value={form.unitId}
                    onChange={(e) => setForm({ ...form, unitId: e.target.value })}
                  >
                    <option value="">Choose unit</option>
                    {units.map((u) => (
                      <option key={u.unitId} value={u.unitId}>
                        {u.unitNumber} - {u.unitName}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="form-group">
                  <label>Event Type *</label>
                  <input
                    required
                    value={form.eventType}
                    onChange={(e) => setForm({ ...form, eventType: e.target.value })}
                  />
                </div>
              </div>
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
                  <label>Start *</label>
                  <input
                    type="datetime-local"
                    required
                    value={form.startAt}
                    onChange={(e) => setForm({ ...form, startAt: e.target.value })}
                  />
                </div>
                <div className="form-group">
                  <label>End *</label>
                  <input
                    type="datetime-local"
                    required
                    value={form.endAt}
                    onChange={(e) => setForm({ ...form, endAt: e.target.value })}
                  />
                </div>
                <div className="form-group">
                  <label>Registration opens</label>
                  <input
                    type="datetime-local"
                    value={form.registrationOpenAt}
                    onChange={(e) => setForm({ ...form, registrationOpenAt: e.target.value })}
                  />
                </div>
                <div className="form-group">
                  <label>Registration closes</label>
                  <input
                    type="datetime-local"
                    value={form.registrationCloseAt}
                    onChange={(e) => setForm({ ...form, registrationCloseAt: e.target.value })}
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
