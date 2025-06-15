// frontend/src/components/EditJob.js
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import authService from '../services/authService';
import './EditJob.css';

const EditJob = () => {
    const { id } = useParams();
    const navigate = useNavigate();
    const { user } = useAuth();
    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');

    const [jobData, setJobData] = useState({
        title: '',
        location: '',
        description: '',
        jobType: 'Full-time',
        salary: '',
        requirements: ''
    });

    useEffect(() => {
        fetchJobDetails();
    }, [id]);

    const fetchJobDetails = async () => {
        try {
            const response = await authService.axiosInstance.get(`/jobs/${id}`);
            const job = response.data;

            // Check if the current user is the owner of the job
            if (job.postedBy.id !== user.id && user.role !== 'ADMIN') {
                setError('You are not authorized to edit this job');
                setLoading(false);
                return;
            }

            setJobData({
                title: job.title,
                location: job.location,
                description: job.description,
                jobType: job.jobType,
                salary: job.salary,
                requirements: job.requirements
            });
            setLoading(false);
        } catch (error) {
            console.error('Error fetching job details:', error);
            setError('Failed to load job details');
            setLoading(false);
        }
    };

    const handleChange = (e) => {
        const { name, value } = e.target;
        setJobData(prev => ({
            ...prev,
            [name]: value
        }));
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setSubmitting(true);
        setError('');
        setSuccess('');

        try {
            const response = await authService.axiosInstance.put(`/jobs/${id}`, jobData);
            setSuccess(response.data.message || 'Job updated successfully!');

            // Redirect after 2 seconds
            setTimeout(() => {
                navigate('/my-jobs');
            }, 2000);
        } catch (error) {
            console.error('Error updating job:', error);
            setError(error.response?.data?.message || 'Failed to update job');
        } finally {
            setSubmitting(false);
        }
    };

    if (loading) {
        return <div className="loading">Loading job details...</div>;
    }

    if (error && !jobData.title) {
        return (
            <div className="error-container">
                <p className="error">{error}</p>
                <button onClick={() => navigate('/my-jobs')}>Back to My Jobs</button>
            </div>
        );
    }

    return (
        <div className="edit-job-container">
            <h2>Edit Job Post</h2>

            {error && <div className="error-message">{error}</div>}
            {success && <div className="success-message">{success}</div>}

            <form onSubmit={handleSubmit} className="edit-job-form">
                <div className="form-group">
                    <label htmlFor="title">Job Title *</label>
                    <input
                        type="text"
                        id="title"
                        name="title"
                        value={jobData.title}
                        onChange={handleChange}
                        required
                        placeholder="e.g., Senior Software Engineer"
                    />
                </div>

                <div className="form-group">
                    <label htmlFor="location">Location *</label>
                    <input
                        type="text"
                        id="location"
                        name="location"
                        value={jobData.location}
                        onChange={handleChange}
                        required
                        placeholder="e.g., New York, NY"
                    />
                </div>

                <div className="form-group">
                    <label htmlFor="jobType">Job Type *</label>
                    <select
                        id="jobType"
                        name="jobType"
                        value={jobData.jobType}
                        onChange={handleChange}
                        required
                    >
                        <option value="Full-time">Full-time</option>
                        <option value="Part-time">Part-time</option>
                        <option value="Contract">Contract</option>
                        <option value="Internship">Internship</option>
                        <option value="Remote">Remote</option>
                    </select>
                </div>

                <div className="form-group">
                    <label htmlFor="salary">Salary</label>
                    <input
                        type="text"
                        id="salary"
                        name="salary"
                        value={jobData.salary}
                        onChange={handleChange}
                        placeholder="e.g., $80,000 - $120,000"
                    />
                </div>

                <div className="form-group">
                    <label htmlFor="description">Job Description *</label>
                    <textarea
                        id="description"
                        name="description"
                        value={jobData.description}
                        onChange={handleChange}
                        required
                        rows="6"
                        placeholder="Describe the role, responsibilities, and what makes this opportunity unique..."
                    />
                </div>

                <div className="form-group">
                    <label htmlFor="requirements">Requirements *</label>
                    <textarea
                        id="requirements"
                        name="requirements"
                        value={jobData.requirements}
                        onChange={handleChange}
                        required
                        rows="6"
                        placeholder="List the required skills, experience, and qualifications..."
                    />
                </div>

                <div className="form-actions">
                    <button
                        type="button"
                        onClick={() => navigate('/my-jobs')}
                        className="cancel-btn"
                    >
                        Cancel
                    </button>
                    <button
                        type="submit"
                        disabled={submitting}
                        className="submit-btn"
                    >
                        {submitting ? 'Updating...' : 'Update Job'}
                    </button>
                </div>
            </form>

            <div className="info-note">
                <p><strong>Note:</strong> If this job was previously approved, updating it will require admin approval again before it appears publicly.</p>
            </div>
        </div>
    );
};

export default EditJob;