import React, { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { apiRequest, ApiError } from "../api/client";

interface EventItem {
  eventId:string; unitId:string; unitName:string; title:string; description:string|null;
  eventType:string; startAt:string; endAt:string; registrationOpenAt:string|null;
  registrationCloseAt:string|null; venue:string; capacity:number; registeredCount:number;
  remainingCapacity:number; status:string;
}
interface Registration { registrationId:string; volunteerId:string; volunteerName:string; collegeId:string; status:string; waitlistPosition?:number|null; registeredAt:string; }
interface Volunteer { volunteerId:string; name:string; status:string; activeUnitId:string|null; }
interface EventStats { totalRegistered:number; waitlistCount:number; attendedCount:number; absentCount:number; attendanceRate:number; }

const transitions:Record<string,string[]> = {
  DRAFT:["publish","cancel"], PUBLISHED:["open","cancel"], OPEN:["close","cancel"], CLOSED:["complete","cancel"]
};

export const EventDetail:React.FC = () => {
  const { id }=useParams<{id:string}>();
  const { isCoordinatorOrOfficer }=useAuth();
  const [event,setEvent]=useState<EventItem|null>(null);
  const [stats,setStats]=useState<EventStats|null>(null);
  const [registrations,setRegistrations]=useState<Registration[]>([]);
  const [volunteer,setVolunteer]=useState<Volunteer|null>(null);
  const [myRegistration,setMyRegistration]=useState<Registration|null>(null);
  const [loading,setLoading]=useState(true);
  const [busy,setBusy]=useState(false);
  const [message,setMessage]=useState<string|null>(null);
  const [error,setError]=useState<string|null>(null);

  const load=async()=>{
    if(!id)return;
    setLoading(true); setError(null);
    try {
      const e=await apiRequest<EventItem>("/events/"+id); setEvent(e);
      try { const s=await apiRequest<EventStats>("/events/"+id+"/stats"); setStats(s); } catch {}
      if(isCoordinatorOrOfficer){
        try { setRegistrations(await apiRequest<Registration[]>("/events/"+id+"/registrations")); } catch {}
      } else {
        try {
          setVolunteer(await apiRequest<Volunteer>("/volunteers/me"));
          try { setMyRegistration(await apiRequest<Registration>("/events/"+id+"/registrations/me")); } catch { setMyRegistration(null); }
        } catch {}
      }
    } catch(e) {
      const err=e as ApiError; setError(err.message || "Failed to load event.");
    } finally { setLoading(false); }
  };
  useEffect(()=>{load();},[id,isCoordinatorOrOfficer]);

  const transition=async(action:string)=>{
    if(!id)return; setBusy(true); setError(null); setMessage(null);
    try {
      await apiRequest("/events/"+id+"/"+action,{method:"POST"});
      setMessage("Event " + action + "ed successfully."); await load();
    } catch(e){const err=e as ApiError; setError(err.message || ("Unable to " + action + " event."));}
    finally{setBusy(false);}
  };

  const register=async(joinWaitlist:boolean = false)=>{
    if(!id || !volunteer)return; setBusy(true); setError(null); setMessage(null);
    try {
      const endpoint = joinWaitlist ? "/events/"+id+"/waitlist" : "/events/"+id+"/registrations";
      await apiRequest(endpoint,{method:"POST",body:JSON.stringify({volunteerId:volunteer.volunteerId, joinWaitlist})});
      setMessage(joinWaitlist ? "Added to event waitlist." : "Registration confirmed.");
      await load();
    } catch(e){const err=e as ApiError; setError(err.message || "Registration failed.");}
    finally{setBusy(false);}
  };

  const cancelRegistration=async()=>{
    if(!id)return; setBusy(true); setError(null); setMessage(null);
    try { await apiRequest("/events/"+id+"/registrations",{method:"DELETE"}); setMessage("Registration cancelled."); await load(); }
    catch(e){const err=e as ApiError; setError(err.message || "Unable to cancel registration.");}
    finally{setBusy(false);}
  };

  const cloneEvent=async()=>{
    if(!id)return; setBusy(true); setError(null); setMessage(null);
    try {
      const res=await apiRequest<EventItem>("/events/"+id+"/clone",{method:"POST"});
      setMessage("Event cloned successfully as DRAFT: " + res.title);
    } catch(e){const err=e as ApiError; setError(err.message || "Unable to clone event.");}
    finally{setBusy(false);}
  };

  if(loading)return <div className="page-container"><p className="loading-state">Loading event details...</p></div>;
  if(error && !event)return <div className="page-container"><div className="alert alert-error"><strong>Error:</strong> {error}</div><Link className="btn-secondary" to="/events">Back to Events</Link></div>;
  if(!event)return null;

  const actions=transitions[event.status] || [];
  const volunteerIsEligible=!!volunteer && volunteer.status==="ACTIVE" && volunteer.activeUnitId===event.unitId;

  return <div className="page-container">
    <Link to="/events" className="back-link">Back to Events</Link>
    <div className="page-header">
      <div><div className="event-card-top"><span className="badge badge-primary">{event.eventType}</span><span className="badge badge-muted">{event.status}</span></div>
        <h1>{event.title}</h1><p className="subtitle">{event.unitName} &bull; {event.venue}</p>
      </div>
      {isCoordinatorOrOfficer && <div className="card-actions">
        <button className="btn-secondary-sm" disabled={busy} onClick={cloneEvent}>Clone Event</button>
        {actions.map(a=><button key={a} className={a==="cancel"?"btn-danger-sm":"btn-primary-sm"} disabled={busy} onClick={()=>transition(a)}>
          {a==="publish"?"Publish":a==="open"?"Open Registration":a==="close"?"Close Registration":a==="complete"?"Mark Completed":"Cancel Event"}
        </button>)}
      </div>}
    </div>

    {error && <div className="alert alert-error"><strong>Error:</strong> {error}</div>}
    {message && <div className="alert alert-success">{message}</div>}

    <div className="detail-grid">
      <section className="section-card">
        <h3>Event Overview</h3><p>{event.description || "No description provided."}</p>
        <dl className="detail-list">
          <div><dt>Starts</dt><dd>{new Date(event.startAt).toLocaleString()}</dd></div>
          <div><dt>Ends</dt><dd>{new Date(event.endAt).toLocaleString()}</dd></div>
          <div><dt>Registration opens</dt><dd>{event.registrationOpenAt?new Date(event.registrationOpenAt).toLocaleString():"Immediately"}</dd></div>
          <div><dt>Registration closes</dt><dd>{event.registrationCloseAt?new Date(event.registrationCloseAt).toLocaleString():"No explicit close time"}</dd></div>
          <div><dt>Capacity</dt><dd>{event.registeredCount}/{event.capacity} registered &mdash; {event.remainingCapacity} remaining</dd></div>
          {stats && <div><dt>Waitlist</dt><dd>{stats.waitlistCount} volunteers queued</dd></div>}
        </dl>
      </section>

      {!isCoordinatorOrOfficer && <section className="section-card">
        <h3>Your Registration</h3>
        {!volunteer ? <p>Loading your volunteer profile...</p> :
          myRegistration && (myRegistration.status==="REGISTERED" || myRegistration.status==="CONFIRMED") ? <div className="card-actions">
            <span className="badge badge-success">CONFIRMED</span>
            {event.status==="OPEN" && <button className="btn-danger-sm" disabled={busy} onClick={cancelRegistration}>Cancel Registration</button>}
          </div> :
          myRegistration && myRegistration.status==="WAITLISTED" ? <div className="card-actions">
            <span className="badge badge-warning">WAITLISTED #{myRegistration.waitlistPosition || 1}</span>
            {event.status==="OPEN" && <button className="btn-danger-sm" disabled={busy} onClick={cancelRegistration}>Leave Waitlist</button>}
          </div> :
          volunteerIsEligible ? event.status==="OPEN" ?
            event.remainingCapacity>0 ?
              <button className="btn-primary" disabled={busy} onClick={()=>register(false)}>Register for Event</button> :
              <div>
                <p style={{ color: "#d97706", fontWeight: 600, marginBottom: "0.5rem" }}>Event is currently at maximum capacity.</p>
                <button className="btn-secondary" disabled={busy} onClick={()=>register(true)}>Join Event Waitlist</button>
              </div>
            : <p>Registration is not currently open.</p>
          : <p>You must be an active member of this event's NSS unit to register.</p>}
        <p className="form-hint">Registration and waitlist positions are managed automatically on a first-come, first-served basis.</p>
      </section>}

      {isCoordinatorOrOfficer && <section className="section-card">
        <div className="section-header"><h3>Registered Volunteers</h3><span className="results-count">{registrations.length} records</span></div>
        {registrations.length===0?<div className="empty-state"><p>No registrations yet.</p></div>:
          <div className="units-table-wrapper"><table className="data-table"><thead><tr><th>College ID</th><th>Name</th><th>Status</th><th>Waitlist #</th><th>Registered</th></tr></thead><tbody>
            {registrations.map(r=><tr key={r.registrationId}>
              <td>{r.collegeId}</td>
              <td>{r.volunteerName}</td>
              <td><span className={`badge ${r.status==="WAITLISTED"?"badge-warning":"badge-success"}`}>{r.status}</span></td>
              <td>{r.waitlistPosition ? `#${r.waitlistPosition}` : "-"}</td>
              <td>{new Date(r.registeredAt).toLocaleString()}</td>
            </tr>)}
          </tbody></table></div>}
      </section>}
    </div>
  </div>;
};
