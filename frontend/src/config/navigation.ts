/**
 * Role-Based Navigation & Capability Configuration
 * Authoritative UI model establishing five separated role workspaces:
 * 1. VOLUNTEER - Personal participation
 * 2. STUDENT_LEADER - Unit-level assistance
 * 3. PROGRAMME_OFFICER - Unit operations & management
 * 4. FACULTY_COORDINATOR - College-wide institutional operations
 * 5. ADMIN - System governance and administration
 */

export type UserRole =
  | "VOLUNTEER"
  | "STUDENT_LEADER"
  | "PROGRAMME_OFFICER"
  | "FACULTY_COORDINATOR"
  | "ADMIN";

export type NavIconKey =
  | "overview"
  | "people"
  | "units"
  | "events"
  | "camps"
  | "attendance"
  | "hours"
  | "achievements"
  | "certificates"
  | "announcements"
  | "activityReports"
  | "documents"
  | "reports"
  | "admin";

export interface NavItem {
  id: string;
  label: string;
  path: string;
  iconKey: NavIconKey;
  badge?: string;
  capability?: string;
}

export interface NavGroup {
  id: string;
  title: string;
  items: NavItem[];
  isAdminGroup?: boolean;
}

export interface MobileNavItem {
  id: string;
  label: string;
  path: string;
  iconKey: NavIconKey;
}

/**
 * Resolves the primary workspace role based on the role hierarchy:
 * ADMIN > FACULTY_COORDINATOR > PROGRAMME_OFFICER > STUDENT_LEADER > VOLUNTEER
 */
export function resolvePrimaryRole(roles?: string[]): UserRole {
  if (!roles || roles.length === 0) return "VOLUNTEER";
  const cleaned = roles.map((r) => r.replace(/^ROLE_/, ""));
  if (cleaned.includes("ADMIN")) return "ADMIN";
  if (cleaned.includes("FACULTY_COORDINATOR")) return "FACULTY_COORDINATOR";
  if (cleaned.includes("PROGRAMME_OFFICER")) return "PROGRAMME_OFFICER";
  if (cleaned.includes("STUDENT_LEADER")) return "STUDENT_LEADER";
  return "VOLUNTEER";
}

export const ROLE_WORKSPACE_CONFIG: Record<
  UserRole,
  {
    displayName: string;
    workspaceTitle: string;
    overviewLabel: string;
    searchScopePlaceholder: string;
  }
> = {
  VOLUNTEER: {
    displayName: "NSS Volunteer",
    workspaceTitle: "Volunteer Workspace",
    overviewLabel: "My NSS",
    searchScopePlaceholder: "Search events, activities, announcements...",
  },
  STUDENT_LEADER: {
    displayName: "Student Leader",
    workspaceTitle: "Student Leadership Workspace",
    overviewLabel: "Unit Overview",
    searchScopePlaceholder: "Search unit volunteers, activities...",
  },
  PROGRAMME_OFFICER: {
    displayName: "Programme Officer",
    workspaceTitle: "Programme Officer Workspace",
    overviewLabel: "Programme Overview",
    searchScopePlaceholder: "Search volunteers, events, records...",
  },
  FACULTY_COORDINATOR: {
    displayName: "Faculty Coordinator",
    workspaceTitle: "Faculty Coordinator Workspace",
    overviewLabel: "College NSS Overview",
    searchScopePlaceholder: "Search units, volunteers, events, reports...",
  },
  ADMIN: {
    displayName: "System Administrator",
    workspaceTitle: "System Administration Workspace",
    overviewLabel: "Institutional Admin",
    searchScopePlaceholder: "Search users, units, volunteers, system logs...",
  },
};

/**
 * Returns role-specific navigation groups for Sidebar.
 */
export function getNavigationForRole(role: UserRole): NavGroup[] {
  switch (role) {
    case "VOLUNTEER":
      return [
        {
          id: "overview",
          title: "Overview",
          items: [
            { id: "dash", label: "My NSS", path: "/dashboard", iconKey: "overview" },
          ],
        },
        {
          id: "programmes",
          title: "Programmes",
          items: [
            { id: "events", label: "Events & Activities", path: "/events", iconKey: "events" },
            { id: "camps", label: "Special Camps", path: "/camps", iconKey: "camps", badge: "Future" },
          ],
        },
        {
          id: "participation",
          title: "Participation",
          items: [
            { id: "attendance", label: "Attendance Check-In", path: "/attendance", iconKey: "attendance" },
            { id: "hours", label: "My Service Hours", path: "/service-hours", iconKey: "hours" },
          ],
        },
        {
          id: "recognition",
          title: "Recognition",
          items: [
            { id: "achievements", label: "Achievements", path: "/achievements", iconKey: "achievements", badge: "Future" },
            { id: "certificates", label: "Certificates", path: "/certificates", iconKey: "certificates", badge: "Future" },
          ],
        },
        {
          id: "communication",
          title: "Communication",
          items: [
            { id: "announcements", label: "Announcements", path: "/announcements", iconKey: "announcements" },
          ],
        },
      ];

    case "STUDENT_LEADER":
      return [
        {
          id: "overview",
          title: "Overview",
          items: [
            { id: "dash", label: "Unit Overview", path: "/dashboard", iconKey: "overview" },
          ],
        },
        {
          id: "people",
          title: "People",
          items: [
            { id: "volunteers", label: "Unit Volunteers", path: "/volunteers", iconKey: "people" },
          ],
        },
        {
          id: "programmes",
          title: "Programmes",
          items: [
            { id: "events", label: "Events & Activities", path: "/events", iconKey: "events" },
            { id: "camps", label: "Special Camps", path: "/camps", iconKey: "camps", badge: "Future" },
          ],
        },
        {
          id: "participation",
          title: "Participation",
          items: [
            { id: "attendance", label: "Attendance Assistance", path: "/attendance", iconKey: "attendance" },
            { id: "hours", label: "Service Hours", path: "/service-hours", iconKey: "hours" },
          ],
        },
        {
          id: "recognition",
          title: "Recognition",
          items: [
            { id: "achievements", label: "Achievements", path: "/achievements", iconKey: "achievements", badge: "Future" },
            { id: "certificates", label: "Certificates", path: "/certificates", iconKey: "certificates", badge: "Future" },
          ],
        },
        {
          id: "communication",
          title: "Communication",
          items: [
            { id: "announcements", label: "Announcements", path: "/announcements", iconKey: "announcements" },
          ],
        },
      ];

    case "PROGRAMME_OFFICER":
      return [
        {
          id: "overview",
          title: "Overview",
          items: [
            { id: "dash", label: "Programme Overview", path: "/dashboard", iconKey: "overview" },
          ],
        },
        {
          id: "organisation",
          title: "Organisation",
          items: [
            { id: "units", label: "Assigned Unit", path: "/units", iconKey: "units" },
          ],
        },
        {
          id: "people",
          title: "People",
          items: [
            { id: "volunteers", label: "Volunteers", path: "/volunteers", iconKey: "people" },
          ],
        },
        {
          id: "programmes",
          title: "Programmes",
          items: [
            { id: "events", label: "Events & Activities", path: "/events", iconKey: "events" },
            { id: "camps", label: "Special Camps", path: "/camps", iconKey: "camps", badge: "Future" },
          ],
        },
        {
          id: "operations",
          title: "Operations",
          items: [
            { id: "attendance", label: "Attendance Operations", path: "/attendance", iconKey: "attendance" },
            { id: "hours", label: "Service Hours Review", path: "/service-hours", iconKey: "hours" },
          ],
        },
        {
          id: "documents",
          title: "Documents",
          items: [
            { id: "activityReports", label: "Activity Reports", path: "/activity-reports", iconKey: "activityReports", badge: "Future" },
            { id: "documents", label: "Repository", path: "/documents", iconKey: "documents", badge: "Future" },
          ],
        },
        {
          id: "reports",
          title: "Reports & Analytics",
          items: [
            { id: "reports", label: "Reports & Exports", path: "/reports", iconKey: "reports" },
          ],
        },
        {
          id: "communication",
          title: "Communication",
          items: [
            { id: "announcements", label: "Announcements", path: "/announcements", iconKey: "announcements" },
          ],
        },
      ];

    case "FACULTY_COORDINATOR":
      return [
        {
          id: "overview",
          title: "Overview",
          items: [
            { id: "dash", label: "College NSS Overview", path: "/dashboard", iconKey: "overview" },
          ],
        },
        {
          id: "organisation",
          title: "Organisation",
          items: [
            { id: "units", label: "NSS Units", path: "/units", iconKey: "units" },
          ],
        },
        {
          id: "people",
          title: "People",
          items: [
            { id: "volunteers", label: "Volunteers", path: "/volunteers", iconKey: "people" },
          ],
        },
        {
          id: "programmes",
          title: "Programmes",
          items: [
            { id: "events", label: "Events & Activities", path: "/events", iconKey: "events" },
            { id: "camps", label: "Special Camps", path: "/camps", iconKey: "camps", badge: "Future" },
          ],
        },
        {
          id: "operations",
          title: "Operations",
          items: [
            { id: "attendance", label: "Attendance Operations", path: "/attendance", iconKey: "attendance" },
            { id: "hours", label: "Service Hours", path: "/service-hours", iconKey: "hours" },
          ],
        },
        {
          id: "documents",
          title: "Documents",
          items: [
            { id: "activityReports", label: "Activity Reports", path: "/activity-reports", iconKey: "activityReports", badge: "Future" },
            { id: "documents", label: "Repository", path: "/documents", iconKey: "documents", badge: "Future" },
          ],
        },
        {
          id: "reports",
          title: "Reports & Analytics",
          items: [
            { id: "reports", label: "Reports & Analytics", path: "/reports", iconKey: "reports" },
          ],
        },
        {
          id: "communication",
          title: "Communication",
          items: [
            { id: "announcements", label: "Announcements", path: "/announcements", iconKey: "announcements" },
          ],
        },
      ];

    case "ADMIN":
      return [
        {
          id: "overview",
          title: "Overview",
          items: [
            { id: "dash", label: "Institutional Admin", path: "/dashboard", iconKey: "overview" },
          ],
        },
        {
          id: "administration",
          title: "Administration",
          isAdminGroup: true,
          items: [
            { id: "admin", label: "Users & Roles", path: "/admin", iconKey: "admin" },
          ],
        },
        {
          id: "organisation",
          title: "Organisation",
          items: [
            { id: "units", label: "NSS Units", path: "/units", iconKey: "units" },
          ],
        },
        {
          id: "people",
          title: "People",
          items: [
            { id: "volunteers", label: "Volunteers", path: "/volunteers", iconKey: "people" },
          ],
        },
        {
          id: "programmes",
          title: "Programmes",
          items: [
            { id: "events", label: "Events & Activities", path: "/events", iconKey: "events" },
            { id: "camps", label: "Special Camps", path: "/camps", iconKey: "camps", badge: "Future" },
          ],
        },
        {
          id: "reports",
          title: "Reports & Analytics",
          items: [
            { id: "reports", label: "Reports & Analytics", path: "/reports", iconKey: "reports" },
          ],
        },
        {
          id: "communication",
          title: "Communication",
          items: [
            { id: "announcements", label: "Announcements", path: "/announcements", iconKey: "announcements" },
          ],
        },
      ];
  }
}

/**
 * Returns role-specific high-frequency bottom navigation items for mobile devices.
 */
export function getMobileNavigationForRole(role: UserRole): MobileNavItem[] {
  switch (role) {
    case "VOLUNTEER":
      return [
        { id: "home", label: "Home", path: "/dashboard", iconKey: "overview" },
        { id: "events", label: "Events", path: "/events", iconKey: "events" },
        { id: "attendance", label: "Attendance", path: "/attendance", iconKey: "attendance" },
        { id: "hours", label: "Hours", path: "/service-hours", iconKey: "hours" },
      ];

    case "STUDENT_LEADER":
      return [
        { id: "home", label: "Home", path: "/dashboard", iconKey: "overview" },
        { id: "events", label: "Events", path: "/events", iconKey: "events" },
        { id: "volunteers", label: "Volunteers", path: "/volunteers", iconKey: "people" },
        { id: "attendance", label: "Attendance", path: "/attendance", iconKey: "attendance" },
      ];

    case "PROGRAMME_OFFICER":
      return [
        { id: "home", label: "Home", path: "/dashboard", iconKey: "overview" },
        { id: "volunteers", label: "Volunteers", path: "/volunteers", iconKey: "people" },
        { id: "events", label: "Events", path: "/events", iconKey: "events" },
        { id: "attendance", label: "Attendance", path: "/attendance", iconKey: "attendance" },
      ];

    case "FACULTY_COORDINATOR":
      return [
        { id: "home", label: "Home", path: "/dashboard", iconKey: "overview" },
        { id: "units", label: "Units", path: "/units", iconKey: "units" },
        { id: "events", label: "Events", path: "/events", iconKey: "events" },
        { id: "reports", label: "Reports", path: "/reports", iconKey: "reports" },
      ];

    case "ADMIN":
      return [
        { id: "home", label: "Home", path: "/dashboard", iconKey: "overview" },
        { id: "users", label: "Users", path: "/admin", iconKey: "admin" },
        { id: "units", label: "Units", path: "/units", iconKey: "units" },
        { id: "reports", label: "Reports", path: "/reports", iconKey: "reports" },
      ];
  }
}

/**
 * Route access permissions by role.
 */
export const ROUTE_PERMISSIONS: Record<string, UserRole[]> = {
  "/dashboard": ["VOLUNTEER", "STUDENT_LEADER", "PROGRAMME_OFFICER", "FACULTY_COORDINATOR", "ADMIN"],
  "/events": ["VOLUNTEER", "STUDENT_LEADER", "PROGRAMME_OFFICER", "FACULTY_COORDINATOR", "ADMIN"],
  "/camps": ["VOLUNTEER", "STUDENT_LEADER", "PROGRAMME_OFFICER", "FACULTY_COORDINATOR", "ADMIN"],
  "/attendance": ["VOLUNTEER", "STUDENT_LEADER", "PROGRAMME_OFFICER", "FACULTY_COORDINATOR", "ADMIN"],
  "/service-hours": ["VOLUNTEER", "STUDENT_LEADER", "PROGRAMME_OFFICER", "FACULTY_COORDINATOR", "ADMIN"],
  "/achievements": ["VOLUNTEER", "STUDENT_LEADER", "PROGRAMME_OFFICER", "FACULTY_COORDINATOR", "ADMIN"],
  "/certificates": ["VOLUNTEER", "STUDENT_LEADER", "PROGRAMME_OFFICER", "FACULTY_COORDINATOR", "ADMIN"],
  "/announcements": ["VOLUNTEER", "STUDENT_LEADER", "PROGRAMME_OFFICER", "FACULTY_COORDINATOR", "ADMIN"],
  "/volunteers": ["STUDENT_LEADER", "PROGRAMME_OFFICER", "FACULTY_COORDINATOR", "ADMIN"],
  "/units": ["PROGRAMME_OFFICER", "FACULTY_COORDINATOR", "ADMIN"],
  "/reports": ["PROGRAMME_OFFICER", "FACULTY_COORDINATOR", "ADMIN"],
  "/activity-reports": ["PROGRAMME_OFFICER", "FACULTY_COORDINATOR", "ADMIN"],
  "/documents": ["PROGRAMME_OFFICER", "FACULTY_COORDINATOR", "ADMIN"],
  "/admin": ["ADMIN"],
};

export function isRouteAllowedForRole(path: string, role: UserRole): boolean {
  const basePath = "/" + path.replace(/^\//, "").split("/")[0];
  const allowedRoles = ROUTE_PERMISSIONS[basePath];
  if (!allowedRoles) return true; // Default allow if not restricted
  return allowedRoles.includes(role);
}
