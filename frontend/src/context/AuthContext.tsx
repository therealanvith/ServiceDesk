"use client";

import { ApiError, type AuthResult, type UserProfile, api } from "@/lib/apiClient";
import { usePathname, useRouter } from "next/navigation";
import { type ReactNode, createContext, useCallback, useContext, useEffect, useState } from "react";

const TOKEN_KEY = "servicedesk_jwt";

/** Pages a signed-out visitor may open. */
const PUBLIC_PATHS = ["/login", "/register"];

interface AuthContextType {
  user: UserProfile | null;
  token: string | null;
  loading: boolean;
  login: (username: string, password: string) => Promise<void>;
  loginWithGoogle: (credential: string) => Promise<void>;
  register: (data: {
    username: string;
    fullName: string;
    email?: string;
    password: string;
  }) => Promise<void>;
  logout: () => void;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType>({
  user: null,
  token: null,
  loading: true,
  login: async () => {},
  loginWithGoogle: async () => {},
  register: async () => {},
  logout: () => {},
  refreshUser: async () => {},
});

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserProfile | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const pathname = usePathname();
  const router = useRouter();

  // Every page except sign-in and sign-up needs an account; a temporary password must be
  // replaced first (the API refuses other requests until it is).
  useEffect(() => {
    if (!loading && !user && !PUBLIC_PATHS.includes(pathname)) {
      router.replace("/login");
    } else if (user?.mustChangePassword && pathname !== "/account") {
      router.replace("/account");
    }
  }, [loading, user, pathname, router]);

  const clearSession = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY);
    setUser(null);
    setToken(null);
  }, []);

  const refreshUser = useCallback(async () => {
    try {
      const storedToken = localStorage.getItem(TOKEN_KEY);
      if (!storedToken) {
        clearSession();
        return;
      }
      setToken(storedToken);
      setUser(await api.getCurrentUser());
    } catch (err: unknown) {
      if (err instanceof ApiError && err.status === 401) {
        clearSession();
      }
    } finally {
      setLoading(false);
    }
  }, [clearSession]);

  useEffect(() => {
    refreshUser();
    window.addEventListener("servicedesk:session-expired", clearSession);
    return () => window.removeEventListener("servicedesk:session-expired", clearSession);
  }, [refreshUser, clearSession]);

  const startSession = (res: AuthResult) => {
    localStorage.setItem(TOKEN_KEY, res.token);
    setToken(res.token);
    setUser(res.user);
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        loading,
        login: async (username, password) => startSession(await api.login({ username, password })),
        loginWithGoogle: async (credential) => startSession(await api.loginWithGoogle(credential)),
        register: async (data) => startSession(await api.register(data)),
        logout: clearSession,
        refreshUser,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
