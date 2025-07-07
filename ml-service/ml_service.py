# ml_service.py - FastAPI wrapper for your GNN job matching system

from fastapi import FastAPI, File, UploadFile, HTTPException, Form
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Optional, Dict
import torch
import redis
import json
import hashlib
from datetime import datetime, timedelta
import asyncio
import uvicorn
import io
import requests 
# Update the request model for parse_cv_direct
from pydantic import BaseModel

# Import your existing GNN modules
from improved_cv_matcher import ImprovedJobMatcher, Member, Job
from graduate_friendly_matcher import get_recommendations_graduate_friendly

# Resume parser imports
import pdfplumber
import docx
import re
from sentence_transformers import SentenceTransformer

app = FastAPI(title="Job Matching ML Service")

# CORS configuration
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000", "http://localhost:8080"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Initialize Redis
redis_client = redis.Redis(host='localhost', port=6379, decode_responses=True)

# Initialize models
matcher = ImprovedJobMatcher()
sentence_model = SentenceTransformer('all-MiniLM-L6-v2')

# Request/Response Models
class CVParseRequest(BaseModel):
    session_id: str
    file_content: str  # Base64 encoded
    file_type: str  # pdf, docx, txt

class ParseCVDirectRequest(BaseModel):
    session_id: str
    cv_text: str

class CVParseResponse(BaseModel):
    member_id: str
    extracted_text: str
    skills: List[str]
    experience_years: int
    seniority_level: str
    title: Optional[str]
    location: Optional[str]

class JobMatchRequest(BaseModel):
    session_id: str
    member_id: str
    mode: str = "graduate_friendly"  # strict, flexible, graduate_friendly, experience_based
    top_k: int = 10

class JobMatchResponse(BaseModel):
    matches: List[Dict]
    total_matches: int

class JobData(BaseModel):
    job_id: str
    title: str
    description: str
    company: str
    required_skills: List[str]
    preferred_skills: List[str] = []
    experience_required: int
    location: str
    seniority_level: str

# CV Parser Functions
def extract_text_from_pdf(file_content: io.BytesIO) -> str:
    """Extract text from PDF using BytesIO object"""
    text = ""
    try:
        with pdfplumber.open(file_content) as pdf:
            for page in pdf.pages:
                page_text = page.extract_text()
                if page_text:
                    text += page_text + "\n"
    except Exception as e:
        print(f"Error extracting text from PDF: {e}")
        raise
    return text.strip()

def extract_text_from_docx(file_content: io.BytesIO) -> str:
    """Extract text from DOCX using BytesIO object"""
    try:
        doc = docx.Document(file_content)
        return "\n".join([para.text for para in doc.paragraphs if para.text.strip()])
    except Exception as e:
        print(f"Error extracting text from DOCX: {e}")
        raise

def extract_skills(text: str) -> List[str]:
    """Extract skills from CV text using pattern matching and NLP"""
    # Common tech skills patterns
    skill_patterns = [
        r'\b(python|java|javascript|react|angular|vue|node\.?js|spring|django|flask)\b',
        r'\b(docker|kubernetes|aws|azure|gcp|git|jenkins|ci/cd)\b',
        r'\b(mysql|postgresql|mongodb|redis|elasticsearch)\b',
        r'\b(machine learning|deep learning|nlp|computer vision)\b',
        r'\b(html|css|sass|typescript|graphql|rest api)\b',
    ]
    
    skills = set()
    text_lower = text.lower()
    
    for pattern in skill_patterns:
        matches = re.findall(pattern, text_lower)
        skills.update(matches)
    
    # Clean up skills
    skills = [skill.replace('.js', 'js').strip() for skill in skills]
    return list(skills)

def extract_experience_years(text: str) -> int:
    """Extract years of experience from CV text"""
    patterns = [
        r'(\d+)\+?\s*years?\s*(?:of\s*)?experience',
        r'experience:\s*(\d+)\+?\s*years?',
        r'(\d+)\s*years?\s*working',
    ]
    
    for pattern in patterns:
        match = re.search(pattern, text.lower())
        if match:
            return int(match.group(1))
    
    # Check for new grad indicators
    if any(term in text.lower() for term in ['recent graduate', 'fresh graduate', 'graduating']):
        return 0
    
    return 1  # Default to 1 year if not found

def determine_seniority_level(text: str, experience_years: int) -> str:
    """Determine seniority level from CV text and experience"""
    text_lower = text.lower()
       
    # Based on experience
    if experience_years >= 5:
        return "senior"
    elif experience_years >= 3:
        return "mid"
    elif experience_years >= 1:
        return "junior"     
    # Check for explicit mentions
    if any(term in text_lower for term in ['senior', 'lead', 'principal', 'staff']):
        return "senior"
    elif any(term in text_lower for term in ['junior', 'entry level', 'graduate']):
        return "entry"
    
    else:
        return "entry"

def extract_title(text: str) -> Optional[str]:
    """Extract job title from CV"""
    # Look for common patterns
    patterns = [
        r'(?:current\s*position|role|title):\s*([^\n]+)',
        r'(?:^|\n)([A-Za-z\s]+(?:Developer|Engineer|Analyst|Scientist|Designer|Manager))',
    ]
    
    for pattern in patterns:
        match = re.search(pattern, text, re.IGNORECASE)
        if match:
            return match.group(1).strip()
    
    return None

def extract_skills_from_description(description: str) -> List[str]:
    """Extract required skills from job description text"""
    return extract_skills(description)

def fetch_jobs_from_spring_boot():
    """Fetch approved jobs from Spring Boot backend"""
    try:
        # Call Spring Boot API to get all approved jobs
        response = requests.get('http://localhost:8080/api/jobs', 
                              headers={'Accept': 'application/json'})
        
        if response.status_code == 200:
            jobs_data = response.json()
            jobs = []
            
            for job_data in jobs_data:
                # Extract skills from description and requirements
                all_text = f"{job_data.get('description', '')} {job_data.get('requirements', '')}"
                skills = extract_skills(all_text)
                
                # Determine experience years from requirements or default
                experience_years = extract_experience_years(job_data.get('requirements', ''))
                if experience_years == 0 and 'senior' in job_data.get('title', '').lower():
                    experience_years = 5
                elif experience_years == 0 and 'mid' in job_data.get('title', '').lower():
                    experience_years = 3
                
                # Determine seniority level
                seniority = determine_seniority_level(all_text, experience_years)
                
                # Create Job object with Spring Boot ID
                job = Job(
                    job_id=str(job_data['id']),  # Use the actual Spring Boot ID
                    title=job_data['title'],
                    description=job_data['description'],
                    company=job_data['company'],
                    required_skills=skills,
                    preferred_skills=[],
                    experience_required=experience_years,
                    location=job_data['location'],
                    seniority_level=seniority
                )
                jobs.append(job)
                
            return jobs
        else:
            print(f"Failed to fetch jobs from Spring Boot: {response.status_code}")
            return []
            
    except Exception as e:
        print(f"Error fetching jobs from Spring Boot: {e}")
        return []
    

# API Endpoints
@app.post("/upload_cv")
async def upload_cv(
    file: UploadFile = File(...),
    session_id: str = Form(...)
):
    """Upload and parse CV file"""
    try:
        # Read file content
        file_content = await file.read()
        
        # Determine file type and extract text
        if file.filename.lower().endswith('.pdf'):
            # Create a BytesIO object from the content
            pdf_file = io.BytesIO(file_content)
            text = extract_text_from_pdf(pdf_file)
        elif file.filename.lower().endswith('.docx'):
            # Create a BytesIO object from the content
            docx_file = io.BytesIO(file_content)
            text = extract_text_from_docx(docx_file)
        else:
            raise HTTPException(status_code=400, detail="Unsupported file format")
        
        # Extract information from CV
        skills = extract_skills(text)
        experience_years = extract_experience_years(text)
        seniority_level = determine_seniority_level(text, experience_years)
        title = extract_title(text)
        
        # Generate member ID
        member_id = f"m_{hashlib.md5(file_content).hexdigest()[:8]}"
        
        # Store in Redis with expiration
        redis_key = f"cv:{session_id}:{member_id}"
        cv_data = {
            "cv_text": text,
            "skills": skills,
            "experience_years": experience_years,
            "seniority_level": seniority_level,
            "title": title,
            "uploaded_at": datetime.now().isoformat()
        }
        
        redis_client.setex(
            redis_key,
            timedelta(hours=1),  # Expire after 1 hour
            json.dumps(cv_data)
        )
        
        # Create member object and add to graph
        member = Member(
            member_id=member_id,
            cv_text=text,
            skills=skills,
            title=title,
            experience_years=experience_years,
            seniority_level=seniority_level,
            location="Remote"
        )
        
        matcher.graph.add_member_node(member)
        
        return CVParseResponse(
            member_id=member_id,
            extracted_text=text[:500] + "..." if len(text) > 500 else text,
            skills=skills,
            experience_years=experience_years,
            seniority_level=seniority_level,
            title=title,
            location="Remote"
        )
    
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))
    
@app.post("/parse_cv_direct", response_model=CVParseResponse)
async def parse_cv_direct(
    session_id: str,
    cv_text: str
):
    """Parse CV text directly without file upload"""
    try:
        # Extract information from CV text
        skills = extract_skills(cv_text)
        experience_years = extract_experience_years(cv_text)
        seniority_level = determine_seniority_level(cv_text, experience_years)
        title = extract_title(cv_text)
        
        # Generate member ID
        member_id = f"m_{hashlib.md5(cv_text.encode()).hexdigest()[:8]}"
        
        # Store in Redis
        redis_key = f"cv:{session_id}:{member_id}"
        cv_data = {
            "cv_text": cv_text,
            "skills": skills,
            "experience_years": experience_years,
            "seniority_level": seniority_level,
            "title": title,
            "uploaded_at": datetime.now().isoformat()
        }
        
        redis_client.setex(
            redis_key,
            timedelta(hours=1),
            json.dumps(cv_data)
        )
        
        # Create member object and add to graph
        member = Member(
            member_id=member_id,
            cv_text=cv_text,
            skills=skills,
            title=title,
            experience_years=experience_years,
            seniority_level=seniority_level,
            location="Remote"
        )
        
        matcher.graph.add_member_node(member)
        
        return CVParseResponse(
            member_id=member_id,
            extracted_text=cv_text[:500] + "..." if len(cv_text) > 500 else cv_text,
            skills=skills,
            experience_years=experience_years,
            seniority_level=seniority_level,
            title=title,
            location="Remote"
        )
    
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))
    
@app.post("/match_jobs", response_model=JobMatchResponse)
async def match_jobs(request: JobMatchRequest):
    """Get job matches for a parsed CV"""
    try:
        # Check if we have any jobs loaded
        job_count = len(matcher.graph.nodes.get('job', {}))
        
        # If no jobs, try to sync from Spring Boot
        if job_count == 0:
            print("No jobs in graph, syncing from Spring Boot...")
            spring_jobs = fetch_jobs_from_spring_boot()
            for job in spring_jobs:
                matcher.graph.add_job_node(job)
        
        # Retrieve CV data from Redis
        redis_key = f"cv:{request.session_id}:{request.member_id}"
        cv_data = redis_client.get(redis_key)
        
        if not cv_data:
            raise HTTPException(status_code=404, detail="CV not found in session")
        
        cv_info = json.loads(cv_data)
        
        # Create Member object
        member = Member(
            member_id=request.member_id,
            cv_text=cv_info["cv_text"],
            skills=cv_info["skills"],
            title=cv_info.get("title"),
            experience_years=cv_info["experience_years"],
            seniority_level=cv_info["seniority_level"],
            location="Remote"
        )
        
        # Add member to matcher graph temporarily
        matcher.graph.add_member_node(member)
        
        # Get recommendations based on mode
        if request.mode == "strict":
            recommendations = matcher.get_recommendations(
                request.member_id,
                top_k=request.top_k
            )
        else:
            # Add the graduate-friendly method to matcher
            matcher.get_recommendations_graduate_friendly = \
                get_recommendations_graduate_friendly.__get__(matcher, ImprovedJobMatcher)
            
            recommendations = matcher.get_recommendations_graduate_friendly(
                request.member_id,
                top_k=request.top_k,
                mode=request.mode
            )
        
        # Format response with actual Spring Boot job IDs
        matches = []
        for rec in recommendations:
            job = matcher.graph.nodes['job'].get(rec['job_id'])
            if job:
                matches.append({
                    "job_id": job.job_id,  # This will be the Spring Boot ID
                    "title": job.title,
                    "company": job.company,
                    "location": job.location,
                    "required_skills": job.required_skills,
                    "preferred_skills": job.preferred_skills,
                    "experience_required": job.experience_required,
                    "seniority_level": job.seniority_level,
                    "score": rec['score']
                })
        
        return JobMatchResponse(
            matches=matches,
            total_matches=len(matches)
        )
    
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

def get_match_quality(score: float) -> str:
    """Determine match quality based on score"""
    if score > 0.85:
        return "EXCELLENT"
    elif score > 0.70:
        return "GOOD"
    elif score > 0.55:
        return "FAIR"
    else:
        return "WEAK"

@app.post("/add_job")
async def add_job(job: JobData):
    """Add a new job to the matching system"""
    try:
        new_job = Job(
            job_id=job.job_id,
            title=job.title,
            description=job.description,
            company=job.company,
            required_skills=job.required_skills,
            preferred_skills=job.preferred_skills,
            experience_required=job.experience_required,
            location=job.location,
            seniority_level=job.seniority_level
        )
        
        matcher.graph.add_job_node(new_job)
        
        return {"status": "success", "job_id": job.job_id}
    
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/train_model")
async def train_model():
    """Retrain the GNN model with current data"""
    try:
        # Get all members and jobs from graph
        members = list(matcher.graph.nodes['member'].values())
        jobs = list(matcher.graph.nodes['job'].values())
        
        if len(members) < 5 or len(jobs) < 5:
            return {"status": "skipped", "message": "Not enough data for training"}
        
        # Train in background
        asyncio.create_task(train_model_async(members, jobs))
        
        return {"status": "training_started"}
    
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

async def train_model_async(members, jobs):
    """Async model training"""
    matcher.train(members, jobs, epochs=20, batch_size=8)
    # Save model
    torch.save({
        'model_state_dict': matcher.model.state_dict(),
        'graph_data': matcher.graph.to_pyg_data()
    }, 'job_matcher_model.pth')

# ------------------------
# Sync jobs from Spring Boot
# ------------------------
@app.post("/sync_jobs_from_spring")
async def sync_jobs_from_spring():
    """Fetch jobs from Spring Boot and add to ML service"""
    try:
        # Call Spring Boot to get all jobs
        response = requests.get('http://localhost:8080/api/jobs')
        response.raise_for_status()
        jobs = response.json()
        
        # Clear existing jobs
        matcher.graph.nodes['job'].clear()
        
        # Add real jobs
        for job_data in jobs:
            job = Job(
                job_id=str(job_data['id']),
                title=job_data['title'],
                description=job_data['description'],
                company=job_data['company'],
                required_skills=extract_skills_from_description(job_data['description']),
                preferred_skills=[],
                experience_required=job_data.get('experienceRequired', 2),
                location=job_data['location'],
                seniority_level=determine_seniority(job_data.get('experienceRequired', 2))
            )
            matcher.graph.add_job_node(job)
        
        return {"status": "success", "jobs_synced": len(jobs)}
    except Exception as e:
        return {"status": "error", "message": str(e)}
    
    # Add endpoint to check loaded jobs
@app.get("/loaded_jobs")
async def get_loaded_jobs():
    """Get list of currently loaded jobs"""
    jobs = []
    for job_id, job in matcher.graph.nodes.get('job', {}).items():
        jobs.append({
            "job_id": job.job_id,
            "title": job.title,
            "company": job.company,
            "location": job.location,
            "skills": job.required_skills
        })
    
    return {
        "total": len(jobs),
        "jobs": jobs
    }

@app.post("/sync_jobs")
async def sync_jobs():
    """Manually sync jobs from Spring Boot"""
    try:
        # Clear existing jobs
        if 'job' in matcher.graph.nodes:
            matcher.graph.nodes['job'].clear()
        
        # Fetch fresh jobs
        spring_jobs = fetch_jobs_from_spring_boot()
        
        if spring_jobs:
            for job in spring_jobs:
                matcher.graph.add_job_node(job)
            
            return {
                "status": "success",
                "message": f"Synced {len(spring_jobs)} jobs from Spring Boot",
                "jobs": [{"id": job.job_id, "title": job.title} for job in spring_jobs]
            }
        else:
            return {
                "status": "warning",
                "message": "No jobs found in Spring Boot",
                "jobs": []
            }
            
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.on_event("startup")
async def startup_event():
    """Load saved model and fetch jobs from Spring Boot on startup"""
    try:
        # Load saved model if exists
        checkpoint = torch.load('job_matcher_model.pth', map_location=matcher.device)
        matcher.model.load_state_dict(checkpoint['model_state_dict'])
        print("Loaded saved model")
    except:
        print("No saved model found, starting fresh")
    
    # Fetch and load jobs from Spring Boot
    print("Fetching jobs from Spring Boot...")
    spring_jobs = fetch_jobs_from_spring_boot()
    
    if spring_jobs:
        print(f"Loading {len(spring_jobs)} jobs from Spring Boot")
        for job in spring_jobs:
            matcher.graph.add_job_node(job)
            print(f"Added job: {job.title} (ID: {job.job_id})")
    else:
        print("No jobs fetched from Spring Boot")

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)