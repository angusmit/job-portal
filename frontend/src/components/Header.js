// frontend/src/components/Header.js
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import './Header.css';

const Header = () => {
  const { user, logout, isAuthenticated } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/');
  };

  return (
    <header className="header">
      <div className="header-container">
        <Link to="/" className="logo">
          JobPortal
        </Link>

        <nav className="nav-menu">
          <Link to="/" className="nav-link">
            Browse Jobs
          </Link>

          {isAuthenticated ? (
            <>
              {user.role === 'EMPLOYER' && (
                <>
                  <Link to="/post-job" className="nav-link">
                    Post Job
                  </Link>
                  <Link to="/my-jobs" className="nav-link">
                    My Jobs
                  </Link>
                </>
              )}

              {user.role === 'JOB_SEEKER' && (
                <>
                  <Link to="/cv-screening" className="nav-link">
                    Smart Job Matching
                  </Link>
                  <Link to="/saved-jobs" className="nav-link">
                    Saved Jobs
                  </Link>
                  <Link to="/job-matches" className="nav-link">
                    AI Job Matching
                  </Link>
                  <Link to="/cv-upload" className="nav-link">
                    CV Upload
                  </Link>
                </>
              )}

              {user.role === 'ADMIN' && (
                <Link to="/admin" className="nav-link admin-link">
                  Admin Dashboard
                </Link>
              )}

              <div className="user-info">
                <span className="welcome-text">
                  Welcome, {user.firstName || user.username}!
                </span>
                <span className="user-role">
                  ({user.role.replace('_', ' ')})
                </span>
                <button onClick={handleLogout} className="logout-btn">
                  Logout
                </button>
              </div>
            </>
          ) : (
            <div className="auth-links">
              <Link to="/login" className="nav-link">
                Login
              </Link>
              <Link to="/register" className="nav-link register-btn">
                Register
              </Link>
            </div>
          )}
        </nav>
      </div>
    </header>
  );
};

export default Header;