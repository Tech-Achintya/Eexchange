import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';

const AdminContext = createContext(null);

function readStoredAdmin() {
  try {
    const raw = localStorage.getItem('admin');
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function AdminProvider({ children }) {
  const [admin, setAdmin] = useState(readStoredAdmin);
  const [adminToken, setAdminToken] = useState(() => localStorage.getItem('admin_token'));

  const signIn = useCallback((payload) => {
    if (!payload?.token) return null;
    const nextAdmin = {
      id: payload.adminId ?? null,
      username: payload.username ?? 'Admin',
      role: payload.role ?? 'MODERATOR',
      token: payload.token,
    };
    localStorage.setItem('admin_token', payload.token);
    localStorage.setItem('admin', JSON.stringify(nextAdmin));
    setAdminToken(payload.token);
    setAdmin(nextAdmin);
    return nextAdmin;
  }, []);

  const signOut = useCallback(() => {
    localStorage.removeItem('admin_token');
    localStorage.removeItem('admin');
    setAdminToken(null);
    setAdmin(null);
  }, []);

  useEffect(() => {
    const handleUnauthorized = () => signOut();
    window.addEventListener('eexchange:admin-unauthorized', handleUnauthorized);
    return () =>
      window.removeEventListener('eexchange:admin-unauthorized', handleUnauthorized);
  }, [signOut]);

  const value = useMemo(
    () => ({
      admin,
      adminToken,
      isAdminAuthenticated: Boolean(admin && adminToken),
      isSuperAdmin: admin?.role === 'SUPER_ADMIN',
      signIn,
      signOut,
    }),
    [admin, adminToken, signIn, signOut],
  );

  return <AdminContext.Provider value={value}>{children}</AdminContext.Provider>;
}

export function useAdmin() {
  const context = useContext(AdminContext);
  if (!context) throw new Error('useAdmin must be used inside <AdminProvider>');
  return context;
}
