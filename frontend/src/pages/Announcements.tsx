import React, { useState, useEffect, useCallback } from "react";
import { useAuth } from "../context/AuthContext";
import { apiRequest } from "../api/client";

interface Announcement {
  announcementId: string;
  title: string;
  content: string;
  unitId: string | null;
  unitName: string;
  createdByName: string;
  publishedAt: string;
  expiresAt: string | null;
}

interface NotificationItem {
  notificationId: string;
  title: string;
  message: string;
  isRead: boolean;
  createdAt: string;
}

export const Announcements: React.FC = () => {
  const { user } = useAuth();
  const isOfficerOrAdmin = user?.roles.some((r) =>
    ["ADMIN", "FACULTY_COORDINATOR", "PROGRAMME_OFFICER"].includes(r)
  );

  const [announcements, setAnnouncements] = useState<Announcement[]>([]);
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [units, setUnits] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  // Filter
  const [selectedUnitFilter, setSelectedUnitFilter] = useState<string>("ALL");

  // Create modal state
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newTitle, setNewTitle] = useState("");
  const [newContent, setNewContent] = useState("");
  const [newUnitId, setNewUnitId] = useState("");
  const [newExpiresAt, setNewExpiresAt] = useState("");
  const [creating, setCreating] = useState(false);

  // Notification panel toggle
  const [showNotifications, setShowNotifications] = useState(false);

  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const [annRes, unitsRes] = await Promise.all([
        apiRequest<Announcement[]>("/announcements"),
        apiRequest<{ content: any[] }>("/units?size=100").catch(() => ({ content: [] }))
      ]);
      setAnnouncements(annRes || []);
      setUnits(unitsRes.content || []);

      if (user) {
        const notifs = await apiRequest<NotificationItem[]>("/notifications/my").catch(() => []);
        setNotifications(notifs || []);
      }
    } catch (err: any) {
      setError(err.message || "Failed to load announcements.");
    } finally {
      setLoading(false);
    }
  }, [user]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleCreateAnnouncement = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTitle.trim() || !newContent.trim()) {
      setError("Title and content are required.");
      return;
    }

    try {
      setCreating(true);
      setError(null);
      await apiRequest<Announcement>("/announcements", {
        method: "POST",
        body: JSON.stringify({
          title: newTitle.trim(),
          content: newContent.trim(),
          unitId: newUnitId ? newUnitId : null,
          expiresAt: newExpiresAt ? new Date(newExpiresAt).toISOString() : null
        })
      });
      setSuccess("Announcement published and notifications broadcast successfully.");
      setShowCreateModal(false);
      setNewTitle("");
      setNewContent("");
      setNewUnitId("");
      setNewExpiresAt("");
      loadData();
    } catch (err: any) {
      setError(err.message || "Failed to create announcement.");
    } finally {
      setCreating(false);
    }
  };

  const handleDeleteAnnouncement = async (id: string) => {
    if (!window.confirm("Are you sure you want to delete this announcement?")) return;
    try {
      await apiRequest(`/announcements/${id}`, { method: "DELETE" });
      setSuccess("Announcement deleted.");
      loadData();
    } catch (err: any) {
      setError(err.message || "Failed to delete announcement.");
    }
  };

  const handleMarkNotificationRead = async (id: string) => {
    try {
      await apiRequest(`/notifications/${id}/read`, { method: "POST" });
      setNotifications((prev) =>
        prev.map((n) => (n.notificationId === id ? { ...n, isRead: true } : n))
      );
    } catch {
      // Ignored
    }
  };

  const unreadNotifsCount = notifications.filter((n) => !n.isRead).length;

  const filteredAnnouncements = announcements.filter((a) => {
    if (selectedUnitFilter === "ALL") return true;
    if (selectedUnitFilter === "COLLEGE") return a.unitId === null;
    return a.unitId === selectedUnitFilter;
  });

  return (
    <div className="container" style={{ padding: "2rem 1rem", maxWidth: "1200px", margin: "0 auto" }}>
      {/* Header */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "2rem", flexWrap: "wrap", gap: "1rem" }}>
        <div>
          <h1 style={{ margin: 0, fontSize: "1.875rem", fontWeight: 700, color: "var(--text-main, #0f172a)" }}>
            Announcements & Notices
          </h1>
          <p style={{ margin: "0.25rem 0 0", color: "var(--text-muted, #64748b)", fontSize: "0.95rem" }}>
            College-wide bulletins, unit circulars, and instant activity updates
          </p>
        </div>

        <div style={{ display: "flex", gap: "0.75rem", alignItems: "center" }}>
          {/* Notifications Toggle */}
          <button
            onClick={() => setShowNotifications(!showNotifications)}
            style={{
              padding: "0.625rem 1rem",
              borderRadius: "0.5rem",
              border: "1px solid var(--border-color, #e2e8f0)",
              backgroundColor: showNotifications ? "#f1f5f9" : "#ffffff",
              cursor: "pointer",
              fontWeight: 600,
              fontSize: "0.875rem",
              display: "flex",
              alignItems: "center",
              gap: "0.5rem"
            }}
          >
            Notifications
            {unreadNotifsCount > 0 && (
              <span style={{ backgroundColor: "#ef4444", color: "#ffffff", borderRadius: "9999px", padding: "0.15rem 0.45rem", fontSize: "0.75rem", fontWeight: 700 }}>
                {unreadNotifsCount}
              </span>
            )}
          </button>

          {/* New Announcement Button */}
          {isOfficerOrAdmin && (
            <button
              onClick={() => setShowCreateModal(true)}
              style={{
                backgroundColor: "var(--primary, #1e40af)",
                color: "#ffffff",
                padding: "0.625rem 1.25rem",
                borderRadius: "0.5rem",
                border: "none",
                fontWeight: 600,
                cursor: "pointer",
                boxShadow: "0 1px 3px rgba(0,0,0,0.1)"
              }}
            >
              + Post Announcement
            </button>
          )}
        </div>
      </div>

      {/* Notifications Banner */}
      {error && (
        <div style={{ padding: "1rem", backgroundColor: "#fef2f2", color: "#991b1b", borderRadius: "0.5rem", marginBottom: "1.5rem", border: "1px solid #fecaca" }}>
          {error}
        </div>
      )}
      {success && (
        <div style={{ padding: "1rem", backgroundColor: "#f0fdf4", color: "#166534", borderRadius: "0.5rem", marginBottom: "1.5rem", border: "1px solid #bbf7d0" }}>
          {success}
        </div>
      )}

      {/* Notifications Drawer */}
      {showNotifications && (
        <div style={{ background: "#ffffff", borderRadius: "0.75rem", border: "1px solid var(--border-color, #e2e8f0)", padding: "1.5rem", marginBottom: "2rem", boxShadow: "0 4px 6px -1px rgba(0,0,0,0.05)" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1rem" }}>
            <h3 style={{ margin: 0, fontSize: "1.125rem", fontWeight: 700 }}>My Broadcast Notifications</h3>
            <span style={{ fontSize: "0.875rem", color: "#64748b" }}>{unreadNotifsCount} unread</span>
          </div>

          {notifications.length === 0 ? (
            <div style={{ color: "#64748b", fontSize: "0.875rem" }}>No personal notifications.</div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem", maxHeight: "300px", overflowY: "auto" }}>
              {notifications.map((n) => (
                <div
                  key={n.notificationId}
                  style={{
                    padding: "0.75rem 1rem",
                    borderRadius: "0.5rem",
                    border: "1px solid",
                    borderColor: n.isRead ? "#e2e8f0" : "#93c5fd",
                    backgroundColor: n.isRead ? "#f8fafc" : "#eff6ff",
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center"
                  }}
                >
                  <div>
                    <div style={{ fontWeight: 600, fontSize: "0.875rem", color: "#1e293b" }}>{n.title}</div>
                    <div style={{ fontSize: "0.8125rem", color: "#475569", marginTop: "0.2rem" }}>{n.message}</div>
                    <div style={{ fontSize: "0.75rem", color: "#94a3b8", marginTop: "0.25rem" }}>
                      {new Date(n.createdAt).toLocaleString()}
                    </div>
                  </div>
                  {!n.isRead && (
                    <button
                      onClick={() => handleMarkNotificationRead(n.notificationId)}
                      style={{
                        padding: "0.25rem 0.5rem",
                        backgroundColor: "#ffffff",
                        border: "1px solid #cbd5e1",
                        borderRadius: "0.25rem",
                        fontSize: "0.75rem",
                        cursor: "pointer"
                      }}
                    >
                      Mark read
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Filter Row */}
      <div style={{ display: "flex", gap: "0.75rem", marginBottom: "1.5rem", alignItems: "center" }}>
        <span style={{ fontSize: "0.875rem", fontWeight: 600, color: "#475569" }}>Filter by Unit:</span>
        <button
          onClick={() => setSelectedUnitFilter("ALL")}
          style={{
            padding: "0.375rem 0.75rem",
            borderRadius: "0.375rem",
            border: "1px solid",
            borderColor: selectedUnitFilter === "ALL" ? "var(--primary, #1e40af)" : "#cbd5e1",
            backgroundColor: selectedUnitFilter === "ALL" ? "var(--primary, #1e40af)" : "#ffffff",
            color: selectedUnitFilter === "ALL" ? "#ffffff" : "#475569",
            fontSize: "0.8125rem",
            fontWeight: 600,
            cursor: "pointer"
          }}
        >
          All
        </button>
        <button
          onClick={() => setSelectedUnitFilter("COLLEGE")}
          style={{
            padding: "0.375rem 0.75rem",
            borderRadius: "0.375rem",
            border: "1px solid",
            borderColor: selectedUnitFilter === "COLLEGE" ? "var(--primary, #1e40af)" : "#cbd5e1",
            backgroundColor: selectedUnitFilter === "COLLEGE" ? "var(--primary, #1e40af)" : "#ffffff",
            color: selectedUnitFilter === "COLLEGE" ? "#ffffff" : "#475569",
            fontSize: "0.8125rem",
            fontWeight: 600,
            cursor: "pointer"
          }}
        >
          College-Wide
        </button>
        {units.map((u) => (
          <button
            key={u.unitId}
            onClick={() => setSelectedUnitFilter(u.unitId.toString())}
            style={{
              padding: "0.375rem 0.75rem",
              borderRadius: "0.375rem",
              border: "1px solid",
              borderColor: selectedUnitFilter === u.unitId.toString() ? "var(--primary, #1e40af)" : "#cbd5e1",
              backgroundColor: selectedUnitFilter === u.unitId.toString() ? "var(--primary, #1e40af)" : "#ffffff",
              color: selectedUnitFilter === u.unitId.toString() ? "#ffffff" : "#475569",
              fontSize: "0.8125rem",
              fontWeight: 600,
              cursor: "pointer"
            }}
          >
            {u.unitName}
          </button>
        ))}
      </div>

      {loading && (
        <div style={{ textAlign: "center", padding: "3rem", color: "#64748b" }}>
          Loading announcements...
        </div>
      )}

      {/* Announcements Feed */}
      {!loading && (
        <div style={{ display: "flex", flexDirection: "column", gap: "1.25rem" }}>
          {filteredAnnouncements.length === 0 ? (
            <div style={{ background: "#ffffff", padding: "3rem", textAlign: "center", borderRadius: "0.75rem", border: "1px solid var(--border-color, #e2e8f0)", color: "#64748b" }}>
              No announcements found for this filter.
            </div>
          ) : (
            filteredAnnouncements.map((a) => (
              <div
                key={a.announcementId}
                style={{
                  background: "#ffffff",
                  padding: "1.5rem",
                  borderRadius: "0.75rem",
                  border: "1px solid var(--border-color, #e2e8f0)",
                  boxShadow: "0 1px 3px rgba(0,0,0,0.05)"
                }}
              >
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: "0.75rem" }}>
                  <div>
                    <div style={{ display: "flex", gap: "0.5rem", alignItems: "center", marginBottom: "0.5rem" }}>
                      <span
                        style={{
                          backgroundColor: a.unitId ? "#e0e7ff" : "#fef3c7",
                          color: a.unitId ? "#3730a3" : "#92400e",
                          padding: "0.2rem 0.5rem",
                          borderRadius: "9999px",
                          fontSize: "0.75rem",
                          fontWeight: 700
                        }}
                      >
                        {a.unitName}
                      </span>
                      <span style={{ fontSize: "0.8125rem", color: "#64748b" }}>
                        Posted by {a.createdByName} on {new Date(a.publishedAt).toLocaleDateString()}
                      </span>
                    </div>
                    <h2 style={{ margin: 0, fontSize: "1.25rem", fontWeight: 700, color: "#0f172a" }}>
                      {a.title}
                    </h2>
                  </div>

                  {isOfficerOrAdmin && (
                    <button
                      onClick={() => handleDeleteAnnouncement(a.announcementId)}
                      style={{
                        background: "none",
                        border: "none",
                        color: "#ef4444",
                        fontSize: "0.8125rem",
                        cursor: "pointer",
                        fontWeight: 600
                      }}
                    >
                      Delete
                    </button>
                  )}
                </div>

                <div style={{ color: "#334155", fontSize: "0.95rem", lineHeight: 1.6, whiteSpace: "pre-wrap" }}>
                  {a.content}
                </div>

                {a.expiresAt && (
                  <div style={{ marginTop: "1rem", fontSize: "0.75rem", color: "#94a3b8" }}>
                    Notice valid until: {new Date(a.expiresAt).toLocaleDateString()}
                  </div>
                )}
              </div>
            ))
          )}
        </div>
      )}

      {/* Post Modal */}
      {showCreateModal && (
        <div style={{ position: "fixed", inset: 0, backgroundColor: "rgba(0,0,0,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#ffffff", padding: "2rem", borderRadius: "0.75rem", width: "90%", maxWidth: "550px", boxShadow: "0 20px 25px -5px rgba(0,0,0,0.1)" }}>
            <h3 style={{ margin: "0 0 1rem", fontSize: "1.25rem", fontWeight: 700 }}>Post Announcement</h3>
            <form onSubmit={handleCreateAnnouncement}>
              <div style={{ marginBottom: "1rem" }}>
                <label style={{ display: "block", fontSize: "0.875rem", fontWeight: 600, marginBottom: "0.25rem" }}>
                  Announcement Title *
                </label>
                <input
                  type="text"
                  value={newTitle}
                  onChange={(e) => setNewTitle(e.target.value)}
                  placeholder="e.g. Schedule Update for Annual Camp"
                  required
                  style={{ width: "100%", padding: "0.5rem", borderRadius: "0.375rem", border: "1px solid #cbd5e1" }}
                />
              </div>

              <div style={{ marginBottom: "1rem" }}>
                <label style={{ display: "block", fontSize: "0.875rem", fontWeight: 600, marginBottom: "0.25rem" }}>
                  Target Audience / Unit
                </label>
                <select
                  value={newUnitId}
                  onChange={(e) => setNewUnitId(e.target.value)}
                  style={{ width: "100%", padding: "0.5rem", borderRadius: "0.375rem", border: "1px solid #cbd5e1" }}
                >
                  <option value="">College-Wide (Broadcast to all students & units)</option>
                  {units.map((u) => (
                    <option key={u.unitId} value={u.unitId}>
                      {u.unitName} ({u.unitNumber})
                    </option>
                  ))}
                </select>
              </div>

              <div style={{ marginBottom: "1rem" }}>
                <label style={{ display: "block", fontSize: "0.875rem", fontWeight: 600, marginBottom: "0.25rem" }}>
                  Content / Details *
                </label>
                <textarea
                  value={newContent}
                  onChange={(e) => setNewContent(e.target.value)}
                  rows={4}
                  placeholder="Provide full text of the circular, timing instructions, or venue details..."
                  required
                  style={{ width: "100%", padding: "0.5rem", borderRadius: "0.375rem", border: "1px solid #cbd5e1" }}
                />
              </div>

              <div style={{ marginBottom: "1.5rem" }}>
                <label style={{ display: "block", fontSize: "0.875rem", fontWeight: 600, marginBottom: "0.25rem" }}>
                  Notice Expiry Date (Optional)
                </label>
                <input
                  type="date"
                  value={newExpiresAt}
                  onChange={(e) => setNewExpiresAt(e.target.value)}
                  style={{ width: "100%", padding: "0.5rem", borderRadius: "0.375rem", border: "1px solid #cbd5e1" }}
                />
              </div>

              <div style={{ display: "flex", justifyContent: "flex-end", gap: "0.75rem" }}>
                <button
                  type="button"
                  onClick={() => setShowCreateModal(false)}
                  style={{ padding: "0.5rem 1rem", borderRadius: "0.375rem", border: "1px solid #cbd5e1", background: "#f8fafc", cursor: "pointer" }}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={creating}
                  style={{ padding: "0.5rem 1rem", borderRadius: "0.375rem", border: "none", background: "#1e40af", color: "#ffffff", fontWeight: 600, cursor: "pointer" }}
                >
                  {creating ? "Publishing..." : "Publish & Broadcast"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
