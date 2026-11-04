import React, { useState } from "react";
import { Link, Navigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import "./LandingPage.css";

// Accessible inline icons
const ArrowRight = () => (
  <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" width="18" height="18">
    <path d="M5 12h14m-6-6 6 6-6 6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);

const ExternalLink = () => (
  <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" width="18" height="18">
    <path d="M7 17 17 7M7 7h10v10" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);

const CheckCircle = () => (
  <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" width="20" height="20">
    <path d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);

const HeartActivity = () => (
  <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" width="24" height="24">
    <path d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);

const Leaf = () => (
  <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" width="24" height="24">
    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);

const UsersGroup = () => (
  <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" width="24" height="24">
    <path d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);

const AwardBadge = () => (
  <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" width="24" height="24">
    <path d="M12 15v2m-6 4l6-2 6 2v-4a7 7 0 10-12 0v4z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);

const BellAlert = () => (
  <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" width="16" height="16">
    <path d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);

export const LandingPage: React.FC = () => {
  const { isAuthenticated, loading } = useAuth();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  if (!loading && isAuthenticated) {
    return <Navigate to="/dashboard" replace />;
  }

  const toggleMenu = () => setMobileMenuOpen((prev) => !prev);
  const closeMenu = () => setMobileMenuOpen(false);

  return (
    <div className="rec-landing">
      {/* Skip to Content for Accessibility */}
      <a href="#main-content" className="skip-link">
        Skip to main content
      </a>

      {/* Institutional Top Header */}
      <header className="rec-header">
        <div className="rec-container rec-header-inner">
          <Link to="/" className="rec-brand" onClick={closeMenu}>
            <div className="rec-emblem-badge" aria-hidden="true">
              <span>REC</span>
              <small>NSS</small>
            </div>
            <div className="rec-brand-text">
              <span className="rec-college-name">Raghu Engineering College</span>
              <span className="rec-portal-subtitle">National Service Scheme · Autonomous Chapter</span>
            </div>
          </Link>

          <button
            type="button"
            className={`rec-mobile-toggle ${mobileMenuOpen ? "active" : ""}`}
            onClick={toggleMenu}
            aria-expanded={mobileMenuOpen}
            aria-label="Toggle navigation menu"
          >
            <span />
            <span />
            <span />
          </button>

          <nav className={`rec-nav-menu ${mobileMenuOpen ? "open" : ""}`} aria-label="Main Navigation">
            <a href="#about" onClick={closeMenu} className="rec-nav-link">
              About NSS
            </a>
            <a href="#initiatives" onClick={closeMenu} className="rec-nav-link">
              Initiatives
            </a>
            <a href="#records" onClick={closeMenu} className="rec-nav-link">
              Service Ledger
            </a>
            <a href="#journey" onClick={closeMenu} className="rec-nav-link">
              How to Join
            </a>
            <Link to="/login" onClick={closeMenu} className="rec-btn-login">
              <span>Portal Sign In</span>
              <ArrowRight />
            </Link>
          </nav>
        </div>
      </header>

      {/* Main Content Container */}
      <main id="main-content">
        {/* Viewport-Optimized Hero Section */}
        <section className="rec-hero-section">
          <div className="rec-container rec-hero-grid">
            <div className="rec-hero-content">
              <div className="rec-pill-badge">
                <span className="rec-pill-dot" />
                <span>Ministry of Youth Affairs & Sports · Govt. of India</span>
              </div>

              <h1 className="rec-hero-title">
                Youth in Service of the <span className="rec-accent-text">Nation</span>
              </h1>

              <p className="rec-hero-description">
                Raghu Engineering College National Service Scheme empowers future engineers with a sense of social responsibility, community empathy, and collaborative civic leadership.
              </p>

              <div className="rec-hero-actions">
                <Link to="/login" className="rec-btn-primary">
                  <span>Enter Volunteer Portal</span>
                  <ArrowRight />
                </Link>
                <a href="#initiatives" className="rec-btn-secondary">
                  <span>Explore Initiatives</span>
                </a>
              </div>

              <div className="rec-motto-banner">
                <div className="rec-motto-icon">
                  <AwardBadge />
                </div>
                <div>
                  <span className="rec-motto-label">Official Motto</span>
                  <p className="rec-motto-quote">"NOT ME, BUT YOU"</p>
                </div>
              </div>
            </div>

            <div className="rec-hero-showcase">
              <div className="rec-surface-card rec-showcase-main">
                <div className="rec-showcase-image-wrapper">
                  <img
                    src="https://images.unsplash.com/photo-1593113598332-cd288d649433?auto=format&fit=crop&w=1200&q=80"
                    alt="Raghu Engineering College NSS Volunteers in community service"
                    className="rec-showcase-image"
                  />
                  <div className="rec-image-gradient-overlay" />
                  <div className="rec-image-caption">
                    <span className="rec-caption-tag">On-Ground Action</span>
                    <h4>Community Health & Literacy Drives</h4>
                  </div>
                </div>

                <div className="rec-metrics-grid">
                  <div className="rec-metric-card">
                    <span className="rec-metric-val">1,200+</span>
                    <span className="rec-metric-lbl">Active Volunteers</span>
                  </div>
                  <div className="rec-metric-card">
                    <span className="rec-metric-val">25,000+</span>
                    <span className="rec-metric-lbl">Hours of Service</span>
                  </div>
                  <div className="rec-metric-card">
                    <span className="rec-metric-val">40+</span>
                    <span className="rec-metric-lbl">Annual Drives</span>
                  </div>
                  <div className="rec-metric-card">
                    <span className="rec-metric-val">04</span>
                    <span className="rec-metric-lbl">Campus Units</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* Full-Bleed Live Announcement Ticker */}
        <div className="rec-announcement-ticker" aria-label="Portal Announcements">
          <div className="rec-container rec-ticker-inner">
            <div className="rec-ticker-badge">
              <BellAlert />
              <span>LIVE UPDATES</span>
            </div>
            <div className="rec-ticker-content">
              <span>Annual Blood Donation Mega-Camp scheduled at College Auditorium</span>
              <span className="rec-ticker-sep">·</span>
              <span>120+ Units Mobilized in recent Visakhapatnam Rural Health Drive</span>
              <span className="rec-ticker-sep">·</span>
              <span>Online NSS Credit Hour Logging Open for All Registered Units</span>
            </div>
            <Link to="/login" className="rec-ticker-link">
              <span>View Portal Noticeboard</span>
              <ArrowRight />
            </Link>
          </div>
        </div>

        {/* Purpose & College Ideals Section */}
        <section id="about" className="rec-section rec-bg-subtle">
          <div className="rec-container">
            <div className="rec-section-header">
              <span className="rec-section-kicker">01 — Core Philosophy</span>
              <h2 className="rec-section-title">An Education Rooted in Humanity</h2>
              <p className="rec-section-desc">
                Engineering excellence reaches its true summit when paired with empathy. Raghu NSS bridges technical innovation with the grassroots needs of surrounding rural and semi-urban communities.
              </p>
            </div>

            <div className="rec-pillars-grid">
              <div className="rec-pillar-card">
                <div className="rec-pillar-icon rec-icon-navy">
                  <UsersGroup />
                </div>
                <h3>Community Empathy</h3>
                <p>
                  Immersing young engineers in real-world village contexts to appreciate social challenges and cultivate genuine human compassion.
                </p>
                <ul className="rec-pillar-list">
                  <li><CheckCircle /> Village survey & sanitation outreach</li>
                  <li><CheckCircle /> Regular field visit immersion</li>
                </ul>
              </div>

              <div className="rec-pillar-card">
                <div className="rec-pillar-icon rec-icon-crimson">
                  <HeartActivity />
                </div>
                <h3>Civic Health & Welfare</h3>
                <p>
                  Mobilizing quick, life-saving initiatives including large-scale voluntary blood donation camps, maternal health awareness, and hygiene drives.
                </p>
                <ul className="rec-pillar-list">
                  <li><CheckCircle /> Annual blood donor directory</li>
                  <li><CheckCircle /> Free diagnostic medical checkups</li>
                </ul>
              </div>

              <div className="rec-pillar-card">
                <div className="rec-pillar-icon rec-icon-green">
                  <Leaf />
                </div>
                <h3>Eco-Sustainability</h3>
                <p>
                  Leading afforestation initiatives, plastic-free campaigns, and clean water conservation for future resilience.
                </p>
                <ul className="rec-pillar-list">
                  <li><CheckCircle /> Campus & regional sapling drives</li>
                  <li><CheckCircle /> Swachh Bharat community cleanup</li>
                </ul>
              </div>
            </div>
          </div>
        </section>

        {/* Flagship Initiatives - Expanded Viewport Grid */}
        <section id="initiatives" className="rec-section">
          <div className="rec-container">
            <div className="rec-section-header">
              <span className="rec-section-kicker">02 — Flagship Drives</span>
              <h2 className="rec-section-title">Where Purpose Meets Measurable Impact</h2>
              <p className="rec-section-desc">
                From village adoption in Visakhapatnam district to campus green initiatives, discover the domains where our volunteers make a lasting difference.
              </p>
            </div>

            <div className="rec-initiatives-grid">
              <article className="rec-initiative-card">
                <div className="rec-card-media">
                  <img
                    src="https://images.unsplash.com/photo-1615461066841-6116e61058f4?auto=format&fit=crop&w=700&q=80"
                    alt="Voluntary Blood Donation Drive"
                    loading="lazy"
                  />
                  <span className="rec-media-tag">Health & Welfare</span>
                </div>
                <div className="rec-card-content">
                  <h3>Voluntary Blood Donation Drives</h3>
                  <p>
                    Conducted in coordination with government general hospitals and the Indian Red Cross, generating hundreds of verified blood units every semester.
                  </p>
                  <div className="rec-card-footer">
                    <span className="rec-card-stat">Semestral Drives</span>
                    <Link to="/login" className="rec-card-link">
                      <span>Register to Donate</span>
                      <ExternalLink />
                    </Link>
                  </div>
                </div>
              </article>

              <article className="rec-initiative-card">
                <div className="rec-card-media">
                  <img
                    src="https://images.unsplash.com/photo-1542601906990-b4d3fb778b09?auto=format&fit=crop&w=700&q=80"
                    alt="Green Campus Tree Plantation"
                    loading="lazy"
                  />
                  <span className="rec-media-tag">Environment</span>
                </div>
                <div className="rec-card-content">
                  <h3>Green Campus & Haritha Haram</h3>
                  <p>
                    Massive tree plantation campaigns across Dakamarri and neighboring villages, promoting groundwater recharge and bio-diversity conservation.
                  </p>
                  <div className="rec-card-footer">
                    <span className="rec-card-stat">3,000+ Saplings</span>
                    <Link to="/login" className="rec-card-link">
                      <span>Join Eco-Club</span>
                      <ExternalLink />
                    </Link>
                  </div>
                </div>
              </article>

              <article className="rec-initiative-card">
                <div className="rec-card-media">
                  <img
                    src="https://images.unsplash.com/photo-1509062522246-3755977927d7?auto=format&fit=crop&w=700&q=80"
                    alt="Digital Literacy & Rural Education"
                    loading="lazy"
                  />
                  <span className="rec-media-tag">Education</span>
                </div>
                <div className="rec-card-content">
                  <h3>Digital Literacy & School Mentorship</h3>
                  <p>
                    Volunteers visit local government primary and high schools to deliver basic computer literacy, mathematics mentoring, and career guidance.
                  </p>
                  <div className="rec-card-footer">
                    <span className="rec-card-stat">12 Adopted Schools</span>
                    <Link to="/login" className="rec-card-link">
                      <span>Volunteer as Tutor</span>
                      <ExternalLink />
                    </Link>
                  </div>
                </div>
              </article>

              <article className="rec-initiative-card">
                <div className="rec-card-media">
                  <img
                    src="https://images.unsplash.com/photo-1532629345422-7515f3d16bb6?auto=format&fit=crop&w=700&q=80"
                    alt="Swachh Bharat Cleanliness Rally"
                    loading="lazy"
                  />
                  <span className="rec-media-tag">Civic Service</span>
                </div>
                <div className="rec-card-content">
                  <h3>Swachh Bharat & Village Upliftment</h3>
                  <p>
                    Targeted sanitation drives, solid waste management awareness, and plastic elimination campaigns conducted directly with gram panchayats.
                  </p>
                  <div className="rec-card-footer">
                    <span className="rec-card-stat">Rural Outreach</span>
                    <Link to="/login" className="rec-card-link">
                      <span>View Next Event</span>
                      <ExternalLink />
                    </Link>
                  </div>
                </div>
              </article>
            </div>
          </div>
        </section>

        {/* Digital Service Record & Transparency */}
        <section id="records" className="rec-section rec-bg-subtle">
          <div className="rec-container rec-ledger-grid">
            <div className="rec-ledger-info">
              <span className="rec-section-kicker">03 — Digital Tracking</span>
              <h2 className="rec-section-title">Transparent, Verified Volunteer Records</h2>
              <p className="rec-section-desc">
                The Raghu NSS portal delivers an institutional tracking system. Every volunteer's attendance, service hours, camp participation, and leadership roles are verified by Programme Officers.
              </p>

              <div className="rec-feature-list">
                <div className="rec-feature-item">
                  <div className="rec-feature-icon">
                    <CheckCircle />
                  </div>
                  <div>
                    <h4>Digital Attendance & Geofencing</h4>
                    <p>Real-time session attendance during campus events and rural special camps.</p>
                  </div>
                </div>

                <div className="rec-feature-item">
                  <div className="rec-feature-icon">
                    <CheckCircle />
                  </div>
                  <div>
                    <h4>Official NSS Credit Hours</h4>
                    <p>Accurate accounting of minimum required hours for university degree certification.</p>
                  </div>
                </div>

                <div className="rec-feature-item">
                  <div className="rec-feature-icon">
                    <CheckCircle />
                  </div>
                  <div>
                    <h4>Automated Certificate Issuance</h4>
                    <p>Tamper-proof participation and completion certificates authenticated by the NSS Cell.</p>
                  </div>
                </div>
              </div>

              <div className="rec-ledger-action">
                <Link to="/login" className="rec-btn-primary">
                  <span>Sign In to Check Your Hours</span>
                  <ArrowRight />
                </Link>
              </div>
            </div>

            <div className="rec-ledger-visual">
              <div className="rec-passport-card">
                <div className="rec-passport-header">
                  <div className="rec-passport-chip">
                    <span className="rec-chip-ring" />
                    <span>NSS VOLUNTEER RECORD</span>
                  </div>
                  <span className="rec-passport-status">VERIFIED</span>
                </div>

                <div className="rec-passport-body">
                  <div className="rec-passport-profile">
                    <div className="rec-avatar-placeholder">
                      <span>VK</span>
                    </div>
                    <div>
                      <h4 className="rec-volunteer-name">Raghu NSS Volunteer</h4>
                      <p className="rec-volunteer-unit">Unit 01 · Department of CSE</p>
                    </div>
                  </div>

                  <div className="rec-passport-stats">
                    <div className="rec-pass-stat">
                      <span className="rec-pass-val">120 hrs</span>
                      <span className="rec-pass-lbl">Accumulated Service</span>
                    </div>
                    <div className="rec-pass-stat">
                      <span className="rec-pass-val">08 Drives</span>
                      <span className="rec-pass-lbl">Completed Events</span>
                    </div>
                    <div className="rec-pass-stat">
                      <span className="rec-pass-val">Grade A</span>
                      <span className="rec-pass-lbl">Performance Score</span>
                    </div>
                  </div>

                  <div className="rec-passport-progress-box">
                    <div className="rec-prog-labels">
                      <span>Degree NSS Requirement</span>
                      <span>100% Complete</span>
                    </div>
                    <div className="rec-prog-track">
                      <div className="rec-prog-bar" style={{ width: "100%" }} />
                    </div>
                  </div>
                </div>

                <div className="rec-passport-footer">
                  <span className="rec-secure-seal">Raghu Engineering College · NSS Cell</span>
                  <span className="rec-code">ID: REC-NSS-2026-V</span>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* 3-Step Volunteer Flow */}
        <section id="journey" className="rec-section">
          <div className="rec-container">
            <div className="rec-section-header">
              <span className="rec-section-kicker">04 — Get Involved</span>
              <h2 className="rec-section-title">How to Join Raghu NSS</h2>
              <p className="rec-section-desc">
                Becoming a registered volunteer is seamless. Follow these three steps to begin your journey of impactful community service.
              </p>
            </div>

            <div className="rec-steps-grid">
              <div className="rec-step-card">
                <div className="rec-step-number">01</div>
                <h3>Enroll & Create Profile</h3>
                <p>
                  Access the portal with your student institutional ID, select your preferred unit, and submit your registration form.
                </p>
              </div>

              <div className="rec-step-card">
                <div className="rec-step-number">02</div>
                <h3>Participate in Drives</h3>
                <p>
                  Receive notifications of upcoming blood donation camps, cleanups, and rural education initiatives, and confirm attendance.
                </p>
              </div>

              <div className="rec-step-card">
                <div className="rec-step-number">03</div>
                <h3>Earn Service Credit</h3>
                <p>
                  Log verified service hours, lead project teams, and earn university-recognized NSS merit certificates upon graduation.
                </p>
              </div>
            </div>
          </div>
        </section>

        {/* Call to Action Banner */}
        <section className="rec-cta-section">
          <div className="rec-container">
            <div className="rec-cta-card">
              <div className="rec-cta-content">
                <span className="rec-cta-tag">Ready to Make an Impact?</span>
                <h2 className="rec-cta-title">Step Forward. Be the Change.</h2>
                <p className="rec-cta-desc">
                  Join hundreds of students currently turning empathy into real progress across Andhra Pradesh. Enter the Raghu NSS portal to start.
                </p>
                <div className="rec-cta-buttons">
                  <Link to="/login" className="rec-btn-cta-primary">
                    <span>Enter Portal Now</span>
                    <ArrowRight />
                  </Link>
                  <a href="#about" className="rec-btn-cta-secondary">
                    <span>Learn More About Units</span>
                  </a>
                </div>
              </div>
            </div>
          </div>
        </section>
      </main>

      {/* Institutional Footer */}
      <footer className="rec-footer">
        <div className="rec-container rec-footer-grid">
          <div className="rec-footer-col rec-col-brand">
            <div className="rec-footer-brand">
              <div className="rec-footer-emblem">REC</div>
              <div>
                <h4>Raghu Engineering College</h4>
                <p>National Service Scheme Cell</p>
              </div>
            </div>
            <p className="rec-footer-address">
              Dakamarri, Bheemunipatnam Mandal,<br />
              Visakhapatnam District, Andhra Pradesh — 531162.<br />
              Affiliated to JNTU-GV, Approved by AICTE, Accredited by NAAC 'A+' Grade.
            </p>
            <p className="rec-footer-motto-callout">Motto: "NOT ME, BUT YOU"</p>
          </div>

          <div className="rec-footer-col">
            <h5 className="rec-footer-heading">Quick Links</h5>
            <ul className="rec-footer-links">
              <li><a href="#about">About Raghu NSS</a></li>
              <li><a href="#initiatives">Flagship Initiatives</a></li>
              <li><a href="#records">Service Ledger</a></li>
              <li><a href="#journey">Volunteer Enrolment</a></li>
              <li><Link to="/login">Portal Login</Link></li>
            </ul>
          </div>

          <div className="rec-footer-col">
            <h5 className="rec-footer-heading">Initiative Areas</h5>
            <ul className="rec-footer-links">
              <li><span>Blood Donation Camps</span></li>
              <li><span>Swachh Bharat Abhiyan</span></li>
              <li><span>Haritha Haram Green Drives</span></li>
              <li><span>Rural Education & Literacy</span></li>
              <li><span>Disaster Relief Support</span></li>
            </ul>
          </div>

          <div className="rec-footer-col">
            <h5 className="rec-footer-heading">Contact NSS Cell</h5>
            <p className="rec-contact-info">
              <strong>Programme Coordinator:</strong><br />
              Raghu Engineering College NSS Bureau<br />
              Email: nss@raghuenggcollege.in<br />
              Helpline: +91 8922 248001 / 248002
            </p>
            <div className="rec-footer-portal-link">
              <Link to="/login" className="rec-footer-login-btn">
                <span>Access Student / Officer Login</span>
                <ArrowRight />
              </Link>
            </div>
          </div>
        </div>

        <div className="rec-footer-bottom">
          <div className="rec-container rec-footer-bottom-inner">
            <p>© {new Date().getFullYear()} Raghu Engineering College NSS. All Rights Reserved.</p>
            <div className="rec-footer-bottom-links">
              <span>Ministry of Youth Affairs & Sports</span>
              <span>·</span>
              <span>Govt. of India</span>
              <span>·</span>
              <span>JNTUGV NSS Bureau</span>
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
};
