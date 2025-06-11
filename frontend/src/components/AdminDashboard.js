// src/components/AdminDashboard.js
import React, { useState, useEffect } from 'react';
import { axiosInstance } from '../services/authService';
import './AdminDashboard.css';

const AdminDashboard = () => {
  const [pendingJobs, setPendingJobs] = useState([]);
  const [stats, setStats] = useState(null);
  const [selectedJob, setSelectedJob] = useState(null);
  const [rejectionReason, setRejectionReason] = useState('');
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('pending');

  useEffect(() => {
    fetchPendingJobs();
    fetchStats();
  }, []);

  const fetchPendingJobs = async () => {
    try {
      const response = await axiosInstance.get('/admin/jobs/pending');
      setPendingJobs(response.data);
      setLoading(false);
    } catch (error) {
      console.error('Error fetching pending jobs:', error);
      setLoading(false);
    }
  };

  const fetchStats = async () => {
    try {
      const response = await axiosInstance.get('/admin/stats');
      setStats(response.data);
    } catch (error) {
      console.error('Error fetching stats:', error);
    }
  };

  const handleApprove = async (jobId) => {
    try {
      await axiosInstance.post(`/admin/jobs/${jobId}/approve`);
      alert('Job approved successfully!');
      fetchPendingJobs();
      fetchStats();
      setSelectedJob(null);
    } catch (error) {
      alert('Error approving job: ' + error.response?.data?.message);
    }
  };

  const handleReject = async (jobId) => {
    if (!rejectionReason.trim()) {
      alert('Please provide a rejection reason');
      return;
    }

    try {
      await axiosInstance.post(`/admin/jobs/${jobId}/reject`, {
        reason: rejectionReason
      });
      alert('Job rejected successfully!');
      fetchPendingJobs();
      fetchStats();
      setSelectedJob(null);
      setRejectionReason('');
    } catch (error) {
      alert('Error rejecting job: ' + error.response?.data?.message);
    }
  };

  return (
    <div className="admin-dashboard">
      <h1>Admin Dashboard</h1>

      {/* Statistics */}
      {stats && (
        <div className="stats-container">
          <div className="stat-card">
            <h3>Total Jobs</h3>
            <p className="stat-number">{stats.totalJobs}</p>
          </div>
          <div className="stat-card">
            <h3>Pending Approval</h3>
            <p className="stat-number pending">{stats.pendingJobs}</p>
          </div>
          <div className="stat-card">
            <h3>Approved Jobs</h3>
            <p className="stat-number approved">{stats.approvedJobs}</p>
          </div>
          <div className="stat-card">
            <h3>Total Users</h3>
            <p className="stat-number">{stats.totalUsers}</p>
          </div>
          <div className="stat-card">
            <h3>Employers</h3>
            <p className="stat-number">{stats.employers}</p>
          </div>
          <div className="stat-card">
            <h3>Job Seekers</h3>
            <p className="stat-number">{stats.jobSeekers}</p>
          </div>
        </div>
      )}

      {/* Tabs */}
      <div className="admin-tabs">
        <button 
          className={activeTab === 'pending' ? 'active' : ''}
          onClick={() => setActiveTab('pending')}
        >
          Pending Jobs ({pendingJobs.length})
        </button>
        <button 
          className={activeTab === 'users' ? 'active' : ''}
          onClick={() => setActiveTab('users')}
        >
          User Management
        </button>
      </div>

      {/* Pending Jobs Tab */}
      {activeTab === 'pending' && (
        <div className="pending-jobs-section">
          <h2>Jobs Pending Approval</h2>
          {loading ? (
            <div className="loading">Loading...</div>
          ) : pendingJobs.length === 0 ? (
            <div className="no-jobs">No jobs pending approval</div>
          ) : (
            <div className="jobs-grid">
              {pendingJobs.map((job) => (
                <div key={job.id} className="job-card">
                  <h3>{job.title}</h3>
                  <p className="company">{job.company}</p>
                  <p className="location">{job.location}</p>
                  <p className="job-type">{job.jobType}</p>
                  <p className="posted-by">Posted by: {job.postedByUsername}</p>
                  <p className="posted-date">
                    {new Date(job.postedDate).toLocaleDateString()}
                  </p>
                  
                  <div className="job-actions">
                    <button
                      onClick={() => setSelectedJob(job)}
                      className="view-btn"
                    >
                      View Details
                    </button>
                    <button
                      onClick={() => handleApprove(job.id)}
                      className="approve-btn"
                    >
                      Approve
                    </button>
                    <button
                      onClick={() => {
                        setSelectedJob(job);
                        setRejectionReason('');
                      }}
                      className="reject-btn"
                    >
                      Reject
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* User Management Tab */}
      {activeTab === 'users' && (
        <div className="users-section">
          <h2>User Management</h2>
          <p>User management features coming soon...</p>
        </div>
      )}

      {/* Job Details Modal */}
      {selectedJob && (
        <div className="modal-overlay" onClick={() => setSelectedJob(null)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <h2>{selectedJob.title}</h2>
            <p><strong>Company:</strong> {selectedJob.company}</p>
            <p><strong>Location:</strong> {selectedJob.location}</p>
            <p><strong>Type:</strong> {selectedJob.jobType}</p>
            <p><strong>Salary:</strong> {selectedJob.salary || 'Not specified'}</p>
            
            <div className="description-section">
              <h3>Description</h3>
              <p>{selectedJob.description}</p>
            </div>
            
            {selectedJob.requirements && (
              <div className="requirements-section">
                <h3>Requirements</h3>
                <p>{selectedJob.requirements}</p>
              </div>
            )}

            {selectedJob.approvalStatus === 'PENDING' && (
              <div className="rejection-section">
                <h3>Rejection Reason (if rejecting)</h3>
                <textarea
                  value={rejectionReason}
                  onChange={(e) => setRejectionReason(e.target.value)}
                  placeholder="Enter reason for rejection..."
                  rows="3"
                />
              </div>
            )}

            <div className="modal-actions">
              <button onClick={() => setSelectedJob(null)} className="cancel-btn">
                Close
              </button>
              {selectedJob.approvalStatus === 'PENDING' && (
                <>
                  <button
                    onClick={() => handleApprove(selectedJob.id)}
                    className="approve-btn"
                  >
                    Approve
                  </button>
                  <button
                    onClick={() => handleReject(selectedJob.id)}
                    className="reject-btn"
                  >
                    Reject
                  </button>
                </>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default AdminDashboard;