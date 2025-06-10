import React, { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import axios from 'axios';
import './JobDetails.css';

const JobDetails = () => {
  const { id } = useParams();
  const [job, setJob] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchJobDetails();
  }, [id]);

  const fetchJobDetails = async () => {
    try {
      const response = await axios.get(`http://localhost:8080/api/jobs/${id}`);
      setJob(response.data);
      setLoading(false);
    } catch (error) {
      console.error('Error fetching job details:', error);
      setLoading(false);
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
        
        <button className="apply-button">Apply Now</button>
      </div>
    </div>
  );
};

export default JobDetails;