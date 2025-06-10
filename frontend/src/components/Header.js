import React from 'react';
import { Link } from 'react-router-dom';

const Header = () => {
  return (
    <header className="header">
      <div className="header-content">
        <Link to="/" className="logo">
          JobPortal MVP
        </Link>
        <nav className="nav-links">
          <Link to="/">Browse Jobs</Link>
          <Link to="/post-job">Post a Job</Link>
        </nav>
      </div>
    </header>
  );
};

export default Header;