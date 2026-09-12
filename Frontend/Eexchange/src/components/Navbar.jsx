import React from 'react';
import './Navbar.css';
import logoImage from '../assets/categories/image.png';

export default function Navbar({ user, currentPage, onNavigate, onLogout }) {
  return (
    <nav className="navbar">
      <div className="nav-left">
        <div className="nav-logo" onClick={() => onNavigate('home')}>
          <img src={logoImage} alt="EExchange Logo" className="nav-logo-img" />
          <span className="nav-logo-text">EExchange</span>
        </div>

        <div className="nav-links">
          <button
            className={`nav-link ${currentPage === 'home' ? 'active' : ''}`}
            onClick={() => onNavigate('home')}
          >
            🏠 Home
          </button>
          {user && (
            <button
              className={`nav-link ${currentPage === 'profile' ? 'active' : ''}`}
              onClick={() => onNavigate('profile')}
            >
              👤 Profile
            </button>
          )}
          {user && user.role === 'ROLE_ADMIN' && (
            <button
              className={`nav-link ${currentPage === 'admin' ? 'active' : ''}`}
              onClick={() => onNavigate('admin')}
            >
              🛠️ Admin
            </button>
          )}
        </div>
      </div>

      <div className="nav-right">
        {user ? (
          <>
            <div className="nav-user" onClick={() => onNavigate('profile')}>
              <div className="nav-avatar">{user?.name?.[0]?.toUpperCase() || '?'}</div>
              <span className="nav-username">{user?.name?.split(' ')[0]}</span>
            </div>
            <button className="logout-btn" onClick={onLogout}>
              Logout
            </button>
          </>
        ) : (
          <button
            className={`btn-primary nav-login-btn ${currentPage === 'login' ? 'active' : ''}`}
            onClick={() => onNavigate('login')}
            style={{ width: 'auto', padding: '8px 20px' }}
          >
            Login / Signup
          </button>
        )}
      </div>
    </nav>
  );
}
