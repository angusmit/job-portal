// frontend/src/components/CVScreening.js
import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import './CVScreening.css';
import CVUpload from './CVUpload';
import JobMatch from './JobMatch';

const CVScreening = () => {
    const { user, isJobSeeker } = useAuth();
    const navigate = useNavigate();
    const [resumeData, setResumeData] = useState(null);
    const [activeTab, setActiveTab] = useState('upload');

    // Redirect if not job seeker
    React.useEffect(() => {
        if (!isJobSeeker) {
            navigate('/');
        }
    }, [isJobSeeker, navigate]);

    const handleUploadSuccess = (data) => {
        setResumeData(data);
        setActiveTab('matches');
    };

    return (
        <div className="cv-screening-container">
            <h1>Smart Job Matching</h1>

            <div className="tab-navigation">
                <button
                    className={activeTab === 'upload' ? 'active' : ''}
                    onClick={() => setActiveTab('upload')}
                >
                    Upload Resume
                </button>
                <button
                    className={activeTab === 'matches' ? 'active' : ''}
                    onClick={() => setActiveTab('matches')}
                    disabled={!resumeData}
                >
                    Job Matches
                </button>
            </div>

            {activeTab === 'upload' && (
                <div className="upload-section">
                    <CVUpload onUploadSuccess={handleUploadSuccess} />

                    <div className="feature-info">
                        <h3>How it works:</h3>
                        <ul>
                            <li>Upload your resume in PDF, DOC, DOCX, TXT, or RTF format</li>
                            <li>Our AI extracts your skills, experience, and education</li>
                            <li>Get matched with relevant jobs based on your profile</li>
                            <li>See detailed match analysis and recommendations</li>
                        </ul>

                        <h3>Privacy First:</h3>
                        <ul>
                            <li>Your resume is processed securely</li>
                            <li>Temporary storage is deleted after 1 hour</li>
                            <li>You control whether to save to your profile</li>
                            <li>No data is shared without your consent</li>
                        </ul>
                    </div>
                </div>
            )}

            {activeTab === 'matches' && resumeData && (
                <div className="matches-section">
                    <div className="resume-summary">
                        <h3>Your Profile Summary</h3>
                        <div className="summary-grid">
                            <div className="summary-item">
                                <strong>Skills:</strong>
                                <p>{resumeData.skills?.join(', ') || 'No skills detected'}</p>
                            </div>
                            <div className="summary-item">
                                <strong>Experience:</strong>
                                <p>{resumeData.experience || 0} years</p>
                            </div>
                            <div className="summary-item">
                                <strong>Education:</strong>
                                <p>{resumeData.education || 'Not specified'}</p>
                            </div>
                        </div>
                    </div>

                    <JobMatch resumeId={resumeData.resumeId} />
                </div>
            )}
        </div>
    );
};

export default CVScreening;