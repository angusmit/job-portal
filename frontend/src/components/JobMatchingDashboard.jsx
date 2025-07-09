import { useNavigate } from 'react-router-dom'; // Import the useNavigate hook
import { AlertCircle, Award, Briefcase, Clock, FileText, MapPin, TrendingUp, Upload, X } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';

const JobMatchingDashboard = () => {
  const navigate = useNavigate(); // Initialize navigate function from React Router

  const [file, setFile] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [cvData, setCvData] = useState(null);
  const [jobMatches, setJobMatches] = useState([]);
  const [loading, setLoading] = useState(false);
  const [matchMode, setMatchMode] = useState('graduate_friendly');
  const [error, setError] = useState('');
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [sessionId, setSessionId] = useState(`session-${Date.now()}`);

  // Check authentication on component mount
  useEffect(() => {
    checkAuthentication();
  }, []);

  const checkAuthentication = () => {
    const token = localStorage.getItem('token') || sessionStorage.getItem('token');
    setIsAuthenticated(!!token);

    if (!token) {
      setError('Please login to use CV matching features');
    }
  };

  // Get auth headers
  const getAuthHeaders = () => {
    const token = localStorage.getItem('token') || sessionStorage.getItem('token');
    return {
      'Authorization': `Bearer ${token}`
    };
  };

  // Handle file drop
  const handleDrop = useCallback((e) => {
    e.preventDefault();
    const droppedFile = e.dataTransfer.files[0];
    if (droppedFile && isValidFile(droppedFile)) {
      setFile(droppedFile);
      setError('');
    } else {
      setError('Please upload a PDF, DOCX, or TXT file');
    }
  }, []);

  const handleDragOver = (e) => {
    e.preventDefault();
  };

  const isValidFile = (file) => {
    const validTypes = ['application/pdf', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'text/plain'];
    return validTypes.includes(file.type) ||
      file.name.endsWith('.pdf') ||
      file.name.endsWith('.docx') ||
      file.name.endsWith('.txt');
  };

  // Upload CV with authentication
  const uploadCV = async () => {
    if (!file) return;

    if (!isAuthenticated) {
      setError('Please login to upload your CV');
      return;
    }

    setUploading(true);
    setError('');

    const formData = new FormData();
    formData.append('file', file);
    formData.append('storageType', 'temporary');

    try {
      const response = await fetch('/api/match/upload-cv', {
        method: 'POST',
        headers: {
          ...getAuthHeaders()
        },
        body: formData,
        credentials: 'include'
      });

      const data = await response.json();

      if (!response.ok) {
        throw new Error(data.error || data.details || 'Failed to upload CV');
      }

      // Handle successful response
      if (data.success && data.data) {
        setCvData(data.data);
        await fetchJobMatches();
      } else {
        throw new Error('Invalid response format');
      }

    } catch (err) {
      console.error('Upload error:', err);
      setError(err.message || 'Error uploading CV');
    } finally {
      setUploading(false);
    }
  };

  // Fetch job matches with authentication
  const fetchJobMatches = async () => {
    if (!isAuthenticated) {
      setError('Please login to view job matches');
      return;
    }

    setLoading(true);
    setError('');

    try {
      const response = await fetch(`/api/match/jobs?mode=${matchMode}&limit=20`, {
        headers: {
          ...getAuthHeaders(),
          'Content-Type': 'application/json'
        },
        credentials: 'include'
      });

      const data = await response.json();

      if (!response.ok) {
        throw new Error(data.error || 'Failed to fetch job matches');
      }

      setJobMatches(data.matches || []);
    } catch (err) {
      console.error('Fetch error:', err);
      setError(err.message || 'Error fetching job matches');
    } finally {
      setLoading(false);
    }
  };

  // Clear CV data
  const clearCV = async () => {
    try {
      const response = await fetch('/api/match/clear-cv', {
        method: 'DELETE',
        headers: {
          ...getAuthHeaders(),
          'Content-Type': 'application/json'
        },
        credentials: 'include'
      });

      if (!response.ok) {
        throw new Error('Failed to clear CV data');
      }

      setFile(null);
      setCvData(null);
      setJobMatches([]);
      setError('');
    } catch (err) {
      setError('Error clearing CV data');
    }
  };

  // Get match quality color
  const getMatchQualityColor = (quality) => {
    switch (quality) {
      case 'EXCELLENT': return 'text-green-600 bg-green-100';
      case 'GOOD': return 'text-blue-600 bg-blue-100';
      case 'FAIR': return 'text-yellow-600 bg-yellow-100';
      default: return 'text-gray-600 bg-gray-100';
    }
  };

  // Safe percentage calculation
  const getMatchPercentage = (score) => {
    if (typeof score !== 'number' || isNaN(score)) {
      return 50; // Default to 50% if score is invalid
    }
    return Math.round(score * 100);
  };

  // Login prompt component
  const LoginPrompt = () => (
    <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-6 text-center">
      <AlertCircle className="w-12 h-12 text-yellow-600 mx-auto mb-4" />
      <h3 className="text-lg font-semibold text-gray-900 mb-2">Authentication Required</h3>
      <p className="text-gray-600 mb-4">Please login to use the AI-powered job matching feature</p>
      <button
        onClick={() => window.location.href = '/login'}
        className="bg-blue-600 text-white px-6 py-2 rounded-lg hover:bg-blue-700"
      >
        Login Now
      </button>
    </div>
  );

  // Handle view details navigation
  const handleViewDetails = (match) => {
    // Try different ID fields that might exist
    const jobId = match.job_id || match.id || match.jobId;

    if (jobId && jobId !== 'undefined') {
      navigate(`/jobs/${jobId}`);
    } else {
      alert('Job details not available. Job ID is missing.');
    }
  };

  // Check CV data and automatically fetch matches if available
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

  // Handle file selection
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

  // Upload and analyze CV
const uploadAndAnalyze = async () => {
  if (!file) {
    setError('Please select a file first');
    return;
  }

  setUploading(true);
  setError('');

  try {
    const formData = new FormData();
    formData.append('file', file, file.name);
    formData.append('session_id', sessionId);

    const response = await fetch('http://localhost:8000/upload_cv', {
      method: 'POST',
      body: formData,
    });

    if (!response.ok) {
      throw new Error('Failed to upload CV');
    }

    const data = await response.json();
    setCvData(data);
    await fetchMatches(data.member_id, matchMode);

    // Redirect to results page after successful upload and match
    navigate('/job-matches/results');
  } catch (err) {
    setError(err.message || 'Error uploading CV');
  } finally {
    setUploading(false);
  }
};

  // Fetch job matches
  const fetchMatches = async (memberId, mode) => {
    setLoading(true);
    setError('');

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
      const processedMatches = data.matches.map(match => ({
        ...match,
        score: typeof match.score === 'number' ? match.score : 0.5,
        job_id: match.job_id || match.id || `job-${Math.random()}`
      }));

      setJobMatches(processedMatches);
    } catch (err) {
      setError(err.message || 'Failed to fetch job matches');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-gray-50 p-4">
      <div className="max-w-7xl mx-auto">
        <h1 className="text-3xl font-bold text-gray-900 mb-8">AI-Powered Job Matching</h1>

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
                  <span style={{ fontSize: '12px', color: '#666' }}> ({(file.size / 1024).toFixed(1)} KB)</span>
                </div>
              )}
            </div>

            {error && (
              <div className="error-message" style={{ whiteSpace: 'pre-wrap' }}>
                {error}
              </div>
            )}

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
                  setJobMatches([]);
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
              </div>

              {jobMatches.length > 0 ? (
                <div className="matches-grid">
                  {jobMatches.map((match) => (
                    <div key={match.job_id} className="match-card">
                      <div className="match-score">
                        {getMatchPercentage(match.score)}%
                      </div>
                      <h3>{match.title || 'Untitled Position'}</h3>
                      <p className="company">{match.company || 'Company Name'}</p>
                      <p className="location">{match.location || 'Location not specified'} • {match.seniority_level || 'Level not specified'}</p>

                      <button 
                        onClick={() => handleViewDetails(match)}
                        className="view-job-btn"
                      >
                        View Details
                      </button>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="text-center py-8 text-gray-500">
                  <Briefcase className="w-12 h-12 mx-auto mb-4 text-gray-300" />
                  <p>No job matches found. Try a different matching mode or upload a different CV.</p>
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default JobMatchingDashboard;
