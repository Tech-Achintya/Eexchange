import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { usersApi } from '../lib/api';

const AuthContext = createContext(null);

function readStoredUser() {
  try {
    const raw = localStorage.getItem('user');
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function normalizeAuthUser(payload) {
  if (!payload) return null;
  return {
    id: payload.id ?? payload.userId ?? null,
    email: payload.email ?? '',
    name: payload.name ?? '',
    collegeId: payload.collegeId ?? payload.college?.id ?? null,
    collegeName: payload.collegeName ?? payload.college?.name ?? '',
    isCollegeVerified:
      payload.isCollegeVerified ?? payload.collegeVerified ?? false,
    status: payload.status ?? 'ACTIVE',
    createdAt: payload.createdAt ?? null,
    isNewUser: payload.isNewUser ?? false,
  };
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(readStoredUser);
  const [token, setToken] = useState(() => localStorage.getItem('token'));
  const [loading, setLoading] = useState(() => Boolean(localStorage.getItem('token')));

  const persistUser = useCallback((nextUser) => {
    setUser(nextUser);
    if (nextUser) localStorage.setItem('user', JSON.stringify(nextUser));
    else localStorage.removeItem('user');
  }, []);

  const signOut = useCallback(() => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    setToken(null);
    persistUser(null);
  }, [persistUser]);

  const signIn = useCallback(
    (authResponse) => {
      const nextUser = normalizeAuthUser(authResponse);
      if (authResponse?.token) {
        localStorage.setItem('token', authResponse.token);
        setToken(authResponse.token);
      }
      persistUser(nextUser);
      return nextUser;
    },
    [persistUser],
  );

  const updateUser = useCallback(
    (partial) => {
      setUser((previous) => {
        const next = { ...(previous || {}), ...partial };
        localStorage.setItem('user', JSON.stringify(next));
        return next;
      });
    },
    [],
  );

  const updateFromProfile = useCallback(
    (profile) => {
      const normalized = normalizeAuthUser(profile);
      setUser((previous) => {
        const next = { ...(previous || {}), ...normalized };
        localStorage.setItem('user', JSON.stringify(next));
        return next;
      });
    },
    [],
  );

  const refreshToken = useCallback((newToken) => {
    if (!newToken) return;
    localStorage.setItem('token', newToken);
    setToken(newToken);
  }, []);

  // Refresh the cached profile on boot, but never log someone out just because
  // the backend is temporarily unreachable.
  useEffect(() => {
    let cancelled = false;
    if (!localStorage.getItem('token')) {
      setLoading(false);
      return undefined;
    }

    usersApi
      .me()
      .then((response) => {
        if (cancelled) return;
        if (response?.data) updateFromProfile(response.data);
      })
      .catch((error) => {
        if (cancelled) return;
        if (error?.response?.status === 401) signOut();
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [updateFromProfile, signOut]);

  useEffect(() => {
    const handleUnauthorized = () => signOut();
    window.addEventListener('eexchange:unauthorized', handleUnauthorized);
    return () => window.removeEventListener('eexchange:unauthorized', handleUnauthorized);
  }, [signOut]);

  const value = useMemo(
    () => ({
      user,
      token,
      loading,
      isAuthenticated: Boolean(user && token),
      isAdmin: user?.role === 'ROLE_ADMIN',
      signIn,
      signOut,
      updateUser,
      updateFromProfile,
      refreshToken,
    }),
    [user, token, loading, signIn, signOut, updateUser, updateFromProfile, refreshToken],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside <AuthProvider>');
  return context;
}
