// frontend/src/components/CVUpload.js
import { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import './CVUpload.css';

const CVUpload = ({ onUploadSuccess }) => {
    const { user } = useAuth();
    const [file, setFile] = useState(null);
    const [uploading, setUploading] = useState(false);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    const [parsedData, setParsedData] = useState(null);
    const [storageOption, setStorageOption] = useState('temporary');

    const handleFileChange = (e) => {
        const selectedFile = e.target.files[0];

        if (selectedFile) {
            // Validate file size (5MB)
            if (selectedFile.size > 5 * 1024 * 1024) {
                setError('File size must be less than 5MB');
                return;
            }

            // Validate file type
            const allowedTypes = [
                'application/pdf',
                'application/msword',
                'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
                'text/plain',
                'application/rtf'
            ];

            if (!allowedTypes.includes(selectedFile.type)) {
                setError('Please upload a PDF, DOC, DOCX, TXT, or RTF file');
                return;
            }

            setFile(selectedFile);
            setError('');
        }
    };

    const handleUpload = async () => {
        if (!file) {
            setError('Please select a file');
            return;
        }

        setUploading(true);
        setError('');
        setSuccess('');

        const formData = new FormData();
        formData.append('file', file);
        formData.append('permanent', storageOption === 'permanent');

        try {
            const token = localStorage.getItem('token');
            const response = await fetch('http://localhost:8080/api/resume/upload', {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${token}`
                },
                body: formData
            });

            if (!response.ok) {
                const errorData = await response.json();
                throw new Error(errorData.message || 'Upload failed');
            }

            const data = await response.json();
            setParsedData(data);
            setSuccess('Resume uploaded and parsed successfully!');

            if (onUploadSuccess) {
                onUploadSuccess(data);
            }

        } catch (error) {
            console.error('Upload error:', error);
            setError(error.message || 'Failed to upload resume');
        } finally {
            setUploading(false);
        }
    };

    const formatSkills = (skills) => {
        if (!skills || skills.length === 0) return 'No skills detected';
        return skills.join(', ');
    };

    return (
        <div className="cv-upload-container">
            <h3>Upload Your Resume</h3>

            <div className="privacy-notice">
                <p>🔒 <strong>Privacy Notice:</strong></p>
                <p>Your resume will be processed securely. Choose your storage preference:</p>
            </div>

            <div className="storage-options">
                <label>
                    <input
                        type="radio"
                        value="temporary"
                        checked={storageOption === 'temporary'}
                        onChange={(e) => setStorageOption(e.target.value)}
                    />
                    <span>Temporary (1 hour) - Recommended for privacy</span>
                </label>
                <label>
                    <input
                        type="radio"
                        value="permanent"
                        checked={storageOption === 'permanent'}
                        onChange={(e) => setStorageOption(e.target.value)}
                    />
                    <span>Save to profile - For easy job applications</span>
                </label>
            </div>

            <div className="file-upload-section">
                <input
                    type="file"
                    id="resume-file"
                    accept=".pdf,.doc,.docx,.txt,.rtf"
                    onChange={handleFileChange}
                    disabled={uploading}
                />
                <label htmlFor="resume-file" className="file-label">
                    {file ? file.name : 'Choose file...'}
                </label>
            </div>

            {file && (
                <div className="file-info">
                    <p>Selected: {file.name}</p>
                    <p>Size: {(file.size / 1024).toFixed(2)} KB</p>
                </div>
            )}

            <button
                onClick={handleUpload}
                disabled={!file || uploading}
                className="upload-btn"
            >
                {uploading ? 'Uploading...' : 'Upload & Parse Resume'}
            </button>

            {error && <div className="error-message">{error}</div>}
            {success && <div className="success-message">{success}</div>}

            {parsedData && (
                <div className="parsed-results">
                    <h4>Resume Analysis Results</h4>
                    <div className="result-item">
                        <strong>Skills Detected:</strong>
                        <p>{formatSkills(parsedData.skills)}</p>
                    </div>
                    <div className="result-item">
                        <strong>Experience:</strong>
                        <p>{parsedData.experience || 0} years</p>
                    </div>
                    <div className="result-item">
                        <strong>Education Level:</strong>
                        <p>{parsedData.education || 'Not specified'}</p>
                    </div>
                    {storageOption === 'temporary' && (
                        <div className="expiry-notice">
                            <p>⏱️ This resume will be deleted after 1 hour</p>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
};

export default CVUpload;