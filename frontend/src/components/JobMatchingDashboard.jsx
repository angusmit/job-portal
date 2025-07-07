import { AlertCircle, Award, Briefcase, Clock, FileText, MapPin, TrendingUp, Upload, X } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';

const JobMatchingDashboard = () => {
  const [file, setFile] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [cvData, setCvData] = useState(null);
  const [jobMatches, setJobMatches] = useState([]);
  const [loading, setLoading] = useState(false);
  const [matchMode, setMatchMode] = useState('graduate_friendly');
  const [error, setError] = useState('');
  const [isAuthenticated, setIsAuthenticated] = useState(false);

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
          // Don't set Content-Type for FormData
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
        // Automatically fetch job matches after successful upload
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

  if (!isAuthenticated) {
    return (
      <div className="min-h-screen bg-gray-50 p-4">
        <div className="max-w-7xl mx-auto">
          <h1 className="text-3xl font-bold text-gray-900 mb-8">AI-Powered Job Matching</h1>
          <LoginPrompt />
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 p-4">
      <div className="max-w-7xl mx-auto">
        <h1 className="text-3xl font-bold text-gray-900 mb-8">AI-Powered Job Matching</h1>

        {/* CV Upload Section */}
        <div className="bg-white rounded-lg shadow-md p-6 mb-8">
          <h2 className="text-xl font-semibold mb-4">Upload Your CV</h2>

          {!cvData ? (
            <div>
              <div
                onDrop={handleDrop}
                onDragOver={handleDragOver}
                className="border-2 border-dashed border-gray-300 rounded-lg p-8 text-center hover:border-blue-500 transition-colors cursor-pointer"
              >
                <Upload className="w-12 h-12 text-gray-400 mx-auto mb-4" />
                <p className="text-gray-600 mb-2">
                  Drag and drop your CV here, or click to browse
                </p>
                <input
                  type="file"
                  onChange={(e) => {
                    const selectedFile = e.target.files[0];
                    if (selectedFile && isValidFile(selectedFile)) {
                      setFile(selectedFile);
                      setError('');
                    } else {
                      setError('Please select a valid file type');
                    }
                  }}
                  accept=".pdf,.docx,.txt"
                  className="hidden"
                  id="file-upload"
                />
                <label
                  htmlFor="file-upload"
                  className="cursor-pointer text-blue-600 hover:text-blue-700"
                >
                  Select file
                </label>
              </div>

              {file && (
                <div className="mt-4 p-4 bg-gray-50 rounded-lg flex items-center justify-between">
                  <div className="flex items-center">
                    <FileText className="w-5 h-5 text-gray-600 mr-2" />
                    <span className="text-gray-700">{file.name}</span>
                    <span className="text-sm text-gray-500 ml-2">
                      ({(file.size / 1024).toFixed(2)} KB)
                    </span>
                  </div>
                  <button
                    onClick={() => setFile(null)}
                    className="text-red-600 hover:text-red-700"
                  >
                    <X className="w-5 h-5" />
                  </button>
                </div>
              )}

              {file && (
                <button
                  onClick={uploadCV}
                  disabled={uploading}
                  className="mt-4 w-full bg-blue-600 text-white py-2 px-4 rounded-lg hover:bg-blue-700 disabled:bg-gray-400 transition-colors"
                >
                  {uploading ? 'Processing...' : 'Upload and Analyze CV'}
                </button>
              )}
            </div>
          ) : (
            <div className="space-y-4">
              <div className="bg-green-50 border border-green-200 rounded-lg p-4">
                <h3 className="font-semibold text-green-800 mb-2">CV Successfully Analyzed!</h3>
                <div className="space-y-2 text-sm">
                  <p><span className="font-medium">Experience:</span> {cvData.experienceYears} years</p>
                  <p><span className="font-medium">Seniority:</span> {cvData.seniorityLevel}</p>
                  <p><span className="font-medium">Skills:</span> {cvData.skills.join(', ')}</p>
                  {cvData.title && <p><span className="font-medium">Current Role:</span> {cvData.title}</p>}
                </div>
              </div>

              <button
                onClick={clearCV}
                className="text-red-600 hover:text-red-700 text-sm"
              >
                Upload Different CV
              </button>
            </div>
          )}

          {error && (
            <div className="mt-4 p-4 bg-red-50 border border-red-200 rounded-lg text-red-700 flex items-start">
              <AlertCircle className="w-5 h-5 mr-2 flex-shrink-0 mt-0.5" />
              <span>{error}</span>
            </div>
          )}
        </div>

        {/* Matching Mode Selection */}
        {cvData && (
          <div className="bg-white rounded-lg shadow-md p-6 mb-8">
            <h2 className="text-xl font-semibold mb-4">Matching Mode</h2>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              {[
                { value: 'strict', label: 'Strict', desc: 'Exact seniority match' },
                { value: 'flexible', label: 'Flexible', desc: 'Adjacent levels' },
                { value: 'graduate_friendly', label: 'Graduate Friendly', desc: 'Best for new grads' },
                { value: 'experience_based', label: 'Experience Based', desc: 'Focus on years' }
              ].map(mode => (
                <button
                  key={mode.value}
                  onClick={() => {
                    setMatchMode(mode.value);
                    fetchJobMatches();
                  }}
                  className={`p-4 rounded-lg border-2 transition-colors ${matchMode === mode.value
                      ? 'border-blue-500 bg-blue-50'
                      : 'border-gray-200 hover:border-gray-300'
                    }`}
                >
                  <div className="font-medium">{mode.label}</div>
                  <div className="text-xs text-gray-600 mt-1">{mode.desc}</div>
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Job Matches */}
        {cvData && (
          <div className="bg-white rounded-lg shadow-md p-6">
            <h2 className="text-xl font-semibold mb-4">
              Your Job Matches
              {jobMatches.length > 0 && <span className="text-gray-500 text-base ml-2">({jobMatches.length} found)</span>}
            </h2>

            {loading ? (
              <div className="text-center py-8">
                <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto"></div>
                <p className="text-gray-600 mt-4">Finding your perfect matches...</p>
              </div>
            ) : jobMatches.length > 0 ? (
              <div className="space-y-4">
                {jobMatches.map((job, index) => (
                  <div key={job.jobId || index} className="border rounded-lg p-4 hover:shadow-md transition-shadow">
                    <div className="flex justify-between items-start mb-2">
                      <div className="flex-1">
                        <h3 className="text-lg font-semibold text-gray-900">
                          {index + 1}. {job.title}
                        </h3>
                        <p className="text-gray-600 flex items-center mt-1">
                          <Briefcase className="w-4 h-4 mr-1" />
                          {job.company}
                          <MapPin className="w-4 h-4 ml-3 mr-1" />
                          {job.location}
                        </p>
                      </div>
                      <div className="text-right">
                        <div className={`inline-flex items-center px-3 py-1 rounded-full text-sm font-medium ${getMatchQualityColor(job.matchQuality)}`}>
                          <TrendingUp className="w-4 h-4 mr-1" />
                          {Math.round(job.matchScore * 100)}% Match
                        </div>
                        <p className="text-xs text-gray-500 mt-1">{job.matchQuality}</p>
                      </div>
                    </div>

                    <p className="text-gray-700 text-sm mb-3">{job.description}</p>

                    <div className="flex flex-wrap gap-2 mb-3">
                      {job.requiredSkills && job.requiredSkills.map((skill, idx) => (
                        <span
                          key={idx}
                          className={`px-2 py-1 text-xs rounded ${cvData.skills && cvData.skills.includes(skill)
                              ? 'bg-green-100 text-green-700'
                              : 'bg-gray-100 text-gray-700'
                            }`}
                        >
                          {skill}
                        </span>
                      ))}
                    </div>

                    <div className="flex items-center text-sm text-gray-600 space-x-4">
                      <span className="flex items-center">
                        <Clock className="w-4 h-4 mr-1" />
                        {job.experienceRequired}+ years
                      </span>
                      <span className="flex items-center">
                        <Award className="w-4 h-4 mr-1" />
                        {job.seniorityLevel}
                      </span>
                    </div>
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
        )}
      </div>
    </div>
  );
};

export default JobMatchingDashboard;