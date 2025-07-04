# ml_service.py - FastAPI wrapper for your GNN job matching system

from fastapi import FastAPI, File, UploadFile, HTTPException
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
def extract_text_from_pdf(file_content: bytes) -> str:
    """Extract text from PDF"""
    text = ""
    with pdfplumber.open(file_content) as pdf:
        for page in pdf.pages:
            text += page.extract_text() or ""
    return text

def extract_text_from_docx(file_content: bytes) -> str:
    """Extract text from DOCX"""
    doc = docx.Document(file_content)
    return "\n".join([para.text for para in doc.paragraphs])

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
    
    # Check for explicit mentions
    if any(term in text_lower for term in ['senior', 'lead', 'principal', 'staff']):
        return "senior"
    elif any(term in text_lower for term in ['junior', 'entry level', 'graduate']):
        return "entry" if experience_years == 0 else "junior"
    
    # Based on experience
    if experience_years >= 5:
        return "senior"
    elif experience_years >= 3:
        return "mid"
    elif experience_years >= 1:
        return "junior"
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

# API Endpoints
@app.post("/parse_cv", response_model=CVParseResponse)
async def parse_cv(file: UploadFile = File(...), session_id: str = None):
    """Parse uploaded CV and extract relevant information"""
    try:
        # Read file content
        content = await file.read()
        
        # Extract text based on file type
        if file.filename.endswith('.pdf'):
            text = extract_text_from_pdf(content)
        elif file.filename.endswith('.docx'):
            text = extract_text_from_docx(content)
        else:
            text = content.decode('utf-8')
        
        # Extract information
        skills = extract_skills(text)
        experience_years = extract_experience_years(text)
        seniority_level = determine_seniority_level(text, experience_years)
        title = extract_title(text)
        
        # Generate member ID
        member_id = f"session_{session_id}_{hashlib.md5(text.encode()).hexdigest()[:8]}"
        
        # Create Member object
        member = Member(
            member_id=member_id,
            cv_text=text,
            skills=skills,
            title=title,
            experience_years=experience_years,
            seniority_level=seniority_level,
            location="Remote"  # Default, can be extracted from CV
        )
        
        # Store in Redis with 30-minute TTL
        redis_key = f"cv:{session_id}:{member_id}"
        redis_client.setex(
            redis_key,
            timedelta(minutes=30),
            json.dumps({
                "member_id": member_id,
                "cv_text": text,
                "skills": skills,
                "experience_years": experience_years,
                "seniority_level": seniority_level,
                "title": title,
                "parsed_at": datetime.now().isoformat()
            })
        )
        
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

@app.post("/match_jobs", response_model=JobMatchResponse)
async def match_jobs(request: JobMatchRequest):
    """Get job matches for a parsed CV"""
    try:
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
        
        # Format matches for response
        matches = []
        for rec in recommendations:
            # Get job details from graph
            job_idx = matcher.graph.node_id_maps['job'].get(rec['job_id'])
            if job_idx is not None:
                job = matcher.graph.nodes['job'][job_idx]
                matches.append({
                    "job_id": job.job_id,
                    "title": job.title,
                    "company": job.company,
                    "description": job.description[:200] + "...",
                    "required_skills": job.required_skills,
                    "experience_required": job.experience_required,
                    "location": job.location,
                    "seniority_level": job.seniority_level,
                    "match_score": round(rec['score'], 3),
                    "match_quality": get_match_quality(rec['score'])
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

@app.on_event("startup")
async def startup_event():
    """Load saved model and initial jobs on startup"""
    try:
        # Load saved model if exists
        checkpoint = torch.load('job_matcher_model.pth', map_location=matcher.device)
        matcher.model.load_state_dict(checkpoint['model_state_dict'])
        print("Loaded saved model")
    except:
        print("No saved model found, starting fresh")
    
    # Load initial jobs from database
    # This should be replaced with actual database query
    sample_jobs = [
        Job("j1", "Python Developer", "Backend development", "TechCo",
            ["python", "django"], [], 2, "NYC", seniority_level="junior"),
        Job("j2", "Data Scientist", "ML role", "DataCorp",
            ["python", "machine-learning"], [], 3, "SF", seniority_level="mid"),
    ]
    
    for job in sample_jobs:
        matcher.graph.add_job_node(job)

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)