import axios from 'axios';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import './JobList.css';

const JobList = () => {
  const [jobs, setJobs] = useState([]);
  const [filteredJobs, setFilteredJobs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [searchQuery, setSearchQuery] = useState('');
  const [filterLocation, setFilterLocation] = useState('');
  const [filterJobType, setFilterJobType] = useState('');

  useEffect(() => {
    fetchJobs();
  }, []);

  const fetchJobs = async () => {
    try {
      setLoading(true);
      setError('');
      const response = await axios.get('http://localhost:8080/api/jobs');
      setJobs(response.data);
      setFilteredJobs(response.data);
    } catch (err) {
      setError('Failed to load jobs');
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = async (e) => {
    e.preventDefault();
    if (!searchQuery.trim()) {
      setFilteredJobs(jobs);
      return;
    }

    try {
      setLoading(true);
      const response = await axios.get('http://localhost:8080/api/jobs/search', {
        params: { query: searchQuery }
      });
      setFilteredJobs(response.data);
    } catch (err) {
      setError('Search failed');
      setFilteredJobs([]);
    } finally {
      setLoading(false);
    }
  };

  const handleFilter = async () => {
    try {
      setLoading(true);
      let url = 'http://localhost:8080/api/jobs/filter?';
      if (filterLocation) url += `location=${encodeURIComponent(filterLocation)}&`;
      if (filterJobType) url += `jobType=${encodeURIComponent(filterJobType)}`;
      const response = await axios.get(url);
      setFilteredJobs(response.data);
    } catch (err) {
      setError('Failed to filter jobs');
      setFilteredJobs([]);
    } finally {
      setLoading(false);
    }
  };

  const clearFilters = () => {
    setSearchQuery('');
    setFilterLocation('');
    setFilterJobType('');
    setFilteredJobs(jobs);
  };

  return (
    <div className="job-list-container">
      <h1>Available Jobs</h1>

      {error && (
        <div className="error-message">
          {error}
          <button onClick={fetchJobs} className="retry-btn">Retry</button>
        </div>
      )}

      {/* Search Bar */}
      <form onSubmit={handleSearch} className="search-form">
        <input
          type="text"
          placeholder="Search by title, company, or keywords..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="search-input"
        />
        <button type="submit" className="search-btn">Search</button>
      </form>

      {/* Filters */}
      <div className="filter-section">
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

      {/* Jobs Grid */}
      <div className="jobs-grid">
        {loading ? (
          <div className="loading">Loading jobs...</div>
        ) : filteredJobs.length === 0 ? (
          <div className="no-results">
            <p>No jobs found.</p>
            {(searchQuery || filterLocation || filterJobType) && (
              <button onClick={clearFilters} className="clear-btn">
                Clear filters and show all jobs
              </button>
            )}
          </div>
        ) : (
          filteredJobs.map(job => (
            <JobCard key={job.id} job={job} />
          ))
        )}
      </div>
    </div>
  );
};

const JobCard = ({ job }) => {
  const [isSaved, setIsSaved] = useState(false);

  useEffect(() => {
    checkSavedStatus();
  }, []);

  const checkSavedStatus = async () => {
    try {
      const token = localStorage.getItem('token');
      if (!token) return;
      const res = await axios.get(`http://localhost:8080/api/jobs/${job.id}/is-saved`, {
        headers: { Authorization: `Bearer ${token}` }
      });
      setIsSaved(res.data.saved);
    } catch {
      setIsSaved(false);
    }
  };

  const handleSave = async (e) => {
    e.preventDefault();
    const token = localStorage.getItem('token');
    if (!token) return;

    try {
      if (isSaved) {
        await axios.delete(`http://localhost:8080/api/jobs/${job.id}/save`, {
          headers: { Authorization: `Bearer ${token}` }
        });
        setIsSaved(false);
      } else {
        await axios.post(`http://localhost:8080/api/jobs/${job.id}/save`, {}, {
          headers: { Authorization: `Bearer ${token}` }
        });
        setIsSaved(true);
      }
    } catch {
      console.error('Failed to toggle saved state');
    }
  };

  return (
    <div className="job-card">
      <h3>{job.title}</h3>
      <p className="company">{job.company}</p>
      <p className="location">📍 {job.location}</p>
      <p className="job-type">💼 {job.jobType}</p>
      {job.salary && <p className="salary">💰 {job.salary}</p>}
      <p className="description">{job.description.substring(0, 150)}...</p>
      <div className="job-footer">
        <span className="posted-date">
          Posted {new Date(job.postedDate).toLocaleDateString()}
        </span>
        <Link to={`/jobs/${job.id}`} className="view-details-btn">
          View Details
        </Link>
        {localStorage.getItem('token') && (
          <button onClick={handleSave} className="save-btn">
            {isSaved ? '❤️' : '🤍'}
          </button>
        )}
      </div>
      {job.approvalStatus && (
        <span className={`status-badge ${job.approvalStatus.toLowerCase()}`}>
          {job.approvalStatus}
        </span>
      )}
    </div>
  );
};

export default JobList;
