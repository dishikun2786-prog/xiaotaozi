import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { useState, useEffect } from "react";
import AdminLayout from "./components/Layout/AdminLayout";
import LoginPage from "./pages/LoginPage";
import DashboardPage from "./pages/DashboardPage";
import ProvidersPage from "./pages/ProvidersPage";
import CardKeysPage from "./pages/CardKeysPage";
import AppUsersPage from "./pages/AppUsersPage";
import AdminUsersPage from "./pages/AdminUsersPage";
import { getAccessToken } from "./lib/api";

function AuthGuard({ children }: { children: React.ReactNode }) {
  const [authed, setAuthed] = useState<boolean | null>(null);

  useEffect(() => {
    const token = getAccessToken();
    if (!token) {
      setAuthed(false);
      return;
    }
    fetch("/api/v1/admin/auth/me", { headers: { Authorization: "Bearer " + token } })
      .then((r) => setAuthed(r.ok))
      .catch(() => setAuthed(false));
  }, []);

  if (authed === null) {
    return (
      <div className="flex h-screen items-center justify-center bg-taozi-bg">
        <div className="text-taozi-textSecondary">Loading...</div>
      </div>
    );
  }

  if (!authed) return <Navigate to="/admin/login" replace />;
  return <AdminLayout>{children}</AdminLayout>;
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/admin/login" element={<LoginPage />} />
        <Route path="/admin" element={<AuthGuard><DashboardPage /></AuthGuard>} />
        <Route path="/admin/dashboard" element={<AuthGuard><DashboardPage /></AuthGuard>} />
        <Route path="/admin/providers" element={<AuthGuard><ProvidersPage /></AuthGuard>} />
        <Route path="/admin/card-keys" element={<AuthGuard><CardKeysPage /></AuthGuard>} />
        <Route path="/admin/users" element={<AuthGuard><AppUsersPage /></AuthGuard>} />
        <Route path="/admin/admins" element={<AuthGuard><AdminUsersPage /></AuthGuard>} />
        <Route path="*" element={<Navigate to="/admin" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
