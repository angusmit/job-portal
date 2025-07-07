import axios from 'axios';
import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import './JobDetails.css';

const JobDetails = () => {
  const { id } = useParams();
  const [job, setJob] = useState(null);
  const [loading, setLoading] = useState(true);

  const [isSaved, setIsSaved] = useState(false);
  const [saving, setSaving] = useState(false);

  const [hasApplied, setHasApplied] = useState(false);
  const [applying, setApplying] = useState(false);

  const [error, setError] = useState('');
  const [coverLetter, setCoverLetter] = useState(''); // Optional: cover letter state

  useEffect(() => {
    fetchJobDetails();
  }, [id]);

  const fetchJobDetails = async () => {
    try {
      const token = localStorage.getItem('token');
      const jobResponse = await axios.get(`http://localhost:8080/api/jobs/${id}`);
      setJob(jobResponse.data);

      // Check if job is already saved
      const savedResponse = await axios.get(`http://localhost:8080/api/jobs/${id}/is-saved`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      setIsSaved(savedResponse.data.saved);

      // Check if job is already applied
      const appliedResponse = await axios.get(`http://localhost:8080/api/jobs/${id}/is-applied`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      setHasApplied(appliedResponse.data.applied);

      setLoading(false);
    } catch (error) {
      console.error('Error fetching job details:', error);
      setLoading(false);
    }
  };

  const handleSaveJob = async () => {
    setSaving(true);
    try {
      const token = localStorage.getItem('token');
      if (isSaved) {
        await axios.delete(`http://localhost:8080/api/jobs/${id}/save`, {
          headers: { 'Authorization': `Bearer ${token}` }
        });
        setIsSaved(false);
      } else {
        await axios.post(`http://localhost:8080/api/jobs/${id}/save`, {}, {
          headers: { 'Authorization': `Bearer ${token}` }
        });
        setIsSaved(true);
      }
    } catch (err) {
      setError('Failed to save job');
    } finally {
      setSaving(false);
    }
  };

  const handleApply = async () => {
    setApplying(true);
    try {
      const token = localStorage.getItem('token');
      await axios.post(
        `http://localhost:8080/api/jobs/${id}/apply`,
        { coverLetter }, // Optional
        {
          headers: { 'Authorization': `Bearer ${token}` }
        }
      );
      setHasApplied(true);
      alert('Application submitted successfully!');
    } catch (err) {
      setError('Failed to apply for job');
    } finally {
      setApplying(false);
    }
  };

  if (loading) return <div className="loading">Loading job details...</div>;
  if (!job) return <div className="error">Job not found</div>;

  return (
    <div className="job-details-container">
      <Link to="/" className="back-link">← Back to Jobs</Link>

      <div className="job-details">
        <h1>{job.title}</h1>
        <div className="job-meta">
          <span className="company">{job.company}</span>
          <span className="location">{job.location}</span>
          <span className="job-type">{job.jobType}</span>
        </div>

        {job.salary && (
          <div className="salary">
            <strong>Salary:</strong> {job.salary}
          </div>
        )}

        <div className="section">
          <h2>Description</h2>
          <p>{job.description}</p>
        </div>

        {job.requirements && (
          <div className="section">
            <h2>Requirements</h2>
            <p>{job.requirements}</p>
          </div>
        )}

        <div className="posted-date">
          Posted on: {new Date(job.postedDate).toLocaleDateString()}
        </div>

        <div className="job-actions">
          <button
            onClick={handleApply}
            disabled={applying || hasApplied}
            className="apply-btn"
          >
            {applying ? 'Applying...' : (hasApplied ? 'Applied' : 'Apply Now')}
          </button>

          <button
            onClick={handleSaveJob}
            disabled={saving}
            className={`save-btn ${isSaved ? 'saved' : ''}`}
          >
            {saving ? 'Saving...' : (isSaved ? 'Saved' : 'Save Job')}
          </button>
        </div>

        {error && <div className="error-message">{error}</div>}
      </div>
    </div>
  );
};

export default JobDetails;
