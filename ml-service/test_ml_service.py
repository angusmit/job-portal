import requests
import json
from pathlib import Path
import io

# Configuration
ML_SERVICE_URL = "http://localhost:8000"
SESSION_ID = "test-session-123"

def test_cv_upload(cv_file_path):
    """Test CV upload endpoint"""
    print(f"Testing CV upload with file: {cv_file_path}")
    
    with open(cv_file_path, 'rb') as f:
        files = {'file': (cv_file_path, f, 'application/pdf')}
        data = {'session_id': SESSION_ID}
        
        response = requests.post(
            f"{ML_SERVICE_URL}/upload_cv",
            files=files,
            data=data
        )
    
    if response.status_code == 200:
        result = response.json()
        print("CV uploaded successfully.")
        print(f"Member ID: {result['member_id']}")
        print(f"Skills extracted: {result['skills']}")
        print(f"Experience: {result['experience_years']} years")
        print(f"Seniority: {result['seniority_level']}")
        return result['member_id']
    else:
        print(f"Upload failed: {response.text}")
        return None

def test_job_matching(member_id, mode="graduate_friendly"):
    """Test job matching endpoint"""
    print(f"\nTesting job matching for member: {member_id}")
    
    payload = {
        "session_id": SESSION_ID,
        "member_id": member_id,
        "mode": mode,
        "top_k": 10
    }
    
    response = requests.post(
        f"{ML_SERVICE_URL}/match_jobs",
        json=payload
    )
    
    if response.status_code == 200:
        result = response.json()
        print(f"Found {result['total_matches']} job matches.")
        
        for i, match in enumerate(result['matches'][:5], 1):
            print(f"\n{i}. {match['title']} at {match['company']}")
            print(f"   Score: {match['match_score']:.3f}")
            print(f"   Seniority: {match['seniority_level']}")
            print(f"   Location: {match['location']}")
    else:
        print(f"Matching failed: {response.text}")

def test_parse_cv_direct():
    """Test direct CV parsing endpoint"""
    print("\nTesting direct CV parsing...")
    
    sample_cv = """
    John Doe
    Senior Software Engineer

    Education:
    BS Computer Science, Stanford University (2019)

    Skills:
    - Python, Java, JavaScript, React, Node.js
    - Machine Learning, Docker, Kubernetes, AWS
    - Git, CI/CD, Agile, System Design

    Experience:
    Software Engineer - Google (2019-2024)
    - Developed scalable microservices using Python and Go
    - Led team of 5 engineers on customer-facing features
    - Implemented ML models for recommendation system

    Projects:
    - Built distributed cache system handling 1M QPS
    - Created real-time data pipeline using Kafka and Spark
    """
    
    payload = {
        "session_id": SESSION_ID,
        "cv_text": sample_cv
    }
    
    response = requests.post(
        f"{ML_SERVICE_URL}/parse_cv_direct",
        json=payload
    )
    
    if response.status_code == 200:
        result = response.json()
        print("CV parsed successfully.")
        print(f"Member ID: {result['member_id']}")
        print(f"Skills: {result['skills']}")
        print(f"Experience: {result['experience_years']} years")
        print(f"Seniority: {result['seniority_level']}")
        return result['member_id']
    else:
        print(f"Direct parsing failed: {response.text}")
        return None

def add_sample_jobs():
    """Add sample jobs to the system for testing"""
    print("\nAdding sample jobs to the system...")
    
    sample_jobs = [
        {
            "job_id": "test-j1",
            "title": "Graduate Software Engineer",
            "description": "Perfect for new CS graduates. Join our 2024 graduate program!",
            "company": "TechCorp",
            "required_skills": ["python", "javascript", "git"],
            "preferred_skills": ["react", "docker"],
            "experience_required": 0,
            "location": "San Francisco",
            "seniority_level": "entry"
        },
        {
            "job_id": "test-j2",
            "title": "Junior Full Stack Developer",
            "description": "0-2 years experience. Work with React and Node.js.",
            "company": "StartupXYZ",
            "required_skills": ["javascript", "react", "node.js"],
            "preferred_skills": ["typescript", "aws"],
            "experience_required": 1,
            "location": "Remote",
            "seniority_level": "junior"
        },
        {
            "job_id": "test-j3",
            "title": "Python Developer",
            "description": "Backend development with Python and Django.",
            "company": "DataCo",
            "required_skills": ["python", "django", "sql"],
            "preferred_skills": ["docker", "redis"],
            "experience_required": 2,
            "location": "New York",
            "seniority_level": "junior"
        },
        {
            "job_id": "test-j4",
            "title": "Senior Software Engineer",
            "description": "Lead our backend team. 5+ years experience required.",
            "company": "MegaCorp",
            "required_skills": ["python", "system-design", "leadership"],
            "preferred_skills": ["kubernetes", "aws"],
            "experience_required": 5,
            "location": "San Francisco",
            "seniority_level": "senior"
        },
        {
            "job_id": "test-j5",
            "title": "Machine Learning Engineer",
            "description": "Build and deploy ML models at scale.",
            "company": "AI Startup",
            "required_skills": ["python", "machine-learning", "tensorflow"],
            "preferred_skills": ["kubernetes", "mlflow"],
            "experience_required": 3,
            "location": "Remote",
            "seniority_level": "mid"
        }
    ]
    
    success_count = 0
    for job in sample_jobs:
        response = requests.post(
            f"{ML_SERVICE_URL}/add_job",
            json=job
        )
        if response.status_code == 200:
            print(f"Added job: {job['title']}")
            success_count += 1
        else:
            print(f"Failed to add job: {job['title']} - {response.text}")
    
    return success_count

def create_test_cv_file():
    """Create a test PDF file if it doesn't exist"""
    test_file = "test_cv.pdf"
    if not Path(test_file).exists():
        print(f"Creating test CV file: {test_file}")
        try:
            from reportlab.lib.pagesizes import letter
            from reportlab.pdfgen import canvas
            
            c = canvas.Canvas(test_file, pagesize=letter)
            c.drawString(100, 750, "John Doe")
            c.drawString(100, 730, "Software Engineer")
            c.drawString(100, 700, "Education: BS Computer Science (2024)")
            c.drawString(100, 670, "Skills: Python, JavaScript, React, Node.js, Git")
            c.drawString(100, 640, "Experience: Software Engineering Intern at TechCorp")
            c.drawString(100, 610, "Projects: Built ML sentiment analysis model")
            c.save()
            print("Test CV file created.")
            return test_file
        except ImportError:
            print("reportlab not installed. Using direct CV parsing instead.")
            return None
    return test_file

if __name__ == "__main__":
    print("="*60)
    print("Testing ML Service Independently")
    print("="*60)
    
    jobs_added = add_sample_jobs()
    
    if jobs_added > 0:
        cv_file = "Simple_CV_John_Doe.pdf"
        if not Path(cv_file).exists():
            cv_file = create_test_cv_file()
        
        member_id = None
        if cv_file and Path(cv_file).exists():
            member_id = test_cv_upload(cv_file)
        
        if not member_id:
            print("\nFile upload failed or file not found. Trying direct CV parsing...")
            member_id = test_parse_cv_direct()
        
        if member_id:
            print("\n" + "="*60)
            print("Testing Different Matching Modes")
            print("="*60)
            
            for mode in ["strict", "flexible", "graduate_friendly"]:
                print(f"\n--- Mode: {mode} ---")
                test_job_matching(member_id, mode)
        else:
            print("\nCould not create member profile for testing.")
    else:
        print("\nNo jobs added to the system. Cannot proceed with matching.")
