# Frontend Architecture & UI/UX Design System Specification

## 1. Technology Stack & Client Architecture

The client application is built as a Single Page Application (SPA) prioritizing performance, sub-100ms UI responsiveness, accessibility (WCAG 2.1 AA), and role-tailored usability across mobile and desktop devices.

- **Core Framework**: React 18 with TypeScript 5
- **Build & Development Tooling**: Vite with Hot Module Replacement (HMR)
- **Routing Engine**: React Router 6 with nested route layouts, capability-based route guards, and URL state synchronizers
- **Styling Architecture**: Vanilla CSS and Tailwind CSS design tokens with custom CSS variables for dynamic theming
- **HTTP Client**: Centralized Fetch client with automatic Authorization Bearer headers, centralized error interceptors, and 401 token expiry handling
- **Iconography**: Lucide React for consistent, high-contrast, scalable vector icons

---

## 2. Design System Foundations

### 2.1 Typography
- **Primary Typeface**: `Inter`, sans-serif (weights: 400 Regular, 500 Medium, 600 Semi-bold, 700 Bold). Used for all interface headings, navigation, form inputs, and body text.
- **Monospace Typeface**: `Roboto Mono` or `ui-monospace`. Reserved strictly for cryptographic QR session tokens, enrollment numbers, transaction IDs, and certificate verification hashes.
- **Hierarchy Scale**:
  - `Display / Page Title`: 28px - 32px (Bold, tracking -0.02em)
  - `Section Heading / H2`: 20px - 24px (Semi-bold, tracking -0.01em)
  - `Card Header / H3`: 16px - 18px (Semi-bold)
  - `Body Regular`: 14px (Regular, line-height 1.5)
  - `Body Small / Meta`: 12px - 13px (Regular, text-slate-500)
  - `Micro / Badge`: 11px (Medium, uppercase, tracking +0.05em)

### 2.2 Color Token Palette
The color system honors the institutional identity of the National Service Scheme while delivering modern contrast ratios:

- **Primary Brand (NSS Navy & Royal Blue)**:
  - `--color-primary-900`: `#1e3a8a` (Deep institutional navy)
  - `--color-primary-700`: `#1d4ed8` (Active brand primary)
  - `--color-primary-600`: `#2563eb` (Interactive buttons and focus rings)
  - `--color-primary-50`: `#eff6ff` (Subtle selection highlights)
- **Secondary Accent (Saffron Gold)**:
  - `--color-accent-600`: `#d97706` (Saffron dark)
  - `--color-accent-500`: `#f59e0b` (Saffron gold badges, milestone indicators)
  - `--color-accent-50`: `#fffbeb` (Golden warning / highlight surfaces)
- **Neutral Grayscale (Slate)**:
  - `--color-slate-900`: `#0f172a` (High-contrast headings and text)
  - `--color-slate-800`: `#1e293b` (Sidebar and dark backgrounds)
  - `--color-slate-600`: `#475569` (Body copy and secondary text)
  - `--color-slate-400`: `#94a3b8` (Muted labels and borders)
  - `--color-slate-200`: `#e2e8f0` (Card borders and dividers)
  - `--color-slate-50`: `#f8fafc` (Application canvas background)
  - `--color-white`: `#ffffff` (Card and modal surfaces)
- **Semantic Status Signals**:
  - **Success / Approved**: `#10b981` (Emerald 500), surface `#ecfdf5`
  - **Danger / Rejected**: `#ef4444` (Rose 500), surface `#fef2f2`
  - **Warning / Pending**: `#f59e0b` (Amber 500), surface `#fffbeb`
  - **Info / Draft**: `#0ea5e9` (Sky 500), surface `#f0f9ff`

### 2.3 Spacing & 8-Point Grid
- Spacing tokens strictly follow an 8-point base scale:
  - `2xs`: 4px | `xs`: 8px | `sm`: 12px | `md`: 16px | `lg`: 24px | `xl`: 32px | `2xl`: 48px | `3xl`: 64px
- Component border radii:
  - Small elements (buttons, badges): `6px` (`rounded-md`)
  - Cards, panels, tables: `12px` (`rounded-xl`)
  - Modals and popovers: `16px` (`rounded-2xl`)

### 2.4 Responsive Breakpoints
- **Mobile (`< 640px`)**: Single column layout, bottom navigation or collapsible drawer, full-width modal sheets, touch targets >= 44x44px.
- **Tablet (`640px - 1024px`)**: Collapsed icon-only sidebar, dual-column KPI grid, horizontally scrollable data tables.
- **Desktop (`1024px - 1440px`)**: Persistent multi-item sidebar, multi-column dashboard layouts, split-screen attendance workflows.
- **Wide (`> 1440px`)**: Centered max-width canvas (`1400px`) preventing visual stretch.

---

## 3. Component Architecture & Design Patterns

### 3.1 Application Shell (`AppLayout.tsx`)
- **Top Header Bar**: Sticky header (`h-16`) containing mobile menu trigger, institutional emblem, active academic year pill (`2025-2026`), in-app notification center bell with unread badge counter, and authenticated user profile menu.
- **Dynamic Sidebar Navigation**:
  - Dynamically filters navigation links based on user roles and atomic capabilities:
    - *Dashboard* (`/dashboard`): Accessible to all roles.
    - *Volunteers* (`/volunteers`): Requires `VOLUNTEERS_VIEW`.
    - *NSS Units* (`/units`): Requires `UNITS_VIEW`.
    - *Events* (`/events`): Requires `EVENTS_VIEW`.
    - *Attendance* (`/attendance`): Requires `ATTENDANCE_VIEW` or `ATTENDANCE_CHECKIN`.
    - *Service Hours* (`/service-hours`): Requires `SERVICE_HOURS_VIEW` or `SERVICE_HOURS_LOG`.
    - *Announcements* (`/announcements`): Requires `ANNOUNCEMENTS_VIEW`.
    - *Reports* (`/reports`): Requires `REPORTS_VIEW`.
  - Active links highlighted with `--color-primary-600` background and distinct visual indicator.

### 3.2 Filterable Data Tables & Server Pagination
- Implemented across Volunteers, Events, Attendance List, and Service Hours.
- Search input with 300ms debounce.
- Dropdown filters for Unit, Status, Category, Department, and Academic Year.
- Status badges with both high-contrast text and semantic icons (WCAG non-color-only requirement).
- Row click opens contextual side-drawer or detail view without losing current filter/pagination state.

### 3.3 Dynamic QR Presentation & Verification Terminal
- **Presenter Mode (Supervisors)**:
  - Full-screen or modal presentation window displaying live-rotating QR code.
  - Circular SVG countdown ring showing remaining seconds before HMAC rotation.
  - Large legible fallback session code (e.g. `ATT-2026-9081`) for manual entry.
  - Live participant counter incrementing in real-time as volunteers scan.
- **Scanner Mode (Volunteers)**:
  - HTML5 video camera stream with scanning viewport viewfinder.
  - Audio/vaptic feedback on successful QR capture.
  - Immediate submission to `/api/v1/attendance/check-in` with immediate feedback (Verified or Error message).

### 3.4 Service Hour Ledger & Adjudication Queue
- **Volunteer View**:
  - 120-hour circular or linear milestone progress bar with regular vs. special camp breakdown.
  - Filterable transaction history showing claim date, event title, claimed hours, approved hours, status badge, and reviewer remarks.
- **Supervisor Queue**:
  - Table of pending hour claims with bulk selection checkboxes.
  - One-click "Approve All Selected" or inline approval/rejection with required remarks modal.

### 3.5 Toast Notification System & Inline Error Boundaries
- Global toast container displaying auto-dismissing notifications (`duration: 4000ms`).
- Categorized by type: Success (green), Error (red), Warning (amber), Info (blue).
- Inline form errors render directly beneath the invalid field with `role="alert"` and red border focus rings.

---

## 4. State Management & Authentication Flow

### 4.1 Authentication Context (`AuthContext.tsx`)
```typescript
interface AuthContextType {
  user: UserDto | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (credentials: LoginRequest) => Promise<void>;
  logout: () => void;
  can: (permission: string) => boolean;
  hasRole: (role: string) => boolean;
}
```
- Token stored securely in `localStorage` and sent via `Authorization: Bearer <token>` header.
- Upon 401 Unauthorized responses from protected endpoints, the client triggers automatic logout, clears cached user state, and redirects to `/login?session_expired=true`.

### 4.2 URL State Synchronization
- Filters, search terms, and active page numbers are synchronized with URL query parameters (`useSearchParams`):
  - Example: `/volunteers?search=kumar&department=CSE&unit=unit-01&page=2`
  - Guarantees browser refresh and deep-linking preserve the user's exact query state.

---

## 5. Accessibility (WCAG 2.1 AA Compliance)

1. **Color Contrast**: All text elements maintain a minimum contrast ratio of 4.5:1 against their background (3:1 for large display text and UI components).
2. **Keyboard Traversal**: All interactive controls (buttons, links, inputs, dropdowns) are reachable via standard `Tab` navigation with visible `focus:ring-2 focus:ring-primary-500 focus:outline-none`.
3. **Screen Reader Semantics**:
   - Proper HTML5 landmark tags: `<header>`, `<nav>`, `<main>`, `<aside>`, `<footer>`.
   - Modals trap focus and specify `role="dialog"`, `aria-modal="true"`, and `aria-labelledby`.
   - Non-color-only status cues: Status badges combine color with explicit text labels and icon glyphs.

