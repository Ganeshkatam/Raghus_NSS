import React from "react";
import { Link } from "react-router-dom";

interface ModulePlaceholderProps {
  title: string;
  category: string;
  description: string;
  phase: string;
  features: string[];
}

export const ModulePlaceholder: React.FC<ModulePlaceholderProps> = ({
  title,
  category,
  description,
  phase,
  features,
}) => {
  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <div className="breadcrumbs">
            <Link to="/dashboard">Overview</Link>
            <span>/</span>
            <span>{category}</span>
            <span>/</span>
            <span className="current">{title}</span>
          </div>
          <h1>{title}</h1>
          <p className="subtitle">{description}</p>
        </div>
        <span className="badge badge-primary">{phase}</span>
      </div>

      <div className="section-card">
        <div className="section-header">
          <h2>Planned Module Specifications</h2>
        </div>
        <p className="cell-sub" style={{ marginBottom: "1rem" }}>
          This workflow is specified in the College NSS UI/UX Implementation Specification and connects to the authoritative transactional backend.
        </p>
        <ul style={{ paddingLeft: "1.25rem", color: "var(--text-secondary)", lineHeight: "1.8" }}>
          {features.map((f, i) => (
            <li key={i}>{f}</li>
          ))}
        </ul>
      </div>
    </div>
  );
};
