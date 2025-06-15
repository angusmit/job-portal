// frontend/src/components/PostJob.js
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import './PostJob.css';

const PostJob = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const [jobData, setJobData] = useState({
    title: '',
    location: '',
    description: '',
    jobType: 'Full-time',
    salary: '',
    requirements: ''
  });

  useEffect(() => {
    if (!user || user.role !== 'EMPLOYER') {
      navigate('/');
    }
  }, [user, navigate]);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setJobData(prev => ({
      ...prev,
      [name]: value
    }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    setSuccess('');

    try {
      const token = localStorage.getItem('token');
      if (!token) {
        setError('Please login to post a job');
        navigate('/login');
        return;
      }

      const response = await fetch('http://localhost:8080/api/jobs', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify(jobData)
      });

      if (response.status === 401) {
        setError('Session expired. Please login again.');
        navigate('/login');
        return;
      }

      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.message || 'Failed to post job');
      }

      const result = await response.json();
      setSuccess(result.message || 'Job posted successfully! It will be visible after admin approval.');

      // Clear form
      setJobData({
        title: '',
        location: '',
        description: '',
        jobType: 'Full-time',
        salary: '',
        requirements: ''
      });

      // Redirect to my jobs after 2 seconds
      setTimeout(() => {
        navigate('/my-jobs');
      }, 2000);

    } catch (error) {
      console.error('Error posting job:', error);
      setError(error.message || 'Failed to post job. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="post-job-container">
      <h2>Post a New Job</h2>

      {error && <div className="error-message">{error}</div>}
      {success && <div className="success-message">{success}</div>}

      <form onSubmit={handleSubmit} className="post-job-form">
        <div className="form-group">
          <label htmlFor="title">Job Title *</label>
          <input
            type="text"
            id="title"
            name="title"
            value={jobData.title}
            onChange={handleChange}
            required
            placeholder="e.g., Senior Software Engineer"
          />
        </div>

        <div className="form-group">
          <label htmlFor="location">Location *</label>
          <input
            type="text"
            id="location"
            name="location"
            value={jobData.location}
            onChange={handleChange}
            required
            placeholder="e.g., New York, NY or Remote"
          />
        </div>

        <div className="form-group">
          <label htmlFor="jobType">Job Type *</label>
          <select
            id="jobType"
            name="jobType"
            value={jobData.jobType}
            onChange={handleChange}
            required
          >
            <option value="Full-time">Full-time</option>
            <option value="Part-time">Part-time</option>
            <option value="Contract">Contract</option>
            <option value="Internship">Internship</option>
            <option value="Remote">Remote</option>
          </select>
        </div>

        <div className="form-group">
          <label htmlFor="salary">Salary Range</label>
          <input
            type="text"
            id="salary"
            name="salary"
            value={jobData.salary}
            onChange={handleChange}
            placeholder="e.g., $80,000 - $120,000"
          />
        </div>

        <div className="form-group">
          <label htmlFor="description">Job Description *</label>
          <textarea
            id="description"
            name="description"
            value={jobData.description}
            onChange={handleChange}
            required
            rows="6"
            placeholder="Describe the role, responsibilities, and what makes this opportunity unique..."
          />
        </div>

        <div className="form-group">
          <label htmlFor="requirements">Requirements *</label>
          <textarea
            id="requirements"
            name="requirements"
            value={jobData.requirements}
            onChange={handleChange}
            required
            rows="6"
            placeholder="List the required skills, experience, and qualifications..."
          />
        </div>

        <div className="form-actions">
          <button
            type="button"
            onClick={() => navigate('/my-jobs')}
            className="cancel-btn"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={loading}
            className="submit-btn"
          >
            {loading ? 'Posting...' : 'Post Job'}
          </button>
        </div>
      </form>

      <div className="info-note">
        <p><strong>Note:</strong> All job posts require admin approval before they appear publicly on the job board.</p>
      </div>
    </div>
  );
};

export default PostJob;