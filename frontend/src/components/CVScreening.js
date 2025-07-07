// frontend/src/components/CVScreening.js
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import './CVScreening.css';

const CVScreening = () => {
    const { user } = useAuth();
    const navigate = useNavigate();
    const [file, setFile] = useState(null);
    const [uploading, setUploading] = useState(false);
    const [cvData, setCvData] = useState(null);
    const [error, setError] = useState('');
    const [sessionId] = useState(`session-${Date.now()}`);

    const handleFileChange = (e) => {
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

    const handleUpload = async () => {
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

            // Direct call to ML service for CV parsing
            const response = await fetch('http://localhost:8000/upload_cv', {
                method: 'POST',
                body: formData
            });

            if (!response.ok) {
                throw new Error('Failed to upload CV');
            }

            const data = await response.json();

            // Store the parsed CV data
            setCvData({
                member_id: data.member_id,
                skills: data.skills || [],
                experience_years: data.experience_years || 0,
                seniority_level: data.seniority_level || 'entry',
                title: data.title || 'Not specified',
                education: data.education || 'Not specified',
                extracted_text: data.extracted_text || '',
                location: data.location || 'Not specified',
                sessionId: sessionId // Include sessionId for job matching
            });

        } catch (err) {
            console.error('Upload error:', err);
            setError('Failed to upload CV. Make sure the ML service is running on port 8000.');
        } finally {
            setUploading(false);
        }
    };

    const handleStartMatching = () => {
        if (!cvData) {
            setError('Please upload a CV first');
            return;
        }

        // Store CV data in sessionStorage to pass to job-matches page
        sessionStorage.setItem('cvData', JSON.stringify(cvData));
        sessionStorage.setItem('sessionId', sessionId);

        // Navigate to job-matches page
        navigate('/job-matches');
    };

    return (
        <div className="cv-screening-container">
            <div className="screening-header">
                <h1>CV Screening & Analysis</h1>
                <p>Upload your CV to analyze your skills and experience</p>
            </div>

            <div className="upload-section">
                <div className="file-upload-area">
                    <input
                        type="file"
                        id="cv-upload"
                        accept=".pdf,.docx"
                        onChange={handleFileChange}
                        className="file-input"
                    />

                    <label htmlFor="cv-upload" className="file-label">
                        <svg className="upload-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                            <polyline points="17 8 12 3 7 8"></polyline>
                            <line x1="12" y1="3" x2="12" y2="15"></line>
                        </svg>

                        {file ? (
                            <span className="file-name">{file.name}</span>
                        ) : (
                            <span>Choose CV file (PDF or DOCX)</span>
                        )}
                    </label>
                </div>

                {error && <div className="error-message">{error}</div>}

                <button
                    onClick={handleUpload}
                    disabled={!file || uploading}
                    className="upload-btn"
                >
                    {uploading ? 'Analyzing CV...' : 'Upload & Analyze'}
                </button>
            </div>

            {cvData && (
                <div className="cv-results">
                    <h2>CV Analysis Results</h2>

                    <div className="results-grid">
                        <div className="result-card">
                            <h3>Personal Information</h3>
                            <div className="result-item">
                                <span className="label">Current Role:</span>
                                <span className="value">{cvData.title}</span>
                            </div>
                            <div className="result-item">
                                <span className="label">Location:</span>
                                <span className="value">{cvData.location}</span>
                            </div>
                        </div>

                        <div className="result-card">
                            <h3>Experience & Education</h3>
                            <div className="result-item">
                                <span className="label">Years of Experience:</span>
                                <span className="value">{cvData.experience_years} years</span>
                            </div>
                            <div className="result-item">
                                <span className="label">Seniority Level:</span>
                                <span className="value">{cvData.seniority_level}</span>
                            </div>
                            <div className="result-item">
                                <span className="label">Education:</span>
                                <span className="value">{cvData.education}</span>
                            </div>
                        </div>

                        <div className="result-card full-width">
                            <h3>Skills Identified</h3>
                            <div className="skills-list">
                                {cvData.skills.length > 0 ? (
                                    cvData.skills.map((skill, index) => (
                                        <span key={index} className="skill-tag">{skill}</span>
                                    ))
                                ) : (
                                    <span className="no-skills">No specific skills identified</span>
                                )}
                            </div>
                        </div>
                    </div>

                    <div className="action-buttons">
                        <button
                            onClick={handleStartMatching}
                            className="match-btn primary"
                        >
                            Find Matching Jobs
                        </button>
                        <button
                            onClick={() => {
                                setCvData(null);
                                setFile(null);
                            }}
                            className="match-btn secondary"
                        >
                            Upload Different CV
                        </button>
                    </div>
                </div>
            )}

            <div className="info-section">
                <h3>How it works</h3>
                <ol>
                    <li>Upload your CV in PDF or DOCX format</li>
                    <li>Our AI analyzes your skills, experience, and qualifications</li>
                    <li>Get personalized job recommendations based on your profile</li>
                    <li>Apply to jobs that match your expertise</li>
                </ol>
            </div>
        </div>
    );
};

export default CVScreening;