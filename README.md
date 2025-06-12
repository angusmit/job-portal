# JobPortal - Full Stack Job Seeking Platform

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18.0-blue.svg)](https://reactjs.org/)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A modern job portal application built with Spring Boot and React, featuring role-based authentication, job management, and administrative controls.

## 🚀 Features

### User Management
- **Three User Roles**: Job Seeker, Employer, and Admin
- JWT-based authentication with secure token management
- User registration with role-specific fields
- Secure password encryption using BCrypt

### Job Seeker Features
- Browse all approved job listings
- Search jobs by title, company, or keywords
- Filter jobs by location and job type
- View detailed job descriptions
- Save jobs for later (backend ready)
- Apply for jobs (coming soon)

### Employer Features
- Post new job listings
- Manage posted jobs (edit/delete)
- View applications (coming soon)
- Company profile management
- Job posts require admin approval before going live

### Admin Features
- Full access to all platform features
- Approve/reject job postings before they appear on the main board
- User management capabilities
- Content moderation
- Platform statistics (coming soon)

### Security Features
- JWT token-based authentication
- Role-based access control (RBAC)
- Protected API endpoints
- Automatic token refresh handling
- Secure password policies

## 🛠️ Tech Stack

### Backend
- **Java 21**
- **Spring Boot 3.2.0**
- **Spring Security** - Authentication and authorization
- **Spring Data JPA** - Database ORM
- **H2 Database** - In-memory database for development
- **JWT (JSON Web Tokens)** - Secure authentication
- **Lombok** - Reduce boilerplate code
- **Maven** - Dependency management

### Frontend
- **React 18**
- **React Router v6** - Client-side routing
- **Axios** - HTTP client with interceptors
- **Context API** - State management
- **CSS3** - Styling

## 📋 Prerequisites

- Java JDK 21 or higher
- Node.js 16 or higher
- Maven 3.8+
- Git

## 🔧 Installation & Setup

### Clone the repository
```bash
git clone https://github.com/yourusername/job-portal.git
cd job-portal
```

### Backend Setup
```bash
cd backend
mvn clean install
mvn spring-boot:run
```
The backend will start on http://localhost:8080

### Frontend Setup
```bash
cd frontend
npm install
npm start
```
The frontend will start on http://localhost:3000

## 📁 Project Structure

```
job-portal/
├── backend/
│   └── src/main/java/com/example/jobportal/
│       ├── config/         # Security and app configuration
│       ├── controller/     # REST API endpoints
│       ├── dto/           # Data Transfer Objects
│       ├── model/         # Entity models
│       ├── repository/    # Data access layer
│       └── security/      # JWT and authentication
├── frontend/
│   └── src/
│       ├── components/    # React components
│       ├── context/       # Authentication context
│       ├── services/      # API services
│       └── App.js         # Main application component
└── README.md
```

## 🔑 Default User Credentials

The application comes with pre-seeded user accounts for testing:

| Role | Username | Password | Email |
|------|----------|----------|-------|
| Admin | admin | admin123 | admin@jobportal.com |
| Employer | techcorp | password123 | hr@techcorp.com |
| Employer | webinc | password123 | hr@webinc.com |
| Job Seeker | johndoe | password123 | john@example.com |
| Job Seeker | janesmith | password123 | jane@example.com |

## 📡 API Endpoints

### Authentication
- `POST /api/auth/signin` - User login
- `POST /api/auth/signup` - User registration
- `POST /api/auth/signout` - User logout
- `GET /api/auth/user` - Get current user

### Jobs (Public)
- `GET /api/jobs` - Get all approved jobs
- `GET /api/jobs/{id}` - Get job details
- `GET /api/jobs/search?query={query}` - Search jobs
- `GET /api/jobs/filter?location={location}&jobType={type}` - Filter jobs

### Jobs (Protected)
- `POST /api/jobs` - Create new job (Employer only)
- `PUT /api/jobs/{id}` - Update job (Job owner only)
- `DELETE /api/jobs/{id}` - Delete job (Job owner or Admin)
- `GET /api/jobs/my-jobs` - Get employer's posted jobs

### Admin (Protected)
- `GET /api/admin/pending-jobs` - Get jobs pending approval
- `PUT /api/admin/jobs/{id}/approve` - Approve job posting
- `PUT /api/admin/jobs/{id}/reject` - Reject job posting

## 🔐 Security Implementation

### JWT Configuration
- Tokens expire after 24 hours
- HS256 algorithm for token signing
- Secure key storage in application properties

### Role-Based Access
- **Job Seekers**: Can browse and save jobs
- **Employers**: Can post jobs (with admin approval) and manage their listings
- **Admins**: Full platform access and content moderation

## 🌟 Key Features Implementation Details

### Job Approval Workflow
1. Employer creates a job posting
2. Job is saved with `pending` status
3. Admin reviews pending jobs
4. Admin approves/rejects the posting
5. Approved jobs appear on the main job board

### Authentication Flow
1. User registers with role-specific information
2. User logs in and receives JWT token
3. Token is stored in localStorage
4. All API requests include token in Authorization header
5. Backend validates token and user permissions

## 💻 How to Use

### For Job Seekers
1. Register as a job seeker
2. Browse available jobs on the homepage
3. Use search and filters to find relevant positions
4. Click on any job to view details
5. Login to save jobs or apply

### For Employers
1. Register as an employer with company details
2. Login to access employer features
3. Click "Post a Job" to create a new listing
4. Fill in job details and submit
5. Wait for admin approval
6. Once approved, your job will appear on the main board
7. Manage your posted jobs from "My Jobs" section

### For Admins
1. Login with admin credentials
2. Access admin panel to view pending job approvals
3. Review job postings and approve/reject them
4. Manage users and platform content
5. Full access to all platform features

## 🔧 Configuration

### Backend Configuration (application.properties)
```properties
# Server Configuration
server.port=8080

# Database Configuration
spring.datasource.url=jdbc:h2:mem:testdb
spring.jpa.hibernate.ddl-auto=create-drop

# JWT Configuration
app.jwtSecret=YourSecureKeyHere
app.jwtExpirationMs=86400000
```

### Environment Variables
For production, set these environment variables:
- `DATABASE_URL` - Production database URL
- `JWT_SECRET` - Secure JWT secret key
- `FRONTEND_URL` - Frontend application URL

## 🚧 Upcoming Features

- [ ] Job application system
- [ ] Resume upload and parsing
- [ ] Email notifications
- [ ] Advanced search with filters
- [ ] Employer dashboard with analytics
- [ ] Admin dashboard with statistics
- [ ] Password reset functionality
- [ ] Social media login
- [ ] Job alerts/subscriptions
- [ ] Application tracking system

## 🐛 Troubleshooting

### Common Issues

**Lombok not generating getters/setters**
- Install Lombok plugin in your IDE
- Enable annotation processing
- Clean and rebuild the project

**JWT Token errors**
- Ensure the JWT secret key is at least 64 characters for HS512
- Check token expiration time
- Clear localStorage and login again

**Database not persisting data**
- H2 is configured as in-memory database
- Data resets on application restart
- Switch to file-based H2 or PostgreSQL for persistence

**CORS errors**
- Check that frontend URL is added to CORS configuration
- Ensure credentials are included in requests

## 🛠️ Development

### Running Tests
```bash
# Backend tests
cd backend
mvn test

# Frontend tests
cd frontend
npm test
```

### Building for Production
```bash
# Backend
cd backend
mvn clean package
java -jar target/jobportal-0.0.1-SNAPSHOT.jar

# Frontend
cd frontend
npm run build
```

## 📊 Database Schema

### Users Table
- id, username, email, password, role
- firstName, lastName, phoneNumber
- Job Seeker fields: resumeSummary, skills, experience, education
- Employer fields: companyName, companyDescription, companyWebsite

### Jobs Table
- id, title, company, location, description
- jobType, salary, requirements
- postedDate, active, status (pending/approved/rejected)
- postedBy (FK to Users)

### Saved_Jobs Table (Junction)
- user_id (FK to Users)
- job_id (FK to Jobs)

## 🏗️ Architecture

### Backend Architecture
- **Controller Layer**: Handles HTTP requests and responses
- **Service Layer**: Business logic implementation
- **Repository Layer**: Data access using Spring Data JPA
- **Security Layer**: JWT authentication and authorization
- **DTO Layer**: Data transfer between layers

### Frontend Architecture
- **Components**: Reusable UI components
- **Context**: Global state management for authentication
- **Services**: API communication layer
- **Routing**: Protected and public routes

## 🚀 Deployment

### Backend Deployment (Heroku/AWS)
1. Configure production database (PostgreSQL recommended)
2. Set environment variables
3. Build JAR file: `mvn clean package`
4. Deploy using platform-specific CLI

### Frontend Deployment (Netlify/Vercel)
1. Update API base URL for production
2. Build production bundle: `npm run build`
3. Deploy build folder to hosting service

### Docker Support (Coming Soon)
```dockerfile
# Backend Dockerfile
FROM openjdk:21-jdk-slim
COPY target/*.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
```

## 🤝 Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📝 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🙏 Acknowledgments

- Spring Boot documentation
- React documentation
- JWT.io for JWT implementation guidance

## 📧 Contact

Your Name - [@angusmit](https://github.com/angusmit) - angusnshtest@gmail.com

Project Link: [https://github.com/angusmit/job-portal](https://github.com/yourusername/job-portal)