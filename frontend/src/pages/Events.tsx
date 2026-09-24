import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { apiRequest, ApiError } from "../api/client";

interface Unit { unitId:string; unitName:string; unitNumber:string; }
interface EventItem {
  eventId:string; unitId:string; unitName:string; title:string; description:string|null;
  eventType:string; startAt:string; endAt:string; registrationOpenAt:string|null;
  registrationCloseAt:string|null; venue:string; capacity:number; registeredCount:number;
  remainingCapacity:number; status:string;
}
interface EventPage { content:EventItem[]; totalPages:number; totalElements:number; number:number; }
const toIso = (value:string) => value ? new Date(value).toISOString() : null;

export const Events: React.FC = () => {
  const { isCoordinatorOrOfficer } = useAuth();
  const [events,setEvents]=useState<EventItem[]>([]);
  const [units,setUnits]=useState<Unit[]>([]);
  const [status,setStatus]=useState("");
  const [unitId,setUnitId]=useState("");
  const [loading,setLoading]=useState(true);
  const [error,setError]=useState<string|null>(null);
  const [showModal,setShowModal]=useState(false);
  const [saving,setSaving]=useState(false);
  const [modalError,setModalError]=useState<string|null>(null);
  const [form,setForm]=useState({
    unitId:"", title:"", description:"", eventType:"SERVICE",
    startAt:"", endAt:"", registrationOpenAt:"", registrationCloseAt:"",
    venue:"", capacity:""
  });

  const load = async () => {
    setLoading(true); setError(null);
    try {
      let query="/events?size=50";
      if(status) query += "&status=" + encodeURIComponent(status);
      if(unitId) query += "&unitId=" + unitId;
      const [page, unitData] = await Promise.all([
        apiRequest<EventPage>(query), apiRequest<Unit[]>("/units")
      ]);
      setEvents(page.content || []); setUnits(unitData || []);
    } catch (e) {
      const err=e as ApiError; setError(err.message || "Failed to load events.");
    } finally { setLoading(false); }
  };

  useEffect(()=>{ load(); },[status,unitId]);

  const create = async (e:React.FormEvent) => {
    e.preventDefault(); setSaving(true); setModalError(null);
    try {
      await apiRequest<EventItem>("/events", {
        method:"POST",
        body:JSON.stringify({
          unitId:form.unitId, title:form.title, description:form.description || null,
          eventType:form.eventType, startAt:toIso(form.startAt), endAt:toIso(form.endAt),
          registrationOpenAt:toIso(form.registrationOpenAt), registrationCloseAt:toIso(form.registrationCloseAt),
          venue:form.venue, capacity:Number(form.capacity)
        })
      });
      setShowModal(false);
      setForm({unitId:"",title:"",description:"",eventType:"SERVICE",startAt:"",endAt:"",
        registrationOpenAt:"",registrationCloseAt:"",venue:"",capacity:""});
      load();
    } catch(e) {
      const err=e as ApiError; setModalError(err.message || "Failed to create event.");
    } finally { setSaving(false); }
  };

  return <div className="page-container">
    <div className="page-header">
      <div><h1>Events &amp; Registration</h1><p className="subtitle">Plan NSS activities, manage registration windows, and protect event capacity.</p></div>
      {isCoordinatorOrOfficer && <button className="btn-primary" onClick={()=>setShowModal(true)}>+ Create Event</button>}
    </div>
    {error && <div className="alert alert-error"><strong>Error:</strong> {error}</div>}
    <div className="filter-bar section-card">
      <div className="form-group"><label htmlFor="eventStatus">Status</label>
        <select id="eventStatus" value={status} onChange={e=>setStatus(e.target.value)}>
          <option value="">All statuses</option><option>DRAFT</option><option>PUBLISHED</option><option>OPEN</option><option>CLOSED</option><option>COMPLETED</option><option>CANCELLED</option>
        </select>
      </div>
      <div className="form-group"><label htmlFor="eventUnit">NSS Unit</label>
        <select id="eventUnit" value={unitId} onChange={e=>setUnitId(e.target.value)}>
          <option value="">All units</option>
          {units.map(u=><option key={u.unitId} value={u.unitId}>{u.unitNumber} — {u.unitName}</option>)}
        </select>
      </div>
    </div>
    {loading ? <p className="loading-state">Loading events...</p> :
      events.length===0 ? <div className="section-card empty-state"><p>No events match the selected filters.</p></div> :
      <div className="event-grid">
        {events.map(event=><article className="event-card" key={event.eventId}>
          <div className="event-card-top"><span className="badge badge-primary">{event.eventType}</span><span className="badge badge-muted">{event.status}</span></div>
          <h2>{event.title}</h2><p className="cell-sub">{event.unitName}</p><p>{event.description || "No description provided."}</p>
          <div className="event-meta">
            <span><strong>When:</strong> {new Date(event.startAt).toLocaleString()}</span>
            <span><strong>Venue:</strong> {event.venue}</span>
            <span><strong>Capacity:</strong> {event.registeredCount}/{event.capacity} registered</span>
          </div>
          <div className="capacity-bar"><span style={{width:(Math.min(100,(event.registeredCount/event.capacity)*100)) + "%"}} /></div>
          <div className="card-actions"><Link className="btn-secondary-sm" to={"/events/"+event.eventId}>View Details</Link>
            {event.status==="OPEN" && event.remainingCapacity>0 && <span className="availability-text">{event.remainingCapacity} seat{event.remainingCapacity===1?"":"s"} left</span>}
            {event.status==="OPEN" && event.remainingCapacity===0 && <span className="availability-text">Full</span>}
          </div>
        </article>)}
      </div>
    }
    {showModal && <div className="modal-backdrop"><div className="modal-card modal-card-wide">
      <div className="modal-header"><h3>Create NSS Event</h3><button className="btn-close" onClick={()=>setShowModal(false)}>&times;</button></div>
      {modalError && <div className="alert alert-error"><strong>Error:</strong> {modalError}</div>}
      <form className="form-stack" onSubmit={create}>
        <div className="form-grid">
          <div className="form-group"><label>NSS Unit *</label><select required value={form.unitId} onChange={e=>setForm({...form,unitId:e.target.value})}><option value="">Choose unit</option>{units.map(u=><option key={u.unitId} value={u.unitId}>{u.unitNumber} — {u.unitName}</option>)}</select></div>
          <div className="form-group"><label>Event Type *</label><input required value={form.eventType} onChange={e=>setForm({...form,eventType:e.target.value})}/></div>
        </div>
        <div className="form-group"><label>Title *</label><input required maxLength={200} value={form.title} onChange={e=>setForm({...form,title:e.target.value})}/></div>
        <div className="form-group"><label>Description</label><textarea rows={3} value={form.description} onChange={e=>setForm({...form,description:e.target.value})}/></div>
        <div className="form-grid">
          <div className="form-group"><label>Start *</label><input type="datetime-local" required value={form.startAt} onChange={e=>setForm({...form,startAt:e.target.value})}/></div>
          <div className="form-group"><label>End *</label><input type="datetime-local" required value={form.endAt} onChange={e=>setForm({...form,endAt:e.target.value})}/></div>
          <div className="form-group"><label>Registration opens</label><input type="datetime-local" value={form.registrationOpenAt} onChange={e=>setForm({...form,registrationOpenAt:e.target.value})}/></div>
          <div className="form-group"><label>Registration closes</label><input type="datetime-local" value={form.registrationCloseAt} onChange={e=>setForm({...form,registrationCloseAt:e.target.value})}/></div>
        </div>
        <div className="form-grid">
          <div className="form-group"><label>Venue *</label><input required value={form.venue} onChange={e=>setForm({...form,venue:e.target.value})}/></div>
          <div className="form-group"><label>Capacity *</label><input required type="number" min="1" value={form.capacity} onChange={e=>setForm({...form,capacity:e.target.value})}/></div>
        </div>
        <div className="modal-actions"><button type="button" className="btn-secondary" onClick={()=>setShowModal(false)}>Cancel</button><button className="btn-primary" disabled={saving}>{saving?"Creating...":"Create Draft Event"}</button></div>
      </form>
    </div></div>}
  </div>;
};
