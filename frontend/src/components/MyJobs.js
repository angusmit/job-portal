// frontend/src/components/MyJobs.js
import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import './MyJobs.css';

const MyJobs = () => {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [jobs, setJobs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  useEffect(() => {
    if (!user || user.role !== 'EMPLOYER') {
      navigate('/');
      return;
    }
    fetchMyJobs();
  }, [user, navigate]);

  const fetchMyJobs = async () => {
    try {
      setLoading(true);
      setError('');

      const token = localStorage.getItem('token');
      if (!token) {
        setError('Please login to view your jobs');
        setLoading(false);
        return;
      }

      const response = await fetch('http://localhost:8080/api/jobs/my-jobs', {
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        }
      });

      if (response.status === 401) {
        setError('Session expired. Please login again.');
        navigate('/login');
        return;
      }

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const data = await response.json();
      setJobs(Array.isArray(data) ? data : []);
    } catch (error) {
      console.error('Error fetching jobs:', error);
      setError('Failed to load your jobs. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (jobId) => {
    if (!window.confirm('Are you sure you want to delete this job?')) {
      return;
    }

    try {
      const token = localStorage.getItem('token');
      const response = await fetch(`http://localhost:8080/api/jobs/${jobId}`, {
        method: 'DELETE',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        }
      });

      if (response.ok) {
        setSuccess('Job deleted successfully');
        setJobs(jobs.filter(job => job.id !== jobId));
      } else {
        const error = await response.json();
        setError(error.message || 'Failed to delete job');
      }
    } catch (error) {
      setError('Failed to delete job');
    }
  };

  const getStatusBadge = (status) => {
    const statusClass = status.toLowerCase();
    return (
      <span className={`status-badge ${statusClass}`}>
        {status.replace('_', ' ')}
      </span>
    );
  };

  // Clear messages after 3 seconds
  useEffect(() => {
    if (success || error) {
      const timer = setTimeout(() => {
        setSuccess('');
        setError('');
      }, 3000);
      return () => clearTimeout(timer);
    }
  }, [success, error]);

  if (loading) {
    return <div className="loading">Loading your jobs...</div>;
  }

  return (
    <div className="my-jobs-container">
      <div className="my-jobs-header">
        <h1>My Posted Jobs</h1>
        <Link to="/post-job" className="post-job-btn">
          Post New Job
        </Link>
      </div>

      {error && <div className="error-message">{error}</div>}
      {success && <div className="success-message">{success}</div>}

      {jobs.length === 0 ? (
        <div className="no-jobs">
          <p>You haven't posted any jobs yet.</p>
          <Link to="/post-job" className="cta-button">
            Post Your First Job
          </Link>
        </div>
      ) : (
        <div className="jobs-grid">
          {jobs.map(job => (
            <div key={job.id} className="job-card">
              <div className="job-card-header">
                <h3>{job.title}</h3>
                {getStatusBadge(job.approvalStatus)}
              </div>

              <div className="job-info">
                <p className="company">{job.company}</p>
                <p className="location">📍 {job.location}</p>
                <p className="job-type">💼 {job.jobType}</p>
                {job.salary && <p className="salary">💰 {job.salary}</p>}
                <p className="posted-date">
                  Posted: {new Date(job.postedDate).toLocaleDateString()}
                </p>
              </div>

              {job.approvalStatus === 'REJECTED' && job.rejectionReason && (
                <div className="rejection-info">
                  <strong>Rejection Reason:</strong> {job.rejectionReason}
                </div>
              )}

              {job.approvalStatus === 'APPROVED' && job.approvedDate && (
                <div className="approval-info">
                  <p>Approved: {new Date(job.approvedDate).toLocaleDateString()}</p>
                </div>
              )}

              <div className="job-description">
                <p>{job.description.substring(0, 150)}...</p>
              </div>

              <div className="job-actions">
                <button
                  className="view-btn"
                  onClick={() => navigate(`/jobs/${job.id}`)}
                >
                  View
                </button>
                <button
                  className="edit-btn"
                  onClick={() => navigate(`/edit-job/${job.id}`)}
                >
                  Edit
                </button>
                <button
                  className="delete-btn"
                  onClick={() => handleDelete(job.id)}
                >
                  Delete
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      <div className="info-section">
        <h3>Job Approval Process</h3>
        <p>All job posts require admin approval before they appear publicly on the platform.</p>
        <ul>
          <li><strong>Pending:</strong> Your job is awaiting admin review</li>
          <li><strong>Approved:</strong> Your job is live and visible to job seekers</li>
          <li><strong>Rejected:</strong> Your job needs modifications. Check the rejection reason and edit accordingly</li>
        </ul>
      </div>
    </div>
  );
};

export default MyJobs;