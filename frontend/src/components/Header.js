import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const Header = () => {
  const navigate = useNavigate();
  const { user, logout, isAuthenticated, isEmployer, isAdmin } = useAuth();

  const handleLogout = () => {
    logout();
    navigate('/');
  };

  return (
    <header className="header">
      <div className="header-content">
        <Link to="/" className="logo">
          JobPortal
        </Link>
        <nav className="nav-links">
          <Link to="/">Browse Jobs</Link>
          
          {isAuthenticated ? (
            <>
              {isAdmin && (
                <Link to="/admin" className="admin-link">
                  Admin Dashboard
                </Link>
              )}
              {isEmployer && (
                <>
                  <Link to="/post-job">Post a Job</Link>
                  <Link to="/my-jobs">My Jobs</Link>
                </>
              )}
              <Link to="/profile">Profile</Link>
              <span className="user-info">Welcome, {user.username}!</span>
              <button onClick={handleLogout} className="logout-btn">
                Logout
              </button>
            </>
          ) : (
            <>
              <Link to="/login">Login</Link>
              <Link to="/register" className="register-btn">
                Register
              </Link>
            </>
          )}
        </nav>
      </div>
    </header>
  );
};

export default Header;