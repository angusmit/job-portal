import axios from 'axios';
import { useState } from 'react';
import './CVUpload.css';

const CVUpload = ({ onUploadSuccess }) => {
    const [file, setFile] = useState(null);
    const [uploading, setUploading] = useState(false);
    const [uploadResult, setUploadResult] = useState(null);
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
            formData.append('sessionId', sessionId);

            const response = await axios.post(
                'http://localhost:8080/api/cv/upload',
                formData,
                {
                    headers: {
                        'Content-Type': 'multipart/form-data',
                        'Authorization': `Bearer ${localStorage.getItem('token')}`
                    }
                }
            );

            setUploadResult(response.data);
            if (onUploadSuccess) {
                onUploadSuccess({
                    ...response.data,
                    sessionId: sessionId
                });
            }
        } catch (err) {
            setError(err.response?.data?.error || 'Failed to upload CV');
        } finally {
            setUploading(false);
        }
    };

    return (
        <div className="cv-upload">
            <h2>Upload Your Resume</h2>

            <div className="upload-area">
                <input
                    type="file"
                    id="cv-file"
                    accept=".pdf,.docx"
                    onChange={handleFileChange}
                    className="file-input"
                />

                <label htmlFor="cv-file" className="file-label">
                    {file ? file.name : 'Choose a file (PDF or DOCX)'}
                </label>

                <button
                    onClick={handleUpload}
                    disabled={!file || uploading}
                    className="upload-btn"
                >
                    {uploading ? 'Uploading...' : 'Upload CV'}
                </button>
            </div>

            {error && <div className="error-message">{error}</div>}

            {uploadResult && (
                <div className="upload-result">
                    <h3>CV Analysis Results</h3>
                    <div className="result-details">
                        <p><strong>Skills Found:</strong> {uploadResult.skills?.join(', ')}</p>
                        <p><strong>Experience:</strong> {uploadResult.experience_years} years</p>
                        <p><strong>Seniority Level:</strong> {uploadResult.seniority_level}</p>
                    </div>
                </div>
            )}

            <div className="privacy-notice">
                🔒 Your CV is stored temporarily and will be deleted after your session ends
            </div>
        </div>
    );
};

export default CVUpload;