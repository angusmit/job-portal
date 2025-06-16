// frontend/src/components/ScraperManagement.js
import { useEffect, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import './ScraperManagement.css';

const ScraperManagement = () => {
    const { user } = useAuth();
    const [activeTab, setActiveTab] = useState('sources');
    const [sources, setSources] = useState([]);
    const [unimportedJobs, setUnimportedJobs] = useState([]);
    const [stats, setStats] = useState({});
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    const [showAddModal, setShowAddModal] = useState(false);
    const [selectedJobs, setSelectedJobs] = useState([]);

    const [newSource, setNewSource] = useState({
        companyName: '',
        careerPageUrl: '',
        jobListSelector: '',
        jobTitleSelector: '',
        jobLocationSelector: '',
        jobDescriptionSelector: '',
        jobTypeSelector: '',
        jobSalarySelector: '',
        jobUrlSelector: '',
        frequency: 'DAILY'
    });

    useEffect(() => {
        fetchStats();
        if (activeTab === 'sources') {
            fetchSources();
        } else {
            fetchUnimportedJobs();
        }
    }, [activeTab]);

    const fetchStats = async () => {
        try {
            const token = localStorage.getItem('token');
            const response = await fetch('http://localhost:8080/api/scraper/stats', {
                headers: { 'Authorization': `Bearer ${token}` }
            });
            const data = await response.json();
            setStats(data);
        } catch (error) {
            console.error('Error fetching stats:', error);
        }
    };

    const fetchSources = async () => {
        setLoading(true);
        try {
            const token = localStorage.getItem('token');
            const response = await fetch('http://localhost:8080/api/scraper/sources', {
                headers: { 'Authorization': `Bearer ${token}` }
            });
            const data = await response.json();
            setSources(data);
        } catch (error) {
            setError('Failed to fetch sources');
        } finally {
            setLoading(false);
        }
    };

    const fetchUnimportedJobs = async () => {
        setLoading(true);
        try {
            const token = localStorage.getItem('token');
            const response = await fetch('http://localhost:8080/api/scraper/jobs/unimported', {
                headers: { 'Authorization': `Bearer ${token}` }
            });
            const data = await response.json();
            setUnimportedJobs(data);
        } catch (error) {
            setError('Failed to fetch unimported jobs');
        } finally {
            setLoading(false);
        }
    };

    const handleAddSource = async (e) => {
        e.preventDefault();
        try {
            const token = localStorage.getItem('token');
            const response = await fetch('http://localhost:8080/api/scraper/sources', {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${token}`,
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(newSource)
            });

            if (response.ok) {
                setSuccess('Source added successfully');
                setShowAddModal(false);
                fetchSources();
                setNewSource({
                    companyName: '',
                    careerPageUrl: '',
                    jobListSelector: '',
                    jobTitleSelector: '',
                    jobLocationSelector: '',
                    jobDescriptionSelector: '',
                    jobTypeSelector: '',
                    jobSalarySelector: '',
                    jobUrlSelector: '',
                    frequency: 'DAILY'
                });
            } else {
                const error = await response.json();
                setError(error.message || 'Failed to add source');
            }
        } catch (error) {
            setError('Failed to add source');
        }
    };

    const handleScrapeSource = async (sourceId) => {
        try {
            const token = localStorage.getItem('token');
            const response = await fetch(`http://localhost:8080/api/scraper/sources/${sourceId}/scrape`, {
                method: 'POST',
                headers: { 'Authorization': `Bearer ${token}` }
            });

            const result = await response.json();
            if (result.success) {
                setSuccess(`Scraped ${result.jobsScraped} new jobs`);
                fetchStats();
            } else {
                setError(result.error || 'Scraping failed');
            }
        } catch (error) {
            setError('Failed to scrape source');
        }
    };

    const handleToggleSource = async (sourceId, currentStatus) => {
        try {
            const token = localStorage.getItem('token');
            const source = sources.find(s => s.id === sourceId);
            source.active = !currentStatus;

            const response = await fetch(`http://localhost:8080/api/scraper/sources/${sourceId}`, {
                method: 'PUT',
                headers: {
                    'Authorization': `Bearer ${token}`,
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(source)
            });

            if (response.ok) {
                fetchSources();
                setSuccess('Source updated');
            }
        } catch (error) {
            setError('Failed to update source');
        }
    };

    const handleImportJobs = async () => {
        if (selectedJobs.length === 0) {
            setError('Please select jobs to import');
            return;
        }

        try {
            const token = localStorage.getItem('token');
            const response = await fetch('http://localhost:8080/api/scraper/jobs/import', {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${token}`,
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(selectedJobs)
            });

            const result = await response.json();
            setSuccess(`Imported ${result.imported} jobs, ${result.skipped} skipped`);
            setSelectedJobs([]);
            fetchUnimportedJobs();
            fetchStats();
        } catch (error) {
            setError('Failed to import jobs');
        }
    };

    const toggleJobSelection = (jobId) => {
        setSelectedJobs(prev => {
            if (prev.includes(jobId)) {
                return prev.filter(id => id !== jobId);
            }
            return [...prev, jobId];
        });
    };

    const selectAllJobs = () => {
        if (selectedJobs.length === unimportedJobs.length) {
            setSelectedJobs([]);
        } else {
            setSelectedJobs(unimportedJobs.map(job => job.id));
        }
    };

    return (
        <div className="scraper-management">
            <h2>Job Scraper Management</h2>

            {/* Statistics */}
            <div className="scraper-stats">
                <div className="stat-card">
                    <h4>Total Sources</h4>
                    <p>{stats.totalSources || 0}</p>
                </div>
                <div className="stat-card">
                    <h4>Active Sources</h4>
                    <p>{stats.activeSources || 0}</p>
                </div>
                <div className="stat-card">
                    <h4>Scraped Jobs</h4>
                    <p>{stats.totalScrapedJobs || 0}</p>
                </div>
                <div className="stat-card">
                    <h4>Unimported Jobs</h4>
                    <p>{stats.activeScrapedJobs || 0}</p>
                </div>
            </div>

            {/* Tab Navigation */}
            <div className="tab-navigation">
                <button
                    className={activeTab === 'sources' ? 'active' : ''}
                    onClick={() => setActiveTab('sources')}
                >
                    Company Sources
                </button>
                <button
                    className={activeTab === 'jobs' ? 'active' : ''}
                    onClick={() => setActiveTab('jobs')}
                >
                    Unimported Jobs
                </button>
            </div>

            {error && <div className="error-message">{error}</div>}
            {success && <div className="success-message">{success}</div>}

            {/* Sources Tab */}
            {activeTab === 'sources' && (
                <div className="sources-section">
                    <div className="section-header">
                        <h3>Company Sources</h3>
                        <button onClick={() => setShowAddModal(true)} className="add-btn">
                            Add Source
                        </button>
                    </div>

                    {loading ? (
                        <div className="loading">Loading sources...</div>
                    ) : sources.length === 0 ? (
                        <p className="no-data">No sources configured yet</p>
                    ) : (
                        <div className="sources-list">
                            {sources.map(source => (
                                <div key={source.id} className="source-card">
                                    <div className="source-header">
                                        <h4>{source.companyName}</h4>
                                        <span className={`status ${source.active ? 'active' : 'inactive'}`}>
                                            {source.active ? 'Active' : 'Inactive'}
                                        </span>
                                    </div>
                                    <div className="source-details">
                                        <p><strong>URL:</strong> <a href={source.careerPageUrl} target="_blank" rel="noopener noreferrer">{source.careerPageUrl}</a></p>
                                        <p><strong>Frequency:</strong> {source.frequency}</p>
                                        <p><strong>Last Scraped:</strong> {source.lastScrapedAt ? new Date(source.lastScrapedAt).toLocaleString() : 'Never'}</p>
                                        <p><strong>Jobs Found:</strong> {source.totalJobsScraped || 0}</p>
                                        {source.lastError && (
                                            <p className="error-info"><strong>Last Error:</strong> {source.lastError}</p>
                                        )}
                                    </div>
                                    <div className="source-actions">
                                        <button onClick={() => handleScrapeSource(source.id)} className="scrape-btn">
                                            Scrape Now
                                        </button>
                                        <button
                                            onClick={() => handleToggleSource(source.id, source.active)}
                                            className={source.active ? 'deactivate-btn' : 'activate-btn'}
                                        >
                                            {source.active ? 'Deactivate' : 'Activate'}
                                        </button>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            )}

            {/* Unimported Jobs Tab */}
            {activeTab === 'jobs' && (
                <div className="jobs-section">
                    <div className="section-header">
                        <h3>Unimported Jobs ({unimportedJobs.length})</h3>
                        <div className="bulk-actions">
                            <button onClick={selectAllJobs} className="select-all-btn">
                                {selectedJobs.length === unimportedJobs.length ? 'Deselect All' : 'Select All'}
                            </button>
                            <button
                                onClick={handleImportJobs}
                                className="import-btn"
                                disabled={selectedJobs.length === 0}
                            >
                                Import Selected ({selectedJobs.length})
                            </button>
                        </div>
                    </div>

                    {loading ? (
                        <div className="loading">Loading jobs...</div>
                    ) : unimportedJobs.length === 0 ? (
                        <p className="no-data">No unimported jobs available</p>
                    ) : (
                        <div className="scraped-jobs-list">
                            {unimportedJobs.map(job => (
                                <div key={job.id} className="scraped-job-card">
                                    <input
                                        type="checkbox"
                                        checked={selectedJobs.includes(job.id)}
                                        onChange={() => toggleJobSelection(job.id)}
                                    />
                                    <div className="job-content">
                                        <h4>{job.title}</h4>
                                        <p className="company">{job.source.companyName}</p>
                                        <p className="location">📍 {job.location || 'Not specified'}</p>
                                        <p className="scraped-date">
                                            Scraped: {new Date(job.firstSeenAt).toLocaleDateString()}
                                        </p>
                                        {job.externalUrl && (
                                            <a href={job.externalUrl} target="_blank" rel="noopener noreferrer" className="external-link">
                                                View Original →
                                            </a>
                                        )}
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            )}

            {/* Add Source Modal */}
            {showAddModal && (
                <div className="modal-overlay" onClick={() => setShowAddModal(false)}>
                    <div className="modal-content large" onClick={(e) => e.stopPropagation()}>
                        <h3>Add Company Source</h3>
                        <form onSubmit={handleAddSource}>
                            <div className="form-group">
                                <label>Company Name *</label>
                                <input
                                    type="text"
                                    value={newSource.companyName}
                                    onChange={(e) => setNewSource({ ...newSource, companyName: e.target.value })}
                                    required
                                />
                            </div>
                            <div className="form-group">
                                <label>Career Page URL *</label>
                                <input
                                    type="url"
                                    value={newSource.careerPageUrl}
                                    onChange={(e) => setNewSource({ ...newSource, careerPageUrl: e.target.value })}
                                    required
                                />
                            </div>
                            <div className="form-group">
                                <label>Scraping Frequency</label>
                                <select
                                    value={newSource.frequency}
                                    onChange={(e) => setNewSource({ ...newSource, frequency: e.target.value })}
                                >
                                    <option value="HOURLY">Hourly</option>
                                    <option value="DAILY">Daily</option>
                                    <option value="WEEKLY">Weekly</option>
                                    <option value="MONTHLY">Monthly</option>
                                </select>
                            </div>

                            <h4>CSS Selectors</h4>
                            <p className="help-text">Provide CSS selectors to extract job information</p>

                            <div className="form-group">
                                <label>Job List Container *</label>
                                <input
                                    type="text"
                                    value={newSource.jobListSelector}
                                    onChange={(e) => setNewSource({ ...newSource, jobListSelector: e.target.value })}
                                    placeholder=".job-listing, .careers-list li"
                                    required
                                />
                            </div>
                            <div className="form-group">
                                <label>Job Title *</label>
                                <input
                                    type="text"
                                    value={newSource.jobTitleSelector}
                                    onChange={(e) => setNewSource({ ...newSource, jobTitleSelector: e.target.value })}
                                    placeholder=".job-title, h3"
                                    required
                                />
                            </div>
                            <div className="form-group">
                                <label>Job Location</label>
                                <input
                                    type="text"
                                    value={newSource.jobLocationSelector}
                                    onChange={(e) => setNewSource({ ...newSource, jobLocationSelector: e.target.value })}
                                    placeholder=".location, .job-location"
                                />
                            </div>
                            <div className="form-group">
                                <label>Job URL</label>
                                <input
                                    type="text"
                                    value={newSource.jobUrlSelector}
                                    onChange={(e) => setNewSource({ ...newSource, jobUrlSelector: e.target.value })}
                                    placeholder="a, .job-link"
                                />
                            </div>

                            <div className="modal-actions">
                                <button type="button" onClick={() => setShowAddModal(false)}>Cancel</button>
                                <button type="submit" className="primary-btn">Add Source</button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
};

export default ScraperManagement;