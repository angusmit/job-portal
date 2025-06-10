import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import axios from 'axios';
import './JobList.css';

const JobList = () => {
  const [jobs, setJobs] = useState([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [locationFilter, setLocationFilter] = useState('');
  const [jobTypeFilter, setJobTypeFilter] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchJobs();
  }, []);

  const fetchJobs = async () => {
    try {
      const response = await axios.get('http://localhost:8080/api/jobs');
      setJobs(response.data);
      setLoading(false);
    } catch (error) {
      console.error('Error fetching jobs:', error);
      setLoading(false);
    }
  };

  const handleSearch = async () => {
    setLoading(true);
    try {
      if (searchTerm) {
        const response = await axios.get(`http://localhost:8080/api/jobs/search?query=${searchTerm}`);
        setJobs(response.data);
      } else {
        fetchJobs();
      }
    } catch (error) {
      console.error('Error searching jobs:', error);
    }
    setLoading(false);
  };

  const handleFilter = async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      if (locationFilter) params.append('location', locationFilter);
      if (jobTypeFilter) params.append('jobType', jobTypeFilter);
      
      const response = await axios.get(`http://localhost:8080/api/jobs/filter?${params}`);
      setJobs(response.data);
    } catch (error) {
      console.error('Error filtering jobs:', error);
    }
    setLoading(false);
  };

  return (
    <div className="job-list-container">
      <div className="search-section">
        <h1>Find Your Dream Job</h1>
        <div className="search-bar">
          <input
            type="text"
            placeholder="Search jobs by title, company, or keywords..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            onKeyPress={(e) => e.key === 'Enter' && handleSearch()}
          />
          <button onClick={handleSearch}>Search</button>
        </div>
        
        <div className="filters">
          <input
            type="text"
            placeholder="Location"
            value={locationFilter}
            onChange={(e) => setLocationFilter(e.target.value)}
          />
          <select
            value={jobTypeFilter}
            onChange={(e) => setJobTypeFilter(e.target.value)}
          >
            <option value="">All Job Types</option>
            <option value="Full-time">Full-time</option>
            <option value="Part-time">Part-time</option>
            <option value="Contract">Contract</option>
            <option value="Internship">Internship</option>
          </select>
          <button onClick={handleFilter}>Apply Filters</button>
        </div>
      </div>

      <div className="jobs-section">
        {loading ? (
          <div className="loading">Loading jobs...</div>
        ) : jobs.length === 0 ? (
          <div className="no-jobs">No jobs found</div>
        ) : (
          <div className="job-cards">
            {jobs.map((job) => (
              <Link to={`/job/${job.id}`} key={job.id} className="job-card-link">
                <div className="job-card">
                  <h3>{job.title}</h3>
                  <p className="company">{job.company}</p>
                  <p className="location">{job.location}</p>
                  <p className="job-type">{job.jobType}</p>
                  {job.salary && <p className="salary">{job.salary}</p>}
                  <p className="posted-date">
                    Posted: {new Date(job.postedDate).toLocaleDateString()}
                  </p>
                </div>
              </Link>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default JobList;