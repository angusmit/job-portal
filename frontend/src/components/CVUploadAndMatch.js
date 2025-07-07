import { useState } from 'react';

const CVUploadAndMatch = () => {
    const [file, setFile] = useState(null);
    const [uploading, setUploading] = useState(false);
    const [matching, setMatching] = useState(false);
    const [cvData, setCvData] = useState(null);
    const [matches, setMatches] = useState([]);
    const [error, setError] = useState('');
    const [sessionId] = useState(`session-${Date.now()}`);

    const API_BASE = 'http://localhost:8080/api/ml';

    const handleFileChange = (e) => {
        const selectedFile = e.target.files[0];
        if (selectedFile && (selectedFile.type === 'application/pdf' ||
            selectedFile.type === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document')) {
            setFile(selectedFile);
            setError('');
        } else {
            setError('Please select a PDF or DOCX file');
        }
    };

    const uploadCV = async () => {
        if (!file) {
            setError('Please select a file first');
            return;
        }

        setUploading(true);
        setError('');

        const formData = new FormData();
        formData.append('file', file);
        formData.append('sessionId', sessionId);

        try {
            const response = await fetch(`${API_BASE}/upload-cv`, {
                method: 'POST',
                body: formData,
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            setCvData(data);
            setError('');
        } catch (err) {
            setError(err.message || 'Failed to upload CV');
        } finally {
            setUploading(false);
        }
    };

    const matchJobs = async (mode = 'graduate_friendly') => {
        if (!cvData?.member_id) {
            setError('Please upload a CV first');
            return;
        }

        setMatching(true);
        setError('');

        try {
            const response = await fetch(`${API_BASE}/match-jobs`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    session_id: sessionId,
                    member_id: cvData.member_id,
                    mode: mode,
                    top_k: 10
                }),
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            setMatches(data.matches || []);
        } catch (err) {
            setError(err.message || 'Failed to match jobs');
        } finally {
            setMatching(false);
        }
    };

    // Direct ML Service Test (bypassing Spring Boot)
    const testDirectMLService = async () => {
        if (!file) {
            setError('Please select a file first');
            return;
        }

        setUploading(true);
        setError('');

        const formData = new FormData();
        formData.append('file', file);
        formData.append('session_id', sessionId);

        try {
            // Direct call to Python ML service
            const response = await fetch('http://localhost:8000/upload_cv', {
                method: 'POST',
                body: formData,
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            setCvData(data);
            setError('');

            // Automatically match jobs after upload
            setTimeout(() => matchJobsDirect(data.member_id), 1000);
        } catch (err) {
            setError('Failed to connect to ML service. Make sure it\'s running on port 8000');
        } finally {
            setUploading(false);
        }
    };

    const matchJobsDirect = async (memberId, mode = 'graduate_friendly') => {
        setMatching(true);
        try {
            const response = await fetch('http://localhost:8000/match_jobs', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    session_id: sessionId,
                    member_id: memberId,
                    mode: mode,
                    top_k: 10
                }),
            });

            const data = await response.json();
            setMatches(data.matches || []);
        } catch (err) {
            setError('Failed to match jobs');
        } finally {
            setMatching(false);
        }
    };

    return (
        <div className="max-w-4xl mx-auto p-6 space-y-6">
            {/* Test Mode Toggle */}
            <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-4">
                <h3 className="font-semibold text-yellow-800 mb-2">Testing Mode</h3>
                <p className="text-sm text-yellow-700 mb-3">
                    If you're getting authentication errors, use the direct ML service test below:
                </p>
                <button
                    onClick={testDirectMLService}
                    disabled={!file || uploading}
                    className="px-4 py-2 bg-yellow-600 text-white rounded hover:bg-yellow-700 disabled:bg-gray-400"
                >
                    Test Direct ML Service (Port 8000)
                </button>
            </div>

            {/* Upload Section */}
            <div className="bg-white rounded-lg shadow-lg p-6">
                <h2 className="text-2xl font-bold mb-4">Upload Your Resume</h2>

                <div className="space-y-4">
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-2">
                            Select CV/Resume (PDF or DOCX)
                        </label>
                        <input
                            type="file"
                            accept=".pdf,.docx"
                            onChange={handleFileChange}
                            className="block w-full text-sm text-gray-500
                file:mr-4 file:py-2 file:px-4
                file:rounded-full file:border-0
                file:text-sm file:font-semibold
                file:bg-blue-50 file:text-blue-700
                hover:file:bg-blue-100"
                        />
                    </div>

                    <button
                        onClick={uploadCV}
                        disabled={!file || uploading}
                        className="w-full py-2 px-4 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:bg-gray-400 transition-colors"
                    >
                        {uploading ? 'Uploading...' : 'Upload CV (via Spring Boot)'}
                    </button>

                    {error && (
                        <div className="p-3 bg-red-100 text-red-700 rounded-md">
                            {error}
                        </div>
                    )}
                </div>

                {/* CV Analysis Results */}
                {cvData && (
                    <div className="mt-6 p-4 bg-gray-50 rounded-md">
                        <h3 className="font-semibold mb-2">CV Analysis Results:</h3>
                        <div className="space-y-1 text-sm">
                            <p><span className="font-medium">Member ID:</span> {cvData.member_id}</p>
                            <p><span className="font-medium">Skills:</span> {cvData.skills?.join(', ')}</p>
                            <p><span className="font-medium">Experience:</span> {cvData.experience_years} years</p>
                            <p><span className="font-medium">Seniority:</span> {cvData.seniority_level}</p>
                            {cvData.title && <p><span className="font-medium">Title:</span> {cvData.title}</p>}
                        </div>
                    </div>
                )}
            </div>

            {/* Job Matching Section */}
            {cvData && (
                <div className="bg-white rounded-lg shadow-lg p-6">
                    <h2 className="text-2xl font-bold mb-4">AI-Powered Job Matching</h2>

                    <div className="flex gap-2 mb-4">
                        <button
                            onClick={() => matchJobs('strict')}
                            disabled={matching}
                            className="px-4 py-2 bg-green-600 text-white rounded-md hover:bg-green-700 disabled:bg-gray-400"
                        >
                            Strict Match
                        </button>
                        <button
                            onClick={() => matchJobs('flexible')}
                            disabled={matching}
                            className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:bg-gray-400"
                        >
                            Flexible Match
                        </button>
                        <button
                            onClick={() => matchJobs('graduate_friendly')}
                            disabled={matching}
                            className="px-4 py-2 bg-purple-600 text-white rounded-md hover:bg-purple-700 disabled:bg-gray-400"
                        >
                            Graduate Friendly
                        </button>
                    </div>

                    {matching && (
                        <div className="text-center py-4">
                            <div className="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
                            <p className="mt-2">Finding best matches...</p>
                        </div>
                    )}

                    {/* Job Matches */}
                    {matches.length > 0 && (
                        <div className="space-y-4 mt-6">
                            <h3 className="font-semibold text-lg">Top Job Matches:</h3>
                            {matches.map((match, index) => (
                                <div key={match.job_id || index} className="border rounded-lg p-4 hover:shadow-md transition-shadow">
                                    <div className="flex justify-between items-start">
                                        <div className="flex-1">
                                            <h4 className="font-semibold text-lg">{match.title}</h4>
                                            <p className="text-gray-600">{match.company}</p>
                                            <p className="text-sm text-gray-500 mt-1">
                                                {match.location} • {match.seniority_level} • {match.experience_required} years
                                            </p>
                                            <div className="mt-2">
                                                <span className="text-sm font-medium">Required Skills: </span>
                                                <span className="text-sm text-gray-600">
                                                    {match.required_skills?.join(', ')}
                                                </span>
                                            </div>
                                        </div>
                                        <div className="ml-4 text-right">
                                            <div className="text-2xl font-bold text-green-600">
                                                {(match.score * 100).toFixed(0)}%
                                            </div>
                                            <div className="text-sm text-gray-500">Match Score</div>
                                        </div>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            )}

            {/* Privacy Notice */}
            <div className="bg-blue-50 rounded-lg p-4 text-sm text-blue-800">
                <p className="font-semibold mb-1">🔒 Your Privacy is Protected</p>
                <p>Your CV is only stored temporarily during this session and will be automatically deleted when you close the browser.</p>
            </div>
        </div>
    );
};

export default CVUploadAndMatch;