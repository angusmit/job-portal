// frontend/src/components/JobMatch.js
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import './JobMatch.css';

const JobMatch = ({ resumeId }) => {
    const { user } = useAuth();
    const [topMatches, setTopMatches] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [showDetails, setShowDetails] = useState({});

    useEffect(() => {
        if (resumeId) {
            fetchTopMatches();
        }
    }, [resumeId]);

    const fetchTopMatches = async () => {
        setLoading(true);
        setError('');

        try {
            const token = localStorage.getItem('token');
            const response = await fetch('http://localhost:8080/api/resume/match/top-jobs?limit=10', {
                headers: {
                    'Authorization': `Bearer ${token}`
                }
            });

            if (!response.ok) {
                const errorData = await response.json();
                throw new Error(errorData.message || 'Failed to fetch matches');
            }

            const data = await response.json();
            setTopMatches(data);
        } catch (error) {
            console.error('Error fetching matches:', error);
            setError(error.message || 'Failed to load job matches');
        } finally {
            setLoading(false);
        }
    };

    const getMatchColor = (score) => {
        if (score >= 80) return 'excellent';
        if (score >= 60) return 'good';
        if (score >= 40) return 'fair';
        return 'low';
    };

    const getMatchText = (score) => {
        if (score >= 80) return 'Excellent Match';
        if (score >= 60) return 'Good Match';
        if (score >= 40) return 'Fair Match';
        return 'Low Match';
    };

    const toggleDetails = (jobId) => {
        setShowDetails(prev => ({
            ...prev,
            [jobId]: !prev[jobId]
        }));
    };

    const handleApplyClick = async (jobId) => {
        // TODO: Implement job application
        alert('Job application feature coming soon!');
    };

    if (!resumeId) {
        return (
            <div className="job-match-container">
                <p className="no-resume">Please upload your resume first to see job matches.</p>
            </div>
        );
    }

    if (loading) {
        return (
            <div className="job-match-container">
                <div className="loading">Finding best job matches...</div>
            </div>
        );
    }

    if (error) {
        return (
            <div className="job-match-container">
                <div className="error-message">{error}</div>
                <button onClick={fetchTopMatches} className="retry-btn">Retry</button>
            </div>
        );
    }

    return (
        <div className="job-match-container">
            <h3>Your Top Job Matches</h3>

            {topMatches.length === 0 ? (
                <p className="no-matches">No matching jobs found. Try updating your resume or check back later.</p>
            ) : (
                <div className="matches-list">
                    {topMatches.map((match, index) => {
                        const job = match.job;
                        const score = Math.round(match.matchScore);
                        const matchClass = getMatchColor(score);
                        const isExpanded = showDetails[job.id];

                        return (
                            <div key={job.id} className={`match-card ${matchClass}`}>
                                <div className="match-header">
                                    <div className="match-rank">#{index + 1}</div>
                                    <div className="match-score-container">
                                        <div className={`match-score ${matchClass}`}>
                                            {score}%
                                        </div>
                                        <span className="match-text">{getMatchText(score)}</span>
                                    </div>
                                </div>

                                <div className="job-info">
                                    <h4>{job.title}</h4>
                                    <p className="company">{job.company}</p>
                                    <div className="job-meta">
                                        <span>📍 {job.location}</span>
                                        <span>💼 {job.jobType}</span>
                                        {job.salary && <span>💰 {job.salary}</span>}
                                    </div>
                                </div>

                                <div className="match-summary">
                                    <div className="score-breakdown">
                                        <div className="score-item">
                                            <span>Skills</span>
                                            <div className="score-bar">
                                                <div
                                                    className="score-fill"
                                                    style={{ width: `${match.skillScore}%` }}
                                                />
                                            </div>
                                            <span>{Math.round(match.skillScore)}%</span>
                                        </div>
                                        <div className="score-item">
                                            <span>Experience</span>
                                            <div className="score-bar">
                                                <div
                                                    className="score-fill"
                                                    style={{ width: `${match.experienceScore}%` }}
                                                />
                                            </div>
                                            <span>{Math.round(match.experienceScore)}%</span>
                                        </div>
                                        <div className="score-item">
                                            <span>Education</span>
                                            <div className="score-bar">
                                                <div
                                                    className="score-fill"
                                                    style={{ width: `${match.educationScore}%` }}
                                                />
                                            </div>
                                            <span>{Math.round(match.educationScore)}%</span>
                                        </div>
                                    </div>
                                </div>

                                <button
                                    className="toggle-details-btn"
                                    onClick={() => toggleDetails(job.id)}
                                >
                                    {isExpanded ? 'Hide Details' : 'Show Details'}
                                </button>

                                {isExpanded && (
                                    <div className="match-details">
                                        <div className="analysis-section">
                                            <h5>Match Analysis</h5>

                                            {match.analysis?.skillMatch && (
                                                <div className="analysis-item">
                                                    <strong>Skills Match:</strong>
                                                    <p>Matched: {match.analysis.skillMatch.matched.join(', ') || 'None'}</p>
                                                    {match.analysis.skillMatch.missing.length > 0 && (
                                                        <p>Missing: {match.analysis.skillMatch.missing.join(', ')}</p>
                                                    )}
                                                </div>
                                            )}

                                            {match.analysis?.experienceMatch && (
                                                <div className="analysis-item">
                                                    <strong>Experience:</strong>
                                                    <p>
                                                        You have {match.analysis.experienceMatch.resumeYears} years,
                                                        job requires {match.analysis.experienceMatch.requiredYears} years
                                                    </p>
                                                </div>
                                            )}

                                            {match.recommendations && match.recommendations.length > 0 && (
                                                <div className="recommendations">
                                                    <strong>Recommendations:</strong>
                                                    <ul>
                                                        {match.recommendations.map((rec, idx) => (
                                                            <li key={idx}>{rec}</li>
                                                        ))}
                                                    </ul>
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                )}

                                <div className="match-actions">
                                    <Link to={`/jobs/${job.id}`} className="view-job-btn">
                                        View Job
                                    </Link>
                                    {match.isGoodMatch && (
                                        <button
                                            className="apply-btn"
                                            onClick={() => handleApplyClick(job.id)}
                                        >
                                            Apply Now
                                        </button>
                                    )}
                                </div>
                            </div>
                        );
                    })}
                </div>
            )}
        </div>
    );
};

export default JobMatch;