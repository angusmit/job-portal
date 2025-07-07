// frontend/src/components/JobMatchingDashboard.js
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import './JobMatchingDashboard.css';

const JobMatchingDashboard = () => {
  const navigate = useNavigate();
  const [file, setFile] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [matching, setMatching] = useState(false);
  const [cvData, setCvData] = useState(null);
  const [matches, setMatches] = useState([]);
  const [error, setError] = useState('');
  const [sessionId, setSessionId] = useState(`session-${Date.now()}`);
  const [matchMode, setMatchMode] = useState('graduate_friendly');

  // Check for CV data from cv-screening page
  useEffect(() => {
    const storedCvData = sessionStorage.getItem('cvData');
    const storedSessionId = sessionStorage.getItem('sessionId');

    if (storedCvData && storedSessionId) {
      const parsedCvData = JSON.parse(storedCvData);
      setCvData(parsedCvData);
      setSessionId(storedSessionId);

      // Clear session storage
      sessionStorage.removeItem('cvData');
      sessionStorage.removeItem('sessionId');

      // Automatically fetch matches
      fetchMatches(parsedCvData.member_id, matchMode);
    }
  }, []);

  const handleFileSelect = (e) => {
    const selectedFile = e.target.files[0];
    if (selectedFile &&
      (selectedFile.type === 'application/pdf' ||
        selectedFile.type === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document')) {
      setFile(selectedFile);
      setError('');
    } else {
      setError('Please select a PDF or DOCX file');
    }
  };

  const handleDrop = (e) => {
    e.preventDefault();
    const droppedFile = e.dataTransfer.files[0];
    if (droppedFile &&
      (droppedFile.type === 'application/pdf' ||
        droppedFile.type === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document')) {
      setFile(droppedFile);
      setError('');
    } else {
      setError('Please drop a PDF or DOCX file');
    }
  };

  const handleDragOver = (e) => {
    e.preventDefault();
  };

  const uploadAndAnalyze = async () => {
    if (!file) {
      setError('Please select a file first');
      return;
    }

    setUploading(true);
    setError('');

    try {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('session_id', sessionId);

      // Direct call to ML service
      const response = await fetch('http://localhost:8000/upload_cv', {
        method: 'POST',
        body: formData
      });

      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`Upload failed: ${errorText}`);
      }

      const data = await response.json();
      setCvData(data);

      // Automatically fetch matches after successful upload
      await fetchMatches(data.member_id, matchMode);

    } catch (err) {
      console.error('Upload error:', err);
      setError(err.message || 'Failed to upload CV. Make sure the ML service is running on port 8000.');
    } finally {
      setUploading(false);
    }
  };

  const fetchMatches = async (memberId, mode) => {
    setMatching(true);
    setError('');

    // Clear previous matches when switching modes
    setMatches([]);

    try {
      const response = await fetch('http://localhost:8000/match_jobs', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          session_id: sessionId,
          member_id: memberId || cvData?.member_id,
          mode: mode,
          top_k: 10
        })
      });

      if (!response.ok) {
        throw new Error('Failed to fetch matches');
      }

      const data = await response.json();
      console.log('Match response:', data); // Debug log

      // Process matches to ensure proper format
      const processedMatches = (data.matches || []).map(match => ({
        ...match,
        // Ensure score is a valid number
        score: typeof match.score === 'number' ? match.score : 0.5,
        // Ensure job_id exists, fallback to id
        job_id: match.job_id || match.id || `job-${Math.random()}`
      }));

      setMatches(processedMatches);

      // If no matches found, show a message
      if (processedMatches.length === 0) {
        setError('No matching jobs found. This might be because the ML service has different job IDs than the main database.');
      }
    } catch (err) {
      console.error('Matching error:', err);
      setError('Failed to fetch job matches');
    } finally {
      setMatching(false);
    }
  };

  const handleModeChange = (mode) => {
    setMatchMode(mode);
    if (cvData?.member_id) {
      fetchMatches(cvData.member_id, mode);
    }
  };

  const handleViewDetails = async (match) => {
    // Try different ID fields that might exist
    const jobId = match.job_id || match.id || match.jobId;

    if (!jobId || jobId === 'undefined') {
      alert('Job details not available. Job ID is missing.');
      console.error('Invalid job ID:', match);
      return;
    }

    // First try to navigate to the job details
    // If the job doesn't exist in Spring Boot, it will show "Job not found"
    console.log('Navigating to job:', jobId);

    // Check if it's a test job ID (from ML service)
    if (jobId.startsWith('test-') || jobId.startsWith('j')) {
      alert('This is a test job from the ML service. It may not exist in the main database.\n\nTo fix this:\n1. Add real jobs through the employer portal\n2. Or ensure ML service uses the same job IDs as your database');
      return;
    }

    navigate(`/jobs/${jobId}`);
  };

  // Calculate match percentage safely
  const getMatchPercentage = (score) => {
    if (typeof score !== 'number' || isNaN(score)) {
      return 50; // Default to 50% if score is invalid
    }
    return Math.round(score * 100);
  };

  return (
    <div className="job-matching-dashboard">
      <div className="dashboard-header">
        <h1>AI-Powered Job Matching</h1>
        <p>Upload your CV and let our AI find the perfect jobs for you</p>
      </div>

      {!cvData ? (
        <div className="upload-section">
          <div
            className="drop-zone"
            onDrop={handleDrop}
            onDragOver={handleDragOver}
          >
            <svg className="upload-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
              <polyline points="7 10 12 15 17 10"></polyline>
              <line x1="12" y1="15" x2="12" y2="3"></line>
            </svg>

            <p>Drag and drop your CV here, or</p>

            <label className="file-select-btn">
              Browse Files
              <input
                type="file"
                accept=".pdf,.docx"
                onChange={handleFileSelect}
                style={{ display: 'none' }}
              />
            </label>

            {file && (
              <div className="selected-file">
                <span>Selected: {file.name}</span>
              </div>
            )}
          </div>

          {error && <div className="error-message">{error}</div>}

          <button
            onClick={uploadAndAnalyze}
            disabled={!file || uploading}
            className="analyze-btn"
          >
            {uploading ? 'Uploading and Analyzing...' : 'Upload and Analyze CV'}
          </button>

          <div className="privacy-notice">
            🔒 Your CV is only stored temporarily during this session
          </div>
        </div>
      ) : (
        <div className="results-section">
          <div className="cv-summary">
            <h2>CV Analysis Complete</h2>
            <div className="cv-details">
              <div className="detail-item">
                <span className="label">Skills:</span>
                <span className="value">{cvData.skills?.join(', ') || 'Not specified'}</span>
              </div>
              <div className="detail-item">
                <span className="label">Experience:</span>
                <span className="value">{cvData.experience_years} years</span>
              </div>
              <div className="detail-item">
                <span className="label">Seniority:</span>
                <span className="value">{cvData.seniority_level}</span>
              </div>
              {cvData.title && (
                <div className="detail-item">
                  <span className="label">Current Role:</span>
                  <span className="value">{cvData.title}</span>
                </div>
              )}
            </div>

            <button
              onClick={() => {
                setCvData(null);
                setMatches([]);
                setFile(null);
              }}
              className="upload-another-btn"
            >
              Upload Another CV
            </button>
          </div>

          <div className="matches-section">
            <div className="matches-header">
              <h2>Job Matches</h2>
              <div className="match-modes">
                <button
                  className={matchMode === 'strict' ? 'active' : ''}
                  onClick={() => handleModeChange('strict')}
                  disabled={matching}
                >
                  STRICT
                </button>
                <button
                  className={matchMode === 'flexible' ? 'active' : ''}
                  onClick={() => handleModeChange('flexible')}
                  disabled={matching}
                >
                  FLEXIBLE
                </button>
                <button
                  className={matchMode === 'graduate_friendly' ? 'active' : ''}
                  onClick={() => handleModeChange('graduate_friendly')}
                  disabled={matching}
                >
                  GRADUATE FRIENDLY
                </button>
              </div>
            </div>

            {matching && <div className="loading">Finding best matches for {matchMode.replace('_', ' ')} mode...</div>}

            {!matching && matches.length === 0 && (
              <div className="no-matches">
                <p>No matches found. This could be because:</p>
                <ul>
                  <li>The ML service has test jobs that don't exist in your main database</li>
                  <li>No jobs match your profile in this mode</li>
                </ul>
                <p>Try a different matching mode or ensure jobs exist in both databases.</p>
              </div>
            )}

            <div className="matches-grid">
              {matches.map((match, index) => (
                <div key={`${match.job_id}-${index}`} className="match-card">
                  <div className="match-score">
                    {getMatchPercentage(match.score)}%
                  </div>
                  <h3>{match.title || 'Untitled Position'}</h3>
                  <p className="company">{match.company || 'Company Name'}</p>
                  <p className="location">
                    {match.location || 'Location not specified'} • {match.seniority_level || 'Level not specified'}
                  </p>
                  {match.required_skills && match.required_skills.length > 0 && (
                    <p className="skills">
                      <strong>Skills:</strong> {match.required_skills.join(', ')}
                    </p>
                  )}
                  <p className="job-id-info">
                    Job ID: {match.job_id || 'N/A'}
                  </p>
                  <button
                    onClick={() => handleViewDetails(match)}
                    className="view-job-btn"
                  >
                    View Details
                  </button>
                </div>
              ))}
            </div>

            {!matching && matches.length > 0 && (
              <div className="match-summary">
                <p>Found {matches.length} matches in <strong>{matchMode.replace('_', ' ')}</strong> mode</p>
                <p className="warning-note">
                  Note: If "View Details" shows "Job not found", it means the ML service has different job IDs than your main database.
                </p>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

export default JobMatchingDashboard;