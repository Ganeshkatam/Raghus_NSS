import React, { createContext, useContext, useState, useEffect } from "react";
import { apiRequest } from "../api/client";
import { UserRole, resolvePrimaryRole, ROLE_WORKSPACE_CONFIG } from "../config/navigation";

export interface User {
  userId: string;
  name: string;
  email: string;
  phone?: string;
  status: string;
  roles: string[];
  permissions?: string[];
}

interface AuthContextType {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  primaryRole: UserRole;
  workspaceConfig: typeof ROLE_WORKSPACE_CONFIG[UserRole];
  isAdmin: boolean;
  isCoordinator: boolean;
  isOfficer: boolean;
  isCoordinatorOrOfficer: boolean;
  isStudentLeader: boolean;
  isVolunteer: boolean;
  roleDisplayName: string;
  permissions: string[];
  hasCapability: (permission: string) => boolean;
  can: (permission: string) => boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
  loading: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem("nss_token"));
  const [user, setUser] = useState<User | null>(() => {
    try {
      const storedToken = localStorage.getItem("nss_token");
      if (!storedToken) return null; // Prevent ghost user if token is absent
      const stored = localStorage.getItem("nss_user");
      return stored ? JSON.parse(stored) : null;
    } catch {
      return null;
    }
  });
  const [loading, setLoading] = useState<boolean>(!token ? false : !user);

  useEffect(() => {
    const handleAuthExpired = () => {
      localStorage.removeItem("nss_token");
      localStorage.removeItem("nss_refresh_token");
      localStorage.removeItem("nss_user");
      setToken(null);
      setUser(null);
    };

    window.addEventListener("nss:auth:expired", handleAuthExpired);
    return () => window.removeEventListener("nss:auth:expired", handleAuthExpired);
  }, []);

  useEffect(() => {
    async function loadUser() {
      if (!token) {
        setLoading(false);
        return;
      }
      try {
        const profile = await apiRequest<User>("/auth/me", { retries: 2 });
        localStorage.setItem("nss_user", JSON.stringify(profile));
        setUser(profile);
      } catch (err: unknown) {
        const apiErr = err as { status?: number; category?: string };
        if (apiErr?.status === 401 || apiErr?.category === "AUTH_EXPIRED" || apiErr?.category === "AUTH_INVALID") {
          localStorage.removeItem("nss_token");
          localStorage.removeItem("nss_refresh_token");
          localStorage.removeItem("nss_user");
          setToken(null);
          setUser(null);
        }
      } finally {
        setLoading(false);
      }
    }
    loadUser();
  }, [token]);

  const login = async (email: string, password: string) => {
    const data = await apiRequest<{ accessToken: string; refreshToken?: string; user: User }>("/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
      retries: 2,
    });

    localStorage.setItem("nss_token", data.accessToken);
    if (data.refreshToken) {
      localStorage.setItem("nss_refresh_token", data.refreshToken);
    }
    localStorage.setItem("nss_user", JSON.stringify(data.user));
    setToken(data.accessToken);
    setUser(data.user);
  };

  const logout = () => {
    if (token) {
      apiRequest("/auth/logout", { method: "POST" }).catch(() => {});
    }
    localStorage.removeItem("nss_token");
    localStorage.removeItem("nss_refresh_token");
    localStorage.removeItem("nss_user");
    setToken(null);
    setUser(null);
  };

  const roles = (user?.roles || []).map((r: string) => r.replace(/^ROLE_/, ""));
  const primaryRole: UserRole = resolvePrimaryRole(user?.roles);
  const workspaceConfig = ROLE_WORKSPACE_CONFIG[primaryRole];

  const isAdmin = roles.includes("ADMIN");
  const isCoordinator = roles.includes("FACULTY_COORDINATOR");
  const isOfficer = roles.includes("PROGRAMME_OFFICER");
  const isCoordinatorOrOfficer = isAdmin || isCoordinator || isOfficer;
  const isStudentLeader = roles.includes("STUDENT_LEADER");
  const isVolunteer = primaryRole === "VOLUNTEER";

  const roleDisplayName = workspaceConfig.displayName;

  const permissions = user?.permissions || [];
  const hasCapability = (permission: string): boolean => {
    if (isAdmin) return true;
    return permissions.includes(permission);
  };
  const can = hasCapability;

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isAuthenticated: Boolean(token && user),
        primaryRole,
        workspaceConfig,
        isAdmin,
        isCoordinator,
        isOfficer,
        isCoordinatorOrOfficer,
        isStudentLeader,
        isVolunteer,
        roleDisplayName,
        permissions,
        hasCapability,
        can,
        login,
        logout,
        loading,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
};

