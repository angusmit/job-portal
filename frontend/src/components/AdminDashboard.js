// frontend/src/components/AdminDashboard.js
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import './AdminDashboard.css';

const AdminDashboard = () => {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState('jobs');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  // Job management state
  const [pendingJobs, setPendingJobs] = useState([]);
  const [jobStats, setJobStats] = useState({
    total: 0,
    pending: 0,
    approved: 0,
    rejected: 0
  });

  // User management state
  const [users, setUsers] = useState([]);
  const [selectedRole, setSelectedRole] = useState('ALL');
  const [userStats, setUserStats] = useState({
    total: 0,
    jobSeekers: 0,
    employers: 0,
    admins: 0
  });

  // Modal state
  const [showRejectModal, setShowRejectModal] = useState(false);
  const [selectedJob, setSelectedJob] = useState(null);
  const [rejectionReason, setRejectionReason] = useState('');

  const [showPasswordModal, setShowPasswordModal] = useState(false);
  const [selectedUser, setSelectedUser] = useState(null);
  const [newPassword, setNewPassword] = useState('');

  // Check if user is admin
  useEffect(() => {
    if (!user || user.role !== 'ADMIN') {
      navigate('/');
      return;
    }
  }, [user, navigate]);

  useEffect(() => {
    if (activeTab === 'jobs') {
      fetchPendingJobs();
      fetchJobStats();
    } else {
      fetchUsers();
      fetchUserStats();
    }
  }, [activeTab, selectedRole]);

  // Helper function to make authenticated requests
  const makeAuthRequest = async (url, options = {}) => {
    const token = localStorage.getItem('token');
    if (!token) {
      throw new Error('No authentication token found');
    }

    const response = await fetch(`http://localhost:8080${url}`, {
      ...options,
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json',
        ...options.headers
      }
    });

    if (response.status === 401) {
      navigate('/login');
      throw new Error('Session expired');
    }

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      throw new Error(errorData.message || `HTTP error! status: ${response.status}`);
    }

    return response.json();
  };

  // Job Management Functions
  const fetchPendingJobs = async () => {
    setLoading(true);
    try {
      const data = await makeAuthRequest('/api/admin/jobs/pending');
      setPendingJobs(Array.isArray(data) ? data : []);
      setError('');
    } catch (error) {
      console.error('Error fetching pending jobs:', error);
      setError('Failed to fetch pending jobs');
      setPendingJobs([]);
    } finally {
      setLoading(false);
    }
  };

  const fetchJobStats = async () => {
    try {
      const data = await makeAuthRequest('/api/admin/jobs/stats');
      setJobStats(data);
    } catch (error) {
      console.error('Failed to fetch job stats:', error);
    }
  };

  const handleApproveJob = async (jobId) => {
    try {
      await makeAuthRequest(`/api/admin/jobs/${jobId}/approve`, {
        method: 'POST'
      });
      setSuccess('Job approved successfully!');
      setPendingJobs(pendingJobs.filter(job => job.id !== jobId));
      fetchJobStats();
    } catch (error) {
      setError('Failed to approve job: ' + error.message);
    }
  };

  const handleRejectJob = async () => {
    if (!rejectionReason.trim()) {
      setError('Please provide a rejection reason');
      return;
    }

    try {
      await makeAuthRequest(`/api/admin/jobs/${selectedJob.id}/reject`, {
        method: 'POST',
        body: JSON.stringify({ reason: rejectionReason })
      });
      setSuccess('Job rejected');
      setPendingJobs(pendingJobs.filter(job => job.id !== selectedJob.id));
      fetchJobStats();
      setShowRejectModal(false);
      setRejectionReason('');
    } catch (error) {
      setError('Failed to reject job: ' + error.message);
    }
  };

  // User Management Functions
  const fetchUsers = async () => {
    setLoading(true);
    try {
      const endpoint = selectedRole === 'ALL'
        ? '/api/admin/users'
        : `/api/admin/users/role/${selectedRole}`;
      const data = await makeAuthRequest(endpoint);
      setUsers(Array.isArray(data) ? data : []);
      setError('');
    } catch (error) {
      console.error('Error fetching users:', error);
      setError('Failed to fetch users');
      setUsers([]);
    } finally {
      setLoading(false);
    }
  };

  const fetchUserStats = async () => {
    try {
      const data = await makeAuthRequest('/api/admin/users/stats');
      setUserStats(data);
    } catch (error) {
      console.error('Failed to fetch user stats:', error);
    }
  };

  const handleToggleUserStatus = async (userId, currentStatus) => {
    try {
      await makeAuthRequest(`/api/admin/users/${userId}/toggle-status`, {
        method: 'POST'
      });
      setSuccess(`User ${currentStatus ? 'disabled' : 'enabled'} successfully`);
      fetchUsers();
    } catch (error) {
      setError('Failed to update user status: ' + error.message);
    }
  };

  const handleResetPassword = async () => {
    if (!newPassword || newPassword.length < 6) {
      setError('Password must be at least 6 characters');
      return;
    }

    try {
      await makeAuthRequest(`/api/admin/users/${selectedUser.id}/reset-password`, {
        method: 'POST',
        body: JSON.stringify({ newPassword })
      });
      setSuccess('Password reset successfully');
      setShowPasswordModal(false);
      setNewPassword('');
    } catch (error) {
      setError('Failed to reset password: ' + error.message);
    }
  };

  const handleDeleteUser = async (userId) => {
    if (!window.confirm('Are you sure you want to deactivate this user?')) {
      return;
    }

    try {
      await makeAuthRequest(`/api/admin/users/${userId}`, {
        method: 'DELETE'
      });
      setSuccess('User deactivated successfully');
      fetchUsers();
    } catch (error) {
      setError(error.message || 'Failed to delete user');
    }
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

  return (
    <div className="admin-dashboard">
      <h1>Admin Dashboard</h1>

      {/* Tab Navigation */}
      <div className="tab-navigation">
        <button
          className={activeTab === 'jobs' ? 'active' : ''}
          onClick={() => setActiveTab('jobs')}
        >
          Job Approvals
        </button>
        <button
          className={activeTab === 'users' ? 'active' : ''}
          onClick={() => setActiveTab('users')}
        >
          User Management
        </button>
      </div>

      {/* Messages */}
      {error && <div className="error-message">{error}</div>}
      {success && <div className="success-message">{success}</div>}

      {/* Job Approvals Tab */}
      {activeTab === 'jobs' && (
        <div className="jobs-section">
          {/* Statistics */}
          <div className="stats-cards">
            <div className="stat-card">
              <h3>Total Jobs</h3>
              <p>{jobStats.total}</p>
            </div>
            <div className="stat-card pending">
              <h3>Pending</h3>
              <p>{jobStats.pending}</p>
            </div>
            <div className="stat-card approved">
              <h3>Approved</h3>
              <p>{jobStats.approved}</p>
            </div>
            <div className="stat-card rejected">
              <h3>Rejected</h3>
              <p>{jobStats.rejected}</p>
            </div>
          </div>

          {/* Pending Jobs List */}
          <h2>Pending Job Approvals</h2>
          {loading ? (
            <div className="loading">Loading...</div>
          ) : pendingJobs.length === 0 ? (
            <p className="no-data">No pending jobs for approval</p>
          ) : (
            <div className="pending-jobs-list">
              {pendingJobs.map(job => (
                <div key={job.id} className="job-card">
                  <div className="job-header">
                    <h3>{job.title}</h3>
                    <span className="company">{job.company}</span>
                  </div>
                  <div className="job-details">
                    <p><strong>Location:</strong> {job.location}</p>
                    <p><strong>Type:</strong> {job.jobType}</p>
                    <p><strong>Salary:</strong> {job.salary || 'Not specified'}</p>
                    <p><strong>Posted by:</strong> {job.postedBy?.username || 'Unknown'}</p>
                    <p><strong>Posted on:</strong> {new Date(job.postedDate).toLocaleDateString()}</p>
                  </div>
                  <div className="job-description">
                    <h4>Description</h4>
                    <p>{job.description}</p>
                  </div>
                  <div className="job-requirements">
                    <h4>Requirements</h4>
                    <p>{job.requirements}</p>
                  </div>
                  <div className="job-actions">
                    <button
                      className="approve-btn"
                      onClick={() => handleApproveJob(job.id)}
                    >
                      Approve
                    </button>
                    <button
                      className="reject-btn"
                      onClick={() => {
                        setSelectedJob(job);
                        setShowRejectModal(true);
                      }}
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
          {/* Statistics */}
          <div className="stats-cards">
            <div className="stat-card">
              <h3>Total Users</h3>
              <p>{userStats.total}</p>
            </div>
            <div className="stat-card">
              <h3>Job Seekers</h3>
              <p>{userStats.jobSeekers}</p>
            </div>
            <div className="stat-card">
              <h3>Employers</h3>
              <p>{userStats.employers}</p>
            </div>
            <div className="stat-card">
              <h3>Admins</h3>
              <p>{userStats.admins}</p>
            </div>
          </div>

          {/* Role Filter */}
          <div className="filter-section">
            <label>Filter by Role:</label>
            <select
              value={selectedRole}
              onChange={(e) => setSelectedRole(e.target.value)}
            >
              <option value="ALL">All Users</option>
              <option value="JOB_SEEKER">Job Seekers</option>
              <option value="EMPLOYER">Employers</option>
              <option value="ADMIN">Admins</option>
            </select>
          </div>

          {/* Users List */}
          <h2>User Management</h2>
          {loading ? (
            <div className="loading">Loading...</div>
          ) : users.length === 0 ? (
            <p className="no-data">No users found</p>
          ) : (
            <div className="users-table">
              <table>
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>Username</th>
                    <th>Email</th>
                    <th>Name</th>
                    <th>Role</th>
                    <th>Status</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {users.map(user => (
                    <tr key={user.id}>
                      <td>{user.id}</td>
                      <td>{user.username}</td>
                      <td>{user.email}</td>
                      <td>{user.firstName} {user.lastName}</td>
                      <td>
                        <span className={`role-badge ${user.role.toLowerCase()}`}>
                          {user.role.replace('_', ' ')}
                        </span>
                      </td>
                      <td>
                        <span className={`status-badge ${user.active ? 'active' : 'inactive'}`}>
                          {user.active ? 'Active' : 'Inactive'}
                        </span>
                      </td>
                      <td>
                        <div className="action-buttons">
                          <button
                            className="toggle-btn"
                            onClick={() => handleToggleUserStatus(user.id, user.active)}
                            title={user.active ? 'Disable User' : 'Enable User'}
                          >
                            {user.active ? '🚫' : '✅'}
                          </button>
                          <button
                            className="password-btn"
                            onClick={() => {
                              setSelectedUser(user);
                              setShowPasswordModal(true);
                            }}
                            title="Reset Password"
                          >
                            🔑
                          </button>
                          {user.role !== 'ADMIN' && (
                            <button
                              className="delete-btn"
                              onClick={() => handleDeleteUser(user.id)}
                              title="Delete User"
                            >
                              🗑️
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Rejection Modal */}
      {showRejectModal && (
        <div className="modal-overlay" onClick={() => setShowRejectModal(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <h3>Reject Job Post</h3>
            <p>Please provide a reason for rejection:</p>
            <textarea
              value={rejectionReason}
              onChange={(e) => setRejectionReason(e.target.value)}
              placeholder="Enter rejection reason..."
              rows="4"
            />
            <div className="modal-actions">
              <button onClick={() => setShowRejectModal(false)}>Cancel</button>
              <button onClick={handleRejectJob} className="reject-btn">Reject</button>
            </div>
          </div>
        </div>
      )}

      {/* Password Reset Modal */}
      {showPasswordModal && (
        <div className="modal-overlay" onClick={() => setShowPasswordModal(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <h3>Reset Password for {selectedUser?.username}</h3>
            <input
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              placeholder="Enter new password (min 6 characters)"
            />
            <div className="modal-actions">
              <button onClick={() => setShowPasswordModal(false)}>Cancel</button>
              <button onClick={handleResetPassword} className="confirm-btn">Reset</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default AdminDashboard;