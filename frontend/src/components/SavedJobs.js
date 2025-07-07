import axios from 'axios';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import './SavedJobs.css';

const SavedJobs = () => {
    const [savedJobs, setSavedJobs] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        fetchSavedJobs();
    }, []);

    const fetchSavedJobs = async () => {
        try {
            const response = await axios.get('http://localhost:8080/api/jobs/saved', {
                headers: {
                    'Authorization': `Bearer ${localStorage.getItem('token')}`
                }
            });
            setSavedJobs(response.data);
        } catch (err) {
            setError('Failed to load saved jobs');
        } finally {
            setLoading(false);
        }
    };

    const handleUnsave = async (jobId) => {
        try {
            await axios.delete(`http://localhost:8080/api/jobs/${jobId}/save`, {
                headers: {
                    'Authorization': `Bearer ${localStorage.getItem('token')}`
                }
            });
            setSavedJobs(savedJobs.filter(job => job.id !== jobId));
        } catch (err) {
            setError('Failed to unsave job');
        }
    };

    if (loading) return <div className="loading">Loading saved jobs...</div>;
    if (error) return <div className="error">{error}</div>;

    return (
        <div className="saved-jobs-container">
            <h1>My Saved Jobs</h1>

            {savedJobs.length === 0 ? (
                <div className="empty-state">
                    <p>You haven't saved any jobs yet.</p>
                    <Link to="/" className="browse-jobs-btn">Browse Jobs</Link>
                </div>
            ) : (
                <div className="saved-jobs-list">
                    {savedJobs.map(job => (
                        <div key={job.id} className="saved-job-card">
                            <div className="job-info">
                                <h3>{job.title}</h3>
                                <p className="company">{job.company}</p>
                                <p className="location">{job.location} • {job.jobType}</p>
                                <p className="saved-date">
                                    Saved on {new Date(job.savedDate).toLocaleDateString()}
                                </p>
                            </div>
                            <div className="job-actions">
                                <Link to={`/jobs/${job.id}`} className="view-btn">
                                    View Details
                                </Link>
                                <button
                                    onClick={() => handleUnsave(job.id)}
                                    className="unsave-btn"
                                >
                                    Remove
                                </button>
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
};

export default SavedJobs;