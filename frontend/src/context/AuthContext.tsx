import React, { createContext, useContext, useState, useEffect } from "react";
import { apiRequest } from "../api/client";

export interface User {
  userId: string;
  name: string;
  email: string;
  phone?: string;
  status: string;
  roles: string[];
}

interface AuthContextType {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  isAdmin: boolean;
  isCoordinator: boolean;
  isOfficer: boolean;
  isCoordinatorOrOfficer: boolean;
  isStudentLeader: boolean;
  isVolunteer: boolean;
  roleDisplayName: string;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
  loading: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [token, setToken] = useState<string | null>(localStorage.getItem("nss_token"));
  const [user, setUser] = useState<User | null>(() => {
    try {
      const stored = localStorage.getItem("nss_user");
      return stored ? JSON.parse(stored) : null;
    } catch {
      return null;
    }
  });
  const [loading, setLoading] = useState<boolean>(!token ? false : !user);

  useEffect(() => {
    async function loadUser() {
      if (!token) {
        setLoading(false);
        return;
      }
      try {
        const profile = await apiRequest<User>("/auth/me");
        localStorage.setItem("nss_user", JSON.stringify(profile));
        setUser(profile);
      } catch {
        localStorage.removeItem("nss_token");
        localStorage.removeItem("nss_user");
        setToken(null);
        setUser(null);
      } finally {
        setLoading(false);
      }
    }
    loadUser();
  }, [token]);

  const login = async (email: string, password: string) => {
    const data = await apiRequest<{ accessToken: string; user: User }>("/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    });

    localStorage.setItem("nss_token", data.accessToken);
    localStorage.setItem("nss_user", JSON.stringify(data.user));
    setToken(data.accessToken);
    setUser(data.user);
  };

  const logout = () => {
    localStorage.removeItem("nss_token");
    localStorage.removeItem("nss_user");
    setToken(null);
    setUser(null);
  };

  const roles = (user?.roles || []).map((r) => r.replace(/^ROLE_/, ""));
  const isAdmin = roles.includes("ADMIN");
  const isCoordinator = roles.includes("FACULTY_COORDINATOR");
  const isOfficer = roles.includes("PROGRAMME_OFFICER");
  const isCoordinatorOrOfficer = isAdmin || isCoordinator || isOfficer;
  const isStudentLeader = roles.includes("STUDENT_LEADER");
  const isVolunteer = roles.includes("VOLUNTEER") || (!isAdmin && !isCoordinator && !isOfficer);

  const roleDisplayName = isAdmin
    ? "System Administrator"
    : isCoordinator
    ? "Faculty Coordinator"
    : isOfficer
    ? "Programme Officer"
    : isStudentLeader
    ? "Student Leader"
    : "NSS Volunteer";

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isAuthenticated: !!user,
        isAdmin,
        isCoordinator,
        isOfficer,
        isCoordinatorOrOfficer,
        isStudentLeader,
        isVolunteer,
        roleDisplayName,
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

