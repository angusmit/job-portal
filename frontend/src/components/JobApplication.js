import axios from 'axios';
import { useState } from 'react';
import './JobApplication.css';

const JobApplication = ({ jobId, jobTitle, onClose, onSuccess }) => {
    const [formData, setFormData] = useState({
        coverLetter: '',
        expectedSalary: '',
        availableFrom: '',
        additionalInfo: ''
    });
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState('');

    const handleChange = (field, value) => {
        setFormData({
            ...formData,
            [field]: value
        });
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setSubmitting(true);
        setError('');

        try {
            await axios.post(
                `http://localhost:8080/api/jobs/${jobId}/apply`,
                formData,
                {
                    headers: {
                        'Authorization': `Bearer ${localStorage.getItem('token')}`
                    }
                }
            );

            onSuccess();
            onClose();
        } catch (err) {
            setError(err.response?.data?.error || 'Failed to submit application');
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div className="application-modal">
            <div className="modal-content">
                <h2>Apply for {jobTitle}</h2>

                <form onSubmit={handleSubmit}>
                    <div className="form-group">
                        <label>Cover Letter *</label>
                        <textarea
                            value={formData.coverLetter}
                            onChange={(e) => handleChange('coverLetter', e.target.value)}
                            required
                            rows="6"
                            placeholder="Tell us why you're a great fit for this role..."
                        />
                    </div>

                    <div className="form-group">
                        <label>Expected Salary</label>
                        <input
                            type="text"
                            value={formData.expectedSalary}
                            onChange={(e) => handleChange('expectedSalary', e.target.value)}
                            placeholder="e.g., $70,000 - $80,000"
                        />
                    </div>

                    <div className="form-group">
                        <label>Available From</label>
                        <input
                            type="date"
                            value={formData.availableFrom}
                            onChange={(e) => handleChange('availableFrom', e.target.value)}
                            min={new Date().toISOString().split('T')[0]}
                        />
                    </div>

                    <div className="form-group">
                        <label>Additional Information</label>
                        <textarea
                            value={formData.additionalInfo}
                            onChange={(e) => handleChange('additionalInfo', e.target.value)}
                            rows="3"
                            placeholder="Any additional information you'd like to share..."
                        />
                    </div>

                    {error && <div className="error-message">{error}</div>}

                    <div className="form-actions">
                        <button type="button" onClick={onClose} className="cancel-btn">
                            Cancel
                        </button>
                        <button type="submit" disabled={submitting} className="submit-btn">
                            {submitting ? 'Submitting...' : 'Submit Application'}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
};

export default JobApplication;