import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { apiRequest, ApiError } from "../api/client";

interface UnitItem {
  unitId: string;
  unitName: string;
  unitNumber: string;
  officerId: string | null;
  officerName: string | null;
  officerEmail: string | null;
  activeMemberCount: number;
  createdAt: string;
}

export const Units: React.FC = () => {
  const { isAdmin, isCoordinatorOrOfficer, hasCapability } = useAuth();
  const canManageUnits = isAdmin || isCoordinatorOrOfficer || hasCapability("UNITS_MANAGE");

  const [units, setUnits] = useState<UnitItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Create Unit Modal
  const [showModal, setShowModal] = useState(false);
  const [unitName, setUnitName] = useState("");
  const [unitNumber, setUnitNumber] = useState("");
  const [creating, setCreating] = useState(false);
  const [modalError, setModalError] = useState<string | null>(null);

  const loadUnits = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await apiRequest<UnitItem[]>("/units");
      setUnits(data);
    } catch (err: unknown) {
      const apiErr = err as ApiError;
      setError(apiErr.message || "Failed to load NSS units.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadUnits();
  }, []);

  const handleCreateUnit = async (e: React.FormEvent) => {
    e.preventDefault();
    setModalError(null);

    const cleanName = unitName.trim();
    const cleanNumber = unitNumber.trim();

    if (!cleanName || !cleanNumber) {
      setModalError("Please provide both Unit Number and Unit Name.");
      return;
    }

    setCreating(true);

    try {
      await apiRequest<UnitItem>("/units", {
        method: "POST",
        body: JSON.stringify({
          unitName: cleanName,
          unitNumber: cleanNumber,
        }),
      });

      setShowModal(false);
      setUnitName("");
      setUnitNumber("");
      loadUnits();
    } catch (err: unknown) {
      const apiErr = err as ApiError;
      setModalError(apiErr.message || "Failed to create unit.");
    } finally {
      setCreating(false);
    }
  };

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h1>NSS Units Management</h1>
          <p className="subtitle">
            Configure institutional NSS units and assign volunteers and officers.
          </p>
        </div>

        {canManageUnits && (
          <button onClick={() => setShowModal(true)} className="btn-primary">
            + Create NSS Unit
          </button>
        )}
      </div>

      {error && (
        <div className="alert alert-error">
          <strong>Error:</strong> {error}
        </div>
      )}

      <div className="section-card">
        {loading ? (
          <p className="loading-state">Loading operational units...</p>
        ) : units.length === 0 ? (
          <div className="empty-state">
            <p>No units currently exist in this institution.</p>
            {canManageUnits && (
              <button
                onClick={() => setShowModal(true)}
                className="btn-primary-sm"
              >
                Create First Unit
              </button>
            )}
          </div>
        ) : (
          <div className="units-table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Unit Number</th>
                  <th>Unit Name</th>
                  <th>Assigned Officer</th>
                  <th>Active Volunteers</th>
                  <th>Created Date</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {units.map((unit) => (
                  <tr key={unit.unitId}>
                    <td>
                      <span className="badge badge-primary">{unit.unitNumber}</span>
                    </td>
                    <td>
                      <strong>{unit.unitName}</strong>
                    </td>
                    <td>
                      {unit.officerName ? (
                        <div>
                          <div>{unit.officerName}</div>
                          <small className="cell-sub">{unit.officerEmail}</small>
                        </div>
                      ) : (
                        <span className="badge badge-muted">Unassigned</span>
                      )}
                    </td>
                    <td>
                      <strong>{unit.activeMemberCount}</strong> volunteers
                    </td>
                    <td>{new Date(unit.createdAt).toLocaleDateString()}</td>
                    <td>
                      <Link
                        to={`/units/${unit.unitId}`}
                        className="table-action-link"
                      >
                        Manage Unit
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {showModal && (
        <div className="modal-backdrop">
          <div className="modal-card">
            <div className="modal-header">
              <h3>Create New NSS Unit</h3>
              <button onClick={() => setShowModal(false)} className="btn-close">
                &times;
              </button>
            </div>

            {modalError && (
              <div className="alert alert-error">
                <strong>Error:</strong> {modalError}
              </div>
            )}

            <form onSubmit={handleCreateUnit} className="form-stack">
              <div className="form-group">
                <label htmlFor="unitNumber">Unit Number / Code *</label>
                <input
                  id="unitNumber"
                  type="text"
                  value={unitNumber}
                  onChange={(e) => setUnitNumber(e.target.value)}
                  placeholder="e.g. UNIT-01"
                  required
                />
              </div>

              <div className="form-group">
                <label htmlFor="unitName">Unit Name *</label>
                <input
                  id="unitName"
                  type="text"
                  value={unitName}
                  onChange={(e) => setUnitName(e.target.value)}
                  placeholder="e.g. Community Welfare Unit A"
                  required
                />
              </div>

              <div className="modal-actions">
                <button
                  type="button"
                  onClick={() => setShowModal(false)}
                  className="btn-secondary"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn-primary"
                  disabled={creating}
                >
                  {creating ? "Creating..." : "Create Unit"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
