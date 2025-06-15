// frontend/src/components/JobList.js
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import './JobList.css';

const JobList = () => {
  const [jobs, setJobs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [filterLocation, setFilterLocation] = useState('');
  const [filterJobType, setFilterJobType] = useState('');

  useEffect(() => {
    fetchJobs();
  }, []);

  const fetchJobs = async () => {
    try {
      setLoading(true);
      setError(null);

      const response = await fetch('http://localhost:8080/api/jobs');

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const data = await response.json();
      console.log('Fetched jobs:', data);

      setJobs(Array.isArray(data) ? data : []);
    } catch (error) {
      console.error('Error fetching jobs:', error);
      setError(`Failed to load jobs: ${error.message}`);
      setJobs([]);
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = async (e) => {
    e.preventDefault();
    if (!searchQuery.trim()) {
      fetchJobs();
      return;
    }

    try {
      setLoading(true);
      setError(null);
      const response = await fetch(`http://localhost:8080/api/jobs/search?query=${encodeURIComponent(searchQuery)}`);
      const data = await response.json();
      setJobs(Array.isArray(data) ? data : []);
    } catch (error) {
      console.error('Error searching jobs:', error);
      setError('Failed to search jobs. Please try again.');
      setJobs([]);
    } finally {
      setLoading(false);
    }
  };

  const handleFilter = async () => {
    try {
      setLoading(true);
      setError(null);
      let url = 'http://localhost:8080/api/jobs/filter?';
      if (filterLocation) url += `location=${encodeURIComponent(filterLocation)}&`;
      if (filterJobType) url += `jobType=${encodeURIComponent(filterJobType)}`;

      const response = await fetch(url);
      const data = await response.json();
      setJobs(Array.isArray(data) ? data : []);
    } catch (error) {
      console.error('Error filtering jobs:', error);
      setError('Failed to filter jobs. Please try again.');
      setJobs([]);
    } finally {
      setLoading(false);
    }
  };

  const clearFilters = () => {
    setSearchQuery('');
    setFilterLocation('');
    setFilterJobType('');
    fetchJobs();
  };

  if (loading) {
    return (
      <div className="job-list-container">
        <div className="loading">Loading jobs...</div>
      </div>
    );
  }

  return (
    <div className="job-list-container">
      <h1>Available Jobs</h1>

      {error && (
        <div className="error-message">
          {error}
          <button onClick={fetchJobs} className="retry-btn">Retry</button>
        </div>
      )}

      {/* Search Section */}
      <div className="search-section">
        <form onSubmit={handleSearch} className="search-form">
          <input
            type="text"
            placeholder="Search jobs by title, company, or keywords..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="search-input"
          />
          <button type="submit" className="search-btn">Search</button>
        </form>
      </div>

      {/* Filter Section */}
      <div className="filter-section">
        <div className="filter-group">
          <input
            type="text"
            placeholder="Location"
            value={filterLocation}
            onChange={(e) => setFilterLocation(e.target.value)}
            className="filter-input"
          />
          <select
            value={filterJobType}
            onChange={(e) => setFilterJobType(e.target.value)}
            className="filter-select"
          >
            <option value="">All Job Types</option>
            <option value="Full-time">Full-time</option>
            <option value="Part-time">Part-time</option>
            <option value="Contract">Contract</option>
            <option value="Internship">Internship</option>
            <option value="Remote">Remote</option>
          </select>
          <button onClick={handleFilter} className="filter-btn">Apply Filters</button>
          <button onClick={clearFilters} className="clear-btn">Clear</button>
        </div>
      </div>

      {/* Jobs Grid */}
      <div className="jobs-grid">
        {jobs.length === 0 ? (
          <div className="no-jobs">
            <p>No jobs found.</p>
            {(searchQuery || filterLocation || filterJobType) && (
              <button onClick={clearFilters} className="clear-btn">
                Clear filters and show all jobs
              </button>
            )}
          </div>
        ) : (
          jobs.map(job => (
            <div key={job.id} className="job-card">
              <h3>{job.title}</h3>
              <p className="company">{job.company}</p>
              <p className="location">📍 {job.location}</p>
              <p className="job-type">💼 {job.jobType}</p>
              {job.salary && <p className="salary">💰 {job.salary}</p>}
              <p className="description">
                {job.description.substring(0, 150)}...
              </p>
              <div className="job-footer">
                <span className="posted-date">
                  Posted {new Date(job.postedDate).toLocaleDateString()}
                </span>
                <Link to={`/jobs/${job.id}`} className="view-details-btn">
                  View Details
                </Link>
              </div>
              {job.approvalStatus && (
                <span className={`status-badge ${job.approvalStatus.toLowerCase()}`}>
                  {job.approvalStatus}
                </span>
              )}
            </div>
          ))
        )}
      </div>
    </div>
  );
};

export default JobList;