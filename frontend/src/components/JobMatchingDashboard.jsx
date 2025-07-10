import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Briefcase, Upload, FileText, CheckCircle } from 'lucide-react';
import './JobMatchingDashboard.css';

const JobMatchingDashboard = () => {
  const navigate = useNavigate();
  const { user, isAuthenticated } = useAuth();
  const [file, setFile] = useState(null);
  const [cvData, setCvData] = useState(null);
  const [jobMatches, setJobMatches] = useState([]);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');
  const [matchMode, setMatchMode] = useState('graduate_friendly');
  const [sessionId] = useState(() => {
    // Get or create session ID
    let id = sessionStorage.getItem('ml_session_id');
    if (!id) {
      id = `session_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
      sessionStorage.setItem('ml_session_id', id);
    }
    return id;
  });

  // Redirect if not authenticated
  useEffect(() => {
    if (!isAuthenticated) {
      navigate('/login');
    }
  }, [isAuthenticated, navigate]);

  // Check for existing CV data from CV screening page
  useEffect(() => {
    const storedCvData = sessionStorage.getItem('cvData');
    const storedSessionId = sessionStorage.getItem('sessionId');
    
    if (storedCvData && storedSessionId) {
      try {
        const parsedCvData = JSON.parse(storedCvData);
        setCvData(parsedCvData);
        
        // Clear session storage
        sessionStorage.removeItem('cvData');
        sessionStorage.removeItem('sessionId');
        
        // Automatically fetch matches
        fetchJobMatches(parsedCvData.member_id);
      } catch (err) {
        console.error('Error parsing stored CV data:', err);
      }
    }
  }, []);

  const handleFileSelect = (e) => {
    const selectedFile = e.target.files[0];
    if (selectedFile) {
      const validTypes = ['application/pdf', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'];
      if (validTypes.includes(selectedFile.type)) {
        setFile(selectedFile);
        setError('');
      } else {
        setError('Please select a PDF or DOCX file');
      }
    }
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
      formData.append('storageType', 'temporary');

      const token = localStorage.getItem('token');
      
      // Upload to Spring Boot backend
      const response = await fetch('/api/match/upload-cv', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`
        },
        body: formData
      });

      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.error || 'Failed to upload CV');
      }

      const result = await response.json();
      
      if (result.success && result.data) {
        setCvData(result.data);
        // Automatically fetch job matches
        await fetchJobMatches(result.data.member_id);
      } else {
        throw new Error(result.error || 'Invalid response from server');
      }

    } catch (err) {
      console.error('Upload error:', err);
      setError(err.message || 'Error uploading CV');
    } finally {
      setUploading(false);
    }
  };

  const fetchJobMatches = async (memberId) => {
    setLoading(true);
    setError('');

    try {
      const token = localStorage.getItem('token');
      const response = await fetch(`/api/match/jobs?mode=${matchMode}&limit=20`, {
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        }
      });

      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.error || 'Failed to fetch job matches');
      }

      const data = await response.json();
      setJobMatches(data.matches || []);
      
    } catch (err) {
      console.error('Fetch error:', err);
      setError(err.message || 'Error fetching job matches');
    } finally {
      setLoading(false);
    }
  };

  const handleViewDetails = (match) => {
    // Extract numeric ID from job_id (e.g., "123" from "123" or "job_123")
    const jobId = match.job_id.replace(/\D/g, '') || match.job_id;
    navigate(`/jobs/${jobId}`);
  };

  const getMatchPercentage = (score) => {
    return Math.round(score * 100);
  };

  const getMatchColor = (score) => {
    if (score >= 0.8) return '#22c55e';
    if (score >= 0.6) return '#3b82f6';
    if (score >= 0.4) return '#f59e0b';
    return '#6b7280';
  };

  const clearCV = async () => {
    try {
      const token = localStorage.getItem('token');
      await fetch('/api/match/clear-cv', {
        method: 'DELETE',
        headers: {
          'Authorization': `Bearer ${token}`
        }
      });
      
      setFile(null);
      setCvData(null);
      setJobMatches([]);
      setError('');
    } catch (err) {
      console.error('Clear CV error:', err);
    }
  };

  return (
    <div className="job-matching-dashboard">
      <div className="dashboard-header">
        <h1>AI-Powered Job Matching</h1>
        <p>Upload your CV to get personalized job recommendations</p>
      </div>

      {error && (
        <div className="error-banner">
          <span>{error}</span>
          <button onClick={() => setError('')}>×</button>
        </div>
      )}

      {!cvData ? (
        <div className="upload-section">
          <div className="upload-card">
            <Upload className="upload-icon" />
            <h2>Upload Your CV</h2>
            <p>Support for PDF and DOCX formats</p>

            <input
              type="file"
              id="cv-file"
              accept=".pdf,.docx"
              onChange={handleFileSelect}
              className="file-input"
            />
            
            <label htmlFor="cv-file" className="file-label">
              {file ? (
                <span className="file-selected">
                  <FileText size={20} />
                  {file.name}
                </span>
              ) : (
                <span>Choose File</span>
              )}
            </label>

            <button 
              onClick={uploadAndAnalyze}
              disabled={!file || uploading}
              className="upload-btn"
            >
              {uploading ? 'Uploading and Analyzing...' : 'Upload and Analyze CV'}
            </button>

            <div className="privacy-notice">
              🔒 Your CV is only stored temporarily during this session
            </div>
          </div>
        </div>
      ) : (
        <div className="results-section">
          <div className="cv-summary">
            <div className="summary-header">
              <h2>CV Analysis Complete</h2>
              <CheckCircle className="success-icon" />
            </div>
            
            <div className="cv-details">
              <div className="detail-row">
                <span className="label">Skills Identified:</span>
                <div className="skills-tags">
                  {cvData.skills?.slice(0, 10).map((skill, idx) => (
                    <span key={idx} className="skill-tag">{skill}</span>
                  ))}
                  {cvData.skills?.length > 10 && (
                    <span className="skill-tag more">+{cvData.skills.length - 10} more</span>
                  )}
                </div>
              </div>
              
              <div className="detail-row">
                <span className="label">Experience:</span>
                <span className="value">{cvData.experience_years} years</span>
              </div>
              
              <div className="detail-row">
                <span className="label">Seniority:</span>
                <span className="value">{cvData.seniority_level}</span>
              </div>
              
              {cvData.title && (
                <div className="detail-row">
                  <span className="label">Current Role:</span>
                  <span className="value">{cvData.title}</span>
                </div>
              )}
            </div>

            <div className="actions">
              <button onClick={clearCV} className="secondary-btn">
                Upload Another CV
              </button>
              
              <select 
                value={matchMode} 
                onChange={(e) => {
                  setMatchMode(e.target.value);
                  if (cvData?.member_id) {
                    fetchJobMatches(cvData.member_id);
                  }
                }}
                className="mode-select"
              >
                <option value="graduate_friendly">Graduate Friendly</option>
                <option value="flexible">Flexible Matching</option>
                <option value="strict">Strict Matching</option>
              </select>
            </div>
          </div>

          <div className="matches-section">
            <h2>Job Matches</h2>
            
            {loading ? (
              <div className="loading-state">
                <div className="spinner"></div>
                <p>Finding the best job matches for you...</p>
              </div>
            ) : jobMatches.length > 0 ? (
              <div className="matches-grid">
                {jobMatches.map((match) => (
                  <div key={match.job_id} className="match-card">
                    <div 
                      className="match-score"
                      style={{ backgroundColor: getMatchColor(match.score) }}
                    >
                      {getMatchPercentage(match.score)}%
                    </div>
                    
                    <h3>{match.title || 'Untitled Position'}</h3>
                    <p className="company">{match.company || 'Company Name'}</p>
                    <p className="location">
                      📍 {match.location || 'Location not specified'}
                    </p>
                    
                    <div className="match-details">
                      <div className="detail">
                        <span className="detail-label">Experience:</span>
                        <span>{match.experience_required} years</span>
                      </div>
                      <div className="detail">
                        <span className="detail-label">Level:</span>
                        <span>{match.seniority_level}</span>
                      </div>
                    </div>
                    
                    {match.match_details && (
                      <div className="skill-match-info">
                        <span>
                          {match.match_details.skill_match} of {match.match_details.total_required_skills} skills matched
                        </span>
                      </div>
                    )}
                    
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
              <div className="no-matches">
                <Briefcase className="no-matches-icon" />
                <p>No job matches found. Try adjusting the matching mode.</p>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

export default JobMatchingDashboard;