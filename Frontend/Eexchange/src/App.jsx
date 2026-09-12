import React, { useState } from 'react';
import AuthPage from './pages/AuthPage';
import HomePage from './pages/HomePage';
import ProfilePage from './pages/ProfilePage';
import AdminPage from './pages/AdminPage';
import Navbar from './components/Navbar';

export default function App() {
  const [user, setUser] = useState(() => {
    try { return JSON.parse(localStorage.getItem('user')); }
    catch { return null; }
  });
  const [page, setPage] = useState('home');

  const handleLogin = (userData) => {
    setUser(userData);
    setPage('home');
  };

  const handleLogout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    setUser(null);
    setPage('home');
  };

  return (
    <div>
      <Navbar
        user={user}
        currentPage={page}
        onNavigate={setPage}
        onLogout={handleLogout}
      />
      {page === 'home' && <HomePage user={user} />}
      {page === 'profile' && <ProfilePage user={user} onUpdateUser={setUser} />}
      {page === 'admin' && user?.role === 'ROLE_ADMIN' && <AdminPage user={user} />}
      {page === 'login' && <AuthPage onLogin={handleLogin} />}
    </div>
  );
}
