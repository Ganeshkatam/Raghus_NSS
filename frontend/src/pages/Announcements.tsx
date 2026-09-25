import React, { useState, useEffect, useCallback } from "react";
import { useAuth } from "../context/AuthContext";
import { apiRequest } from "../api/client";
import { CustomSelect } from "../components/CustomSelect";
import { SearchBar } from "../components/SearchBar";

interface Announcement {
  announcementId: string;
  title: string;
  content: string;
  unitId: string | null;
  unitName: string;
  priority?: string;
  createdByName: string;
  publishedAt: string;
  expiresAt: string | null;
}

interface NotificationItem {
  notificationId: string;
  title: string;
  message: string;
  category?: string;
  link?: string | null;
  isRead: boolean;
  createdAt: string;
}

export const Announcements: React.FC = () => {
  const { user, hasCapability } = useAuth();
  const isOfficerOrAdmin = hasCapability("ANNOUNCEMENTS_MANAGE") || user?.roles.some((r) =>
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
  const [selectedPriorityFilter, setSelectedPriorityFilter] = useState<string>("ALL");
  const [searchQuery, setSearchQuery] = useState<string>("");

  // Create modal state
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newTitle, setNewTitle] = useState("");
  const [newContent, setNewContent] = useState("");
  const [newUnitId, setNewUnitId] = useState("");
  const [newPriority, setNewPriority] = useState("NORMAL");
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
          priority: newPriority,
          expiresAt: newExpiresAt ? new Date(newExpiresAt).toISOString() : null
        })
      });
      setSuccess("Announcement published and notifications broadcast successfully.");
      setShowCreateModal(false);
      setNewTitle("");
      setNewContent("");
      setNewUnitId("");
      setNewPriority("NORMAL");
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

  const handleMarkAllNotificationsRead = async () => {
    try {
      await apiRequest(`/notifications/read-all`, { method: "POST" });
      setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })));
    } catch {
      // Ignored
    }
  };

  const unreadNotifsCount = notifications.filter((n) => !n.isRead).length;

  const filteredAnnouncements = announcements.filter((a) => {
    if (selectedUnitFilter !== "ALL") {
      if (selectedUnitFilter === "COLLEGE" && a.unitId !== null) return false;
      if (selectedUnitFilter !== "COLLEGE" && a.unitId !== selectedUnitFilter) return false;
    }
    if (selectedPriorityFilter !== "ALL") {
      if ((a.priority || "NORMAL") !== selectedPriorityFilter) return false;
    }
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      const matchTitle = a.title.toLowerCase().includes(q);
      const matchContent = a.content.toLowerCase().includes(q);
      const matchAuthor = a.createdByName && a.createdByName.toLowerCase().includes(q);
      const matchUnit = a.unitName && a.unitName.toLowerCase().includes(q);
      if (!matchTitle && !matchContent && !matchAuthor && !matchUnit) return false;
    }
    return true;
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
            <div style={{ display: "flex", alignItems: "center", gap: "1rem" }}>
              <span style={{ fontSize: "0.875rem", color: "#64748b" }}>{unreadNotifsCount} unread</span>
              {unreadNotifsCount > 0 && (
                <button
                  onClick={handleMarkAllNotificationsRead}
                  style={{
                    padding: "0.25rem 0.5rem",
                    backgroundColor: "#f1f5f9",
                    border: "1px solid #cbd5e1",
                    borderRadius: "0.25rem",
                    fontSize: "0.75rem",
                    fontWeight: 600,
                    cursor: "pointer"
                  }}
                >
                  Mark all as read
                </button>
              )}
            </div>
          </div>

          {notifications.length === 0 ? (
            <div style={{ color: "#64748b", fontSize: "0.875rem" }}>No personal notifications.</div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem", maxHeight: "350px", overflowY: "auto" }}>
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
                    <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
                      {n.category && (
                        <span style={{
                          backgroundColor: "#dbeafe",
                          color: "#1e40af",
                          borderRadius: "4px",
                          padding: "0.1rem 0.4rem",
                          fontSize: "0.7rem",
                          fontWeight: 700
                        }}>
                          {n.category}
                        </span>
                      )}
                      <span style={{ fontWeight: 600, fontSize: "0.875rem", color: "#1e293b" }}>{n.title}</span>
                    </div>
                    <div style={{ fontSize: "0.8125rem", color: "#475569", marginTop: "0.25rem" }}>{n.message}</div>
                    <div style={{ display: "flex", gap: "1rem", alignItems: "center", marginTop: "0.35rem" }}>
                      <span style={{ fontSize: "0.75rem", color: "#94a3b8" }}>
                        {new Date(n.createdAt).toLocaleString()}
                      </span>
                      {n.link && (
                        <a
                          href={n.link}
                          style={{
                            fontSize: "0.75rem",
                            color: "var(--primary-color, #1e40af)",
                            fontWeight: 600,
                            textDecoration: "none",
                          }}
                        >
                          View Details &rarr;
                        </a>
                      )}
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
                        cursor: "pointer",
                        whiteSpace: "nowrap"
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
      <div style={{ display: "flex", gap: "1rem", marginBottom: "1.5rem", alignItems: "center", justifyContent: "space-between", flexWrap: "wrap" }}>
        <div style={{ display: "flex", gap: "0.5rem", alignItems: "center", flexWrap: "wrap" }}>
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

        <div style={{ minWidth: "260px", maxWidth: "380px" }}>
          <SearchBar
            value={searchQuery}
            onChange={setSearchQuery}
            placeholder="Search circulars by title, topic..."
          />
        </div>
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
              {searchQuery ? "No announcements match your search query." : "No announcements found for this filter."}
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
                    <div style={{ display: "flex", gap: "0.5rem", alignItems: "center", marginBottom: "0.5rem", flexWrap: "wrap" }}>
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
                      {a.priority && a.priority !== "NORMAL" && (
                        <span
                          style={{
                            backgroundColor: a.priority === "URGENT" ? "#fee2e2" : "#fef3c7",
                            color: a.priority === "URGENT" ? "#991b1b" : "#92400e",
                            padding: "0.2rem 0.5rem",
                            borderRadius: "9999px",
                            fontSize: "0.75rem",
                            fontWeight: 800,
                            letterSpacing: "0.025em"
                          }}
                        >
                          {a.priority}
                        </span>
                      )}
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
        <div className="modal-backdrop">
          <div className="modal-card">
            <div className="modal-header">
              <h2>Post Announcement</h2>
              <button
                type="button"
                className="btn-close"
                onClick={() => setShowCreateModal(false)}
                aria-label="Close modal"
              >
                &times;
              </button>
            </div>
            <div className="modal-body">
              <form onSubmit={handleCreateAnnouncement} className="form-stack">
                <div className="form-group">
                  <label htmlFor="announcementTitle">Announcement Title *</label>
                  <input
                    id="announcementTitle"
                    type="text"
                    value={newTitle}
                    onChange={(e) => setNewTitle(e.target.value)}
                    placeholder="e.g. Schedule Update for Annual Camp"
                    required
                  />
                </div>

                <div className="form-group">
                  <label htmlFor="announcementUnit">Target Audience / Unit</label>
                  <CustomSelect
                    id="announcementUnit"
                    value={newUnitId}
                    onChange={setNewUnitId}
                    options={[
                      { value: "", label: "College-Wide (Broadcast to all students & units)" },
                      ...units.map((u) => ({
                        value: u.unitId,
                        label: `${u.unitName} (${u.unitNumber})`,
                      })),
                    ]}
                    placeholder="College-Wide (Broadcast to all students & units)"
                  />
                </div>

                <div className="form-group">
                  <label htmlFor="announcementPriority">Priority Level *</label>
                  <CustomSelect
                    id="announcementPriority"
                    value={newPriority}
                    onChange={setNewPriority}
                    options={[
                      { value: "NORMAL", label: "Normal Priority" },
                      { value: "HIGH", label: "High Priority" },
                      { value: "URGENT", label: "Urgent Notice" },
                    ]}
                    placeholder="Select priority"
                  />
                </div>

                <div className="form-group">
                  <label htmlFor="announcementContent">Content / Details *</label>
                  <textarea
                    id="announcementContent"
                    value={newContent}
                    onChange={(e) => setNewContent(e.target.value)}
                    rows={4}
                    placeholder="Provide full text of the circular, timing instructions, or venue details..."
                    required
                  />
                </div>

                <div className="form-group">
                  <label htmlFor="announcementExpires">Notice Expiry Date (Optional)</label>
                  <input
                    id="announcementExpires"
                    type="date"
                    value={newExpiresAt}
                    onChange={(e) => setNewExpiresAt(e.target.value)}
                  />
                </div>

                <div className="modal-actions">
                  <button
                    type="button"
                    onClick={() => setShowCreateModal(false)}
                    className="btn-secondary"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={creating}
                    className="btn-primary"
                  >
                    {creating ? "Publishing..." : "Publish & Broadcast"}
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
