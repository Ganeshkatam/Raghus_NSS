import React, { useState } from "react";
import { Link, Navigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import "./LandingPage.css";

const Outbound = () => <svg aria-hidden="true" viewBox="0 0 24 24" fill="none"><path d="M7 17 17 7M7 7h10v10" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round"/></svg>;
const Forward = () => <svg aria-hidden="true" viewBox="0 0 24 24" fill="none"><path d="M4 12h15m-6-6 6 6-6 6" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round"/></svg>;
const CollegeMark = () => <span className="rnh-mark" aria-label="Raghu NSS mark"><b>R</b><i /></span>;

export const LandingPage: React.FC = () => {
  const { isAuthenticated, loading } = useAuth();
  const [menuOpen, setMenuOpen] = useState(false);
  if (!loading && isAuthenticated) return <Navigate to="/dashboard" replace />;
  const close = () => setMenuOpen(false);

  return <main className="rnh-site">
    <a className="rnh-skip" href="#rnh-main">Skip to content</a>
    <header className="rnh-nav">
      <Link to="/" className="rnh-brand" onClick={close} aria-label="Raghu Engineering College NSS home">
        <CollegeMark /><span><strong>RAGHU</strong><small>ENGINEERING COLLEGE <b>·</b> NSS</small></span>
      </Link>
      <button className={`rnh-menu-toggle${menuOpen ? " open" : ""}`} onClick={() => setMenuOpen(v => !v)} aria-expanded={menuOpen} aria-controls="rnh-links" aria-label={menuOpen ? "Close menu" : "Open menu"}><i/><i/></button>
      <nav id="rnh-links" className={menuOpen ? "rnh-links open" : "rnh-links"} aria-label="Main navigation">
        <a href="#purpose" onClick={close}>Our purpose</a><a href="#initiatives" onClick={close}>Initiatives</a><a href="#journey" onClick={close}>Your journey</a>
        <Link to="/login" className="rnh-login-link" onClick={close}>Portal sign in <Outbound/></Link>
      </nav>
    </header>

    <section className="rnh-hero" id="rnh-main">
      <div className="rnh-hero-copy">
        <div className="rnh-eyebrow"><i/> RAGHU ENGINEERING COLLEGE · NATIONAL SERVICE SCHEME</div>
        <h1>Make your<br/><em>presence</em><br/>mean something.</h1>
        <p>A community is changed by people who choose to show up. Discover meaningful work, take part in campus initiatives, and turn your time into service.</p>
        <div className="rnh-hero-actions"><Link to="/login" className="rnh-button dark">Enter the NSS portal <Outbound/></Link><a href="#purpose" className="rnh-underlink">Explore our purpose <span>↓</span></a></div>
        <div className="rnh-hero-motto"><i/> NOT ME, BUT YOU <b>✳</b> THE NSS MOTTO</div>
      </div>
      <div className="rnh-hero-art" aria-label="Students participating in community service">
        <div className="rnh-hero-photo"><img src="https://images.unsplash.com/photo-1531206715517-5c0ba140b2b8?auto=format&fit=crop&w=1400&q=85" alt="Volunteers working together during a community initiative" fetchPriority="high"/></div>
        <div className="rnh-inset-photo"><img src="https://images.unsplash.com/photo-1559027615-cd4628902d4a?auto=format&fit=crop&w=700&q=85" alt="Volunteers collaborating outdoors" loading="lazy"/></div>
        <div className="rnh-stamp"><small>THE POWER</small><strong>OF<br/>SHOWING<br/>UP.</strong><small>REC · NSS</small></div>
        <div className="rnh-vertical">LEARN · SERVE · LEAD</div><span className="rnh-index">01 / 04</span>
      </div>
    </section>

    <section className="rnh-purpose" id="purpose">
      <div className="rnh-overline"><span>01 — THE REASON WE EXIST</span><span>RAGHU / NSS</span></div>
      <div className="rnh-purpose-grid">
        <div><p className="rnh-kicker">BEYOND THE CLASSROOM</p><h2>An education<br/>in <em>humanity.</em></h2></div>
        <div className="rnh-purpose-quote"><b>“</b><p>The National Service Scheme creates space for students to understand the world beyond themselves. It connects learning with action—and individual potential with collective progress.</p><small>────　NOT ME, BUT YOU</small></div>
      </div>
      <div className="rnh-purpose-foot"><b>01</b><p>Character is built not only by what we know, but by what we choose to do for others.</p><strong>✳</strong></div>
    </section>

    <section className="rnh-initiatives" id="initiatives">
      <div className="rnh-initiatives-head"><div><p className="rnh-kicker">02 — WHERE ACTION BEGINS</p><h2>Many ways<br/>to <em>make a mark.</em></h2></div><p>Every initiative starts with a simple decision: to contribute. Find the kind of service that moves you.</p></div>
      <div className="rnh-mosaic">
        <article className="rnh-environment">
          <img src="https://images.unsplash.com/photo-1532629345422-7515f3d16bb6?auto=format&fit=crop&w=1200&q=85" alt="Hands planting a young tree" loading="lazy"/>
          <div><small>01 / ENVIRONMENT</small><h3>Leave the<br/>earth better.</h3><p>Greening drives · Clean surroundings · Sustainability</p></div><b>↗</b>
        </article>
        <article className="rnh-community">
          <small>02</small><span>✳</span><div><p>COMMUNITY WELLBEING</p><h3>Care that<br/>travels further.</h3><small>Health awareness · Outreach · Community support</small></div><i/>
        </article>
        <article className="rnh-knowledge">
          <div><img src="https://images.unsplash.com/photo-1529390079861-591de354faf5?auto=format&fit=crop&w=900&q=85" alt="Students learning together" loading="lazy"/></div>
          <section><small>03 / KNOWLEDGE</small><h3>Share what<br/>you know.</h3><p>Education support, digital literacy and awareness programmes.</p><b><Outbound/></b></section>
        </article>
        <article className="rnh-citizenship">
          <small>04 — ACTIVE CITIZENSHIP</small><div className="rnh-bars"><i/><i/><i/><i/><i/></div><h3>Small acts.<br/><em>Lasting impact.</em></h3><p>Be part of organised efforts that strengthen the communities around us.</p><a href="#journey">Find your starting point <Forward/></a>
        </article>
      </div>
      <p className="rnh-disclaimer">Illustrative areas of NSS engagement. Activities are announced through the institutional portal.</p>
    </section>

    <section className="rnh-ledger">
      <div className="rnh-ledger-copy"><p className="rnh-kicker">03 — SERVICE, MADE VISIBLE</p><h2>Every hour<br/>has a <em>story.</em></h2><p>The NSS portal brings participation into one organised space. Find programmes, register for activities and keep your service record connected to the work you do.</p><Link to="/login">Explore the volunteer portal <Outbound/></Link></div>
      <div className="rnh-record">
        <div className="rnh-record-head"><span>THE SERVICE RECORD</span><span>REC / NSS / 001</span></div>
        <div className="rnh-record-number">01 <small>/ PARTICIPATE</small></div><div className="rnh-record-rule"><i/></div>
        <div className="rnh-record-labels"><span>Discover an initiative</span><span>Register and take part</span></div>
        <div className="rnh-record-bottom"><b>NSS<br/>✳<br/>REC</b><p>One platform.<br/><strong>A connected record of service.</strong></p><small>17° / 83°<br/>SERVICE IN MOTION</small></div>
      </div>
    </section>

    <section className="rnh-journey" id="journey">
      <div className="rnh-journey-intro"><p className="rnh-kicker">04 — YOUR NEXT CHAPTER</p><h2>Start with<br/><em>one yes.</em></h2><p>You do not need to have everything figured out. You only need to begin.</p></div>
      <div className="rnh-steps">
        <article><b>I</b><div><h3>Discover</h3><p>Explore activities and initiatives shared by the college NSS team.</p></div><span>⌕</span></article>
        <article><b>II</b><div><h3>Take part</h3><p>Register for a programme and contribute alongside your community.</p></div><span>↗</span></article>
        <article><b>III</b><div><h3>Make it count</h3><p>Keep your participation and service record connected in the NSS portal.</p></div><span>✳</span></article>
      </div>
    </section>

    <section className="rnh-final">
      <div className="rnh-final-orbit"/>
      <div className="rnh-final-overline"><span>RAGHU ENGINEERING COLLEGE</span><span>NATIONAL SERVICE SCHEME</span></div>
      <p className="rnh-kicker">THE NEXT STEP IS YOURS</p><h2>Be present.<br/><em>Be of service.</em></h2>
      <div className="rnh-final-bottom"><p>Join the work that makes a difference—one person, one initiative, one community at a time.</p><Link to="/login" className="rnh-button light">Enter the NSS portal <Outbound/></Link></div>
    </section>

    <footer className="rnh-footer">
      <div className="rnh-footer-brand"><CollegeMark/><span><b>RAGHU ENGINEERING COLLEGE</b><small>NATIONAL SERVICE SCHEME</small></span></div>
      <p>NOT ME, BUT YOU.</p>
      <div className="rnh-footer-bottom"><span>© {new Date().getFullYear()} Raghu Engineering College NSS</span><Link to="/login">Institutional portal <Outbound/></Link></div>
    </footer>
  </main>;
};
