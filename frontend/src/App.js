// frontend/src/App.js
import { Navigate, Route, BrowserRouter as Router, Routes } from 'react-router-dom';
import './App.css';
import AdminDashboard from './components/AdminDashboard';
import CVScreening from './components/CVScreening';
import CVUpload from './components/CVUpload';
import EditJob from './components/EditJob';
import Header from './components/Header';
import JobDetails from './components/JobDetails';
import JobList from './components/JobList';
import JobMatchingDashboard from './components/JobMatchingDashboard.jsx';
import Login from './components/Login';
import MyJobs from './components/MyJobs';
import PostJob from './components/PostJob';
import Register from './components/Register';
import { AuthProvider, useAuth } from './context/AuthContext';
import SavedJobs from './components/SavedJobs';

// Protected Route Component
const ProtectedRoute = ({ children, allowedRoles = [] }) => {
  const { isAuthenticated, user } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" />;
  }

  if (allowedRoles.length > 0 && !allowedRoles.includes(user?.role)) {
    return <Navigate to="/" />;
  }

  return children;
};

function App() {
  return (
    <AuthProvider>
      <Router>
        <div className="App">
          <Header />
          <main className="main-content">
            <Routes>
              {/* Public Routes */}
              <Route path="/" element={<JobList />} />
              <Route path="/login" element={<Login />} />
              <Route path="/register" element={<Register />} />
              <Route path="/jobs/:id" element={<JobDetails />} />
              {/* Employer Routes */}
              <Route
                path="/post-job"
                element={
                  <ProtectedRoute allowedRoles={['EMPLOYER']}>
                    <PostJob />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/edit-job/:id"
                element={
                  <ProtectedRoute allowedRoles={['EMPLOYER']}>
                    <EditJob />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/my-jobs"
                element={
                  <ProtectedRoute allowedRoles={['EMPLOYER']}>
                    <MyJobs />
                  </ProtectedRoute>
                }
              />

              {/* Admin Routes */}
              <Route
                path="/admin"
                element={
                  <ProtectedRoute allowedRoles={['ADMIN']}>
                    <AdminDashboard />
                  </ProtectedRoute>
                }
              />

              {/* Job Seeker Routes */}
              <Route
                path="/cv-screening"
                element={
                  <ProtectedRoute allowedRoles={['JOB_SEEKER']}>
                    <CVScreening />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/cv-upload"
                element={
                  <ProtectedRoute allowedRoles={['JOB_SEEKER']}>
                    <CVUpload />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/saved-jobs"
                element={
                  <ProtectedRoute allowedRoles={['JOB_SEEKER']}>
                    <div>Saved Jobs - Coming Soon</div>
                  </ProtectedRoute>
                }
              />
              <Route
                path="/job-matches"
                element={
                  <ProtectedRoute allowedRoles={['JOB_SEEKER']}>
                    <JobMatchingDashboard />
                  </ProtectedRoute>
                }
              />

              {/* 404 Route */}
              <Route path="*" element={<Navigate to="/" />} />
            </Routes>
          </main>
        </div>
      </Router>
    </AuthProvider>
  );
}

export default App;