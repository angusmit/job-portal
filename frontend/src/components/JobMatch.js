import axios from 'axios';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import './JobMatch.css';

const JobMatch = ({ resumeData }) => {
    const [matches, setMatches] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [matchMode, setMatchMode] = useState('graduate_friendly');

    const fetchMatches = async (mode) => {
        if (!resumeData?.member_id) {
            setError('Please upload a CV first');
            return;
        }

        setLoading(true);
        setError('');

        try {
            const response = await axios.post(
                'http://localhost:8080/api/jobs/match',
                {
                    session_id: resumeData.sessionId,
                    member_id: resumeData.member_id,
                    mode: mode,
                    top_k: 10
                },
                {
                    headers: {
                        'Authorization': `Bearer ${localStorage.getItem('token')}`
                    }
                }
            );

            setMatches(response.data.matches || []);
        } catch (err) {
            setError('Failed to fetch job matches');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        if (resumeData?.member_id) {
            fetchMatches(matchMode);
        }
    }, [resumeData]);

    const handleModeChange = (mode) => {
        setMatchMode(mode);
        fetchMatches(mode);
    };

    if (!resumeData) {
        return (
            <div className="job-match-empty">
                <p>Please upload your CV first to see job matches</p>
            </div>
        );
    }

    return (
        <div className="job-match">
            <h2>AI-Powered Job Matches</h2>

            <div className="match-modes">
                <button
                    className={matchMode === 'strict' ? 'active' : ''}
                    onClick={() => handleModeChange('strict')}
                >
                    Exact Match
                </button>
                <button
                    className={matchMode === 'flexible' ? 'active' : ''}
                    onClick={() => handleModeChange('flexible')}
                >
                    Flexible Match
                </button>
                <button
                    className={matchMode === 'graduate_friendly' ? 'active' : ''}
                    onClick={() => handleModeChange('graduate_friendly')}
                >
                    Graduate Friendly
                </button>
            </div>

            {loading && <div className="loading">Finding best matches...</div>}
            {error && <div className="error-message">{error}</div>}

            <div className="matches-list">
                {matches.map((match, index) => (
                    <div key={match.job_id || index} className="match-card">
                        <div className="match-score">
                            {(match.score * 100).toFixed(0)}%
                        </div>
                        <div className="match-details">
                            <h3>{match.title}</h3>
                            <p className="company">{match.company}</p>
                            <p className="location">{match.location} • {match.seniority_level}</p>
                            <p className="skills">
                                Skills: {match.required_skills?.join(', ')}
                            </p>
                            <Link to={`/jobs/${match.job_id}`} className="view-job-btn">
                                View Details
                            </Link>
                        </div>
                    </div>
                ))}
            </div>

            {matches.length === 0 && !loading && (
                <div className="no-matches">
                    No matches found. Try a different matching mode.
                </div>
            )}
        </div>
    );
};

export default JobMatch;