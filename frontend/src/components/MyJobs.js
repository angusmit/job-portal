// src/components/MyJobs.js
import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { axiosInstance } from '../services/authService';
import './MyJobs.css';

const MyJobs = () => {
  const [jobs, setJobs] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchMyJobs();
  }, []);

  const fetchMyJobs = async () => {
    try {
      const response = await axiosInstance.get('/jobs/my-jobs');
      setJobs(response.data);
      setLoading(false);
    } catch (error) {
      console.error('Error fetching jobs:', error);
      setLoading(false);
    }
  };

  const getStatusBadge = (status) => {
    switch (status) {
      case 'APPROVED':
        return <span className="status-badge approved">Approved</span>;
      case 'PENDING':
        return <span className="status-badge pending">Pending Approval</span>;
      case 'REJECTED':
        return <span className="status-badge rejected">Rejected</span>;
      default:
        return null;
    }
  };

  const handleDelete = async (jobId) => {
    if (window.confirm('Are you sure you want to delete this job?')) {
      try {
        await axiosInstance.delete(`/jobs/${jobId}`);
        alert('Job deleted successfully');
        fetchMyJobs();
      } catch (error) {
        alert('Error deleting job: ' + error.response?.data?.message);
      }
    }
  };

  if (loading) {
    return <div className="loading">Loading your jobs...</div>;
  }

  return (
    <div className="my-jobs-container">
      <div className="my-jobs-header">
        <h1>My Posted Jobs</h1>
        <Link to="/post-job" className="post-new-btn">
          Post New Job
        </Link>
      </div>

      {jobs.length === 0 ? (
        <div className="no-jobs">
          <p>You haven't posted any jobs yet.</p>
          <Link to="/post-job" className="post-first-job-btn">
            Post Your First Job
          </Link>
        </div>
      ) : (
        <div className="jobs-table">
          <table>
            <thead>
              <tr>
                <th>Job Title</th>
                <th>Location</th>
                <th>Type</th>
                <th>Posted Date</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {jobs.map((job) => (
                <tr key={job.id}>
                  <td>
                    <Link to={`/job/${job.id}`} className="job-title-link">
                      {job.title}
                    </Link>
                  </td>
                  <td>{job.location}</td>
                  <td>{job.jobType}</td>
                  <td>{new Date(job.postedDate).toLocaleDateString()}</td>
                  <td>
                    {getStatusBadge(job.approvalStatus)}
                    {job.rejectionReason && (
                      <div className="rejection-reason">
                        Reason: {job.rejectionReason}
                      </div>
                    )}
                  </td>
                  <td>
                    <div className="actions">
                      {job.approvalStatus === 'APPROVED' && (
                        <Link to={`/job/${job.id}`} className="view-btn">
                          View
                        </Link>
                      )}
                      <Link to={`/edit-job/${job.id}`} className="edit-btn">
                        Edit
                      </Link>
                      <button
                        onClick={() => handleDelete(job.id)}
                        className="delete-btn"
                      >
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div className="info-box">
        <h3>About Job Approval</h3>
        <p>
          All job postings require admin approval before they become visible to job seekers.
          This process typically takes 1-2 business hours during working days.
        </p>
        <ul>
          <li><strong>Pending:</strong> Your job is awaiting admin review</li>
          <li><strong>Approved:</strong> Your job is live and visible to all users</li>
          <li><strong>Rejected:</strong> Your job needs modifications. Check the reason and resubmit</li>
        </ul>
      </div>
    </div>
  );
};

export default MyJobs;