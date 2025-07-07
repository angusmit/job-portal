// frontend/src/services/api.js
import axios from 'axios';

// API Base URLs
const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080/api';
const ML_SERVICE_URL = process.env.REACT_APP_ML_SERVICE_URL || 'http://localhost:8000';

// Create axios instance for main API
const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Create axios instance for ML service
const mlApi = axios.create({
  baseURL: ML_SERVICE_URL,
});

// Add token to main API requests
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Handle response errors
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // Token expired or invalid
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

// Auth API
export const authAPI = {
  login: (credentials) => api.post('/auth/signin', credentials),
  register: (userData) => api.post('/auth/signup', userData),
  logout: () => api.post('/auth/signout'),
  getCurrentUser: () => api.get('/auth/user'),
};

// Jobs API
export const jobsAPI = {
  // Public endpoints
  getAll: () => api.get('/jobs'),
  getById: (id) => api.get(`/jobs/${id}`),
  search: (query) => api.get(`/jobs/search?query=${encodeURIComponent(query)}`),
  filter: (params) => api.get('/jobs/filter', { params }),

  // Protected endpoints
  create: (jobData) => api.post('/jobs', jobData),
  update: (id, jobData) => api.put(`/jobs/${id}`, jobData),
  delete: (id) => api.delete(`/jobs/${id}`),
  getMyJobs: () => api.get('/jobs/my-jobs'),

  // Save/Unsave jobs
  saveJob: (jobId) => api.post(`/jobs/${jobId}/save`),
  unsaveJob: (jobId) => api.delete(`/jobs/${jobId}/save`),
  getSavedJobs: () => api.get('/jobs/saved'),

  // Apply for jobs
  apply: (jobId, applicationData) => api.post(`/jobs/${jobId}/apply`, applicationData),
  getApplications: () => api.get('/jobs/applications'),
};

// CV/ML API - Direct to ML Service
export const cvAPI = {
  // Direct ML service calls
  uploadCV: (formData) => {
    return mlApi.post('/upload_cv', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
  },

  matchJobs: (matchData) => {
    return mlApi.post('/match_jobs', matchData);
  },

  // Alternative: through Spring Boot proxy (if configured)
  uploadCVProxy: (formData) => {
    return api.post('/cv/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
  },

  matchJobsProxy: (matchData) => {
    return api.post('/jobs/match', matchData);
  },
};

// Admin API
export const adminAPI = {
  // Job approval
  getPendingJobs: () => api.get('/admin/pending-jobs'),
  approveJob: (id) => api.put(`/admin/jobs/${id}/approve`),
  rejectJob: (id, reason) => api.put(`/admin/jobs/${id}/reject`, { reason }),

  // User management
  getUsers: () => api.get('/admin/users'),
  deleteUser: (id) => api.delete(`/admin/users/${id}`),
  updateUserRole: (id, role) => api.put(`/admin/users/${id}/role`, { role }),

  // Statistics
  getStats: () => api.get('/admin/stats'),
};

// Employer API
export const employerAPI = {
  getCompanyJobs: () => api.get('/employer/jobs'),
  getJobApplications: (jobId) => api.get(`/employer/jobs/${jobId}/applications`),
  updateApplicationStatus: (jobId, applicationId, status) =>
    api.put(`/employer/jobs/${jobId}/applications/${applicationId}`, { status }),
};

// Export axios instances for custom usage
export { api as default, mlApi };

// Helper functions
export const isAuthenticated = () => {
  return !!localStorage.getItem('token');
};

export const getAuthToken = () => {
  return localStorage.getItem('token');
};

export const getCurrentUser = () => {
  const userStr = localStorage.getItem('user');
  return userStr ? JSON.parse(userStr) : null;
};