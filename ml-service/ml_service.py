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
import pdfplumber
import docx
import re
import numpy as np
import logging

# Import your existing GNN modules
from improved_cv_matcher import ImprovedJobMatcher, Member, Job
from graduate_friendly_matcher import get_recommendations_graduate_friendly

from enhanced_cv_parser import (
    extract_skills_enhanced,
    extract_experience_years_enhanced,
    determine_seniority_level_enhanced,
    extract_education,
    extract_title,
    SKILL_VARIATIONS
)
from enhanced_matching import get_recommendations_enhanced


# Set up logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

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

# Request/Response Models
class CVParseRequest(BaseModel):
    session_id: str
    file_content: str  # Base64 encoded
    file_type: str     # pdf, docx, txt

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

# --- CV Parser Functions ---

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
        logger.error(f"Error extracting text from PDF: {e}")
        raise
    return text.strip()

def extract_text_from_docx(file_content: io.BytesIO) -> str:
    """Extract text from DOCX using BytesIO object"""
    try:
        doc = docx.Document(file_content)
        return "\n".join([para.text for para in doc.paragraphs if para.text.strip()])
    except Exception as e:
        logger.error(f"Error extracting text from DOCX: {e}")
        raise

# Replace the existing extract_skills function
extract_skills = extract_skills_enhanced

# Replace the existing extract_experience_years function  
extract_experience_years = extract_experience_years_enhanced

# Replace the existing determine_seniority_level function
determine_seniority_level = determine_seniority_level_enhanced


def extract_title(text: str) -> Optional[str]:
    """Extract job title from CV"""
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
        response = requests.get('http://localhost:8080/api/jobs', headers={'Accept': 'application/json'})
        if response.status_code == 200:
            jobs_data = response.json()
            jobs = []
            for job_data in jobs_data:
                all_text = f"{job_data.get('description', '')} {job_data.get('requirements', '')}"
                skills = extract_skills(all_text)
                experience_years = extract_experience_years(job_data.get('requirements', ''))
                if experience_years == 0 and 'senior' in job_data.get('title', '').lower():
                    experience_years = 5
                elif experience_years == 0 and 'mid' in job_data.get('title', '').lower():
                    experience_years = 3
                seniority = determine_seniority_level(all_text, experience_years)
                job = Job(
                    job_id=str(job_data['id']),
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
            logger.error(f"Failed to fetch jobs from Spring Boot: {response.status_code}")
            return []
    except Exception as e:
        logger.error(f"Error fetching jobs from Spring Boot: {e}")
        return []

# --- API Endpoints ---

@app.post("/upload_cv")
async def upload_cv(
    file: UploadFile = File(...),
    session_id: str = Form(...)
):
    """Upload and parse CV file with enhanced extraction"""
    try:
        # Read file content
        file_content = await file.read()
        
        # Determine file type and extract text
        if file.filename.lower().endswith('.pdf'):
            pdf_file = io.BytesIO(file_content)
            text = extract_text_from_pdf(pdf_file)
        elif file.filename.lower().endswith('.docx'):
            docx_file = io.BytesIO(file_content)
            text = extract_text_from_docx(docx_file)
        else:
            raise HTTPException(status_code=400, detail="Unsupported file format")
        
        # Enhanced extraction
        skills = extract_skills_enhanced(text)
        experience_years = extract_experience_years_enhanced(text)
        seniority_level = determine_seniority_level_enhanced(experience_years, text)
        title = extract_title(text)
        education = extract_education(text)
        
        # Log extraction results for debugging
        print(f"Extracted skills ({len(skills)}): {skills[:10]}...")
        print(f"Experience: {experience_years} years")
        print(f"Seniority: {seniority_level}")
        print(f"Title: {title}")
        print(f"Education: {education[:100]}...")
        
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
            "education": education,
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
        print(f"Error in upload_cv: {str(e)}")
        raise HTTPException(status_code=400, detail=str(e))

@app.post("/parse_cv_direct", response_model=CVParseResponse)
async def parse_cv_direct(
    session_id: str,
    cv_text: str
):
    """Parse CV text directly without file upload"""
    try:
        skills = extract_skills(cv_text)
        experience_years = extract_experience_years(cv_text)
        seniority_level = determine_seniority_level(experience_years)
        title = extract_title(cv_text)
        member_id = f"m_{hashlib.md5(cv_text.encode()).hexdigest()[:8]}"
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
        logger.error(f"Direct parse error: {str(e)}")
        raise HTTPException(status_code=400, detail=str(e))

@app.post("/match_jobs", response_model=JobMatchResponse)
async def match_jobs(request: JobMatchRequest):
    """Get job matches with enhanced matching logic"""
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
        
        # Create Member object with enhanced data
        member = Member(
            member_id=request.member_id,
            cv_text=cv_info["cv_text"],
            skills=cv_info["skills"],
            title=cv_info.get("title", "Not specified"),
            experience_years=cv_info["experience_years"],
            seniority_level=cv_info["seniority_level"],
            location="Remote"
        )
        
        # Add member to matcher graph temporarily
        matcher.graph.add_member_node(member)
        
        # Add the enhanced matching method to the matcher
        matcher.get_recommendations_enhanced = get_recommendations_enhanced.__get__(matcher, ImprovedJobMatcher)
        
        # Get recommendations with enhanced logic
        recommendations = matcher.get_recommendations_enhanced(
            request.member_id,
            top_k=request.top_k,
            mode=request.mode
        )
        
        # Log matching results
        print(f"Matching mode: {request.mode}")
        print(f"Found {len(recommendations)} matches")
        if recommendations:
            print(f"Top match: {recommendations[0]['title']} - Score: {recommendations[0]['score']:.2f}")
        
        # Format response
        matches = []
        for rec in recommendations:
            matches.append({
                "job_id": rec['job_id'],
                "title": rec['title'],
                "company": rec['company'],
                "location": rec['location'],
                "required_skills": rec['required_skills'],
                "preferred_skills": rec.get('preferred_skills', []),
                "experience_required": rec['experience_required'],
                "seniority_level": rec['seniority_level'],
                "score": rec['score'],
                "match_details": rec.get('match_details', {})
            })
        
        return JobMatchResponse(
            matches=matches,
            total_matches=len(matches)
        )
    
    except Exception as e:
        print(f"Error in match_jobs: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/test_extraction")
async def test_extraction(text: str):
    """Test the extraction functions with raw text"""
    try:
        skills = extract_skills_enhanced(text)
        experience = extract_experience_years_enhanced(text)
        seniority = determine_seniority_level_enhanced(experience, text)
        title = extract_title(text)
        education = extract_education(text)
        
        return {
            "skills": skills,
            "skill_count": len(skills),
            "experience_years": experience,
            "seniority_level": seniority,
            "title": title,
            "education": education
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

# Update the health endpoint to show more info
@app.get("/health")
async def health():
    """Health check with system info"""
    job_count = len(matcher.graph.nodes.get('job', {}))
    member_count = len(matcher.graph.nodes.get('member', {}))
    
    # Get sample skills to verify database is loaded
    
    sample_skills = list(SKILL_VARIATIONS.keys())[:10]
    
    return {
        "status": "healthy",
        "jobs_count": job_count,
        "members_count": member_count,
        "skill_database_loaded": len(SKILL_VARIATIONS) > 0,
        "total_skills": len(SKILL_VARIATIONS),
        "sample_skills": sample_skills,
        "matching_modes": ["strict", "flexible", "graduate_friendly"],
        "extraction_enhanced": True
    }

def calculate_match_score(member: Member, job: Job, mode: str) -> float:
    """Calculate match score between member and job"""
    score = 0.0
    member_skills = set(skill.lower() for skill in member.skills)
    job_skills = set(skill.lower() for skill in job.required_skills)
    if job_skills:
        skill_match = len(member_skills & job_skills) / len(job_skills)
        score += skill_match * 0.4
    else:
        score += 0.2
    exp_diff = abs(member.experience_years - job.experience_required)
    if mode == "graduate_friendly" and member.experience_years <= 1:
        if job.experience_required <= 2:
            score += 0.3
        elif job.experience_required <= 3:
            score += 0.2
        else:
            score += 0.1
    else:
        if exp_diff == 0:
            score += 0.3
        elif exp_diff <= 1:
            score += 0.25
        elif exp_diff <= 2:
            score += 0.15
        elif exp_diff <= 3:
            score += 0.05
    if mode == "strict":
        if member.seniority_level == job.seniority_level:
            score += 0.2
    elif mode == "flexible":
        level_map = {"entry": 0, "junior": 1, "mid": 2, "senior": 3}
        member_level = level_map.get(member.seniority_level, 0)
        job_level = level_map.get(job.seniority_level, 0)
        if abs(member_level - job_level) <= 1:
            score += 0.2
    else:
        if member.seniority_level in ["entry", "junior"]:
            if job.seniority_level in ["entry", "junior", "mid"]:
                score += 0.2
        else:
            score += 0.2
    if member.title and job.title:
        title_similarity = len(set(member.title.lower().split()) & set(job.title.lower().split()))
        if title_similarity > 0:
            score += 0.1
    if score == 0 and len(member_skills & job_skills) > 0:
        score = 0.1
    return min(score, 1.0)

@app.get("/test_matching/{member_id}")
async def test_matching(member_id: str):
    """Debug endpoint to test matching"""
    try:
        member = None
        for m in matcher.graph.nodes.get('member', {}).values():
            if m.member_id == member_id:
                member = m
                break
        if not member:
            return {"error": "Member not found"}
        jobs = list(matcher.graph.nodes.get('job', {}).values())
        results = {
            "member": {
                "id": member.member_id,
                "skills": member.skills,
                "experience": member.experience_years,
                "seniority": member.seniority_level
            },
            "available_jobs": len(jobs),
            "sample_scores": []
        }
        for job in jobs[:5]:
            for mode in ["strict", "flexible", "graduate_friendly"]:
                score = calculate_match_score(member, job, mode)
                results["sample_scores"].append({
                    "job_id": job.job_id,
                    "title": job.title,
                    "mode": mode,
                    "score": score
                })
        return results
    except Exception as e:
        return {"error": str(e)}

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
        logger.error(f"Add job error: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/train_model")
async def train_model():
    """Retrain the GNN model with current data"""
    try:
        members = list(matcher.graph.nodes['member'].values())
        jobs = list(matcher.graph.nodes['job'].values())
        if len(members) < 5 or len(jobs) < 5:
            return {"status": "skipped", "message": "Not enough data for training"}
        asyncio.create_task(train_model_async(members, jobs))
        return {"status": "training_started"}
    except Exception as e:
        logger.error(f"Train model error: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))

async def train_model_async(members, jobs):
    """Async model training"""
    matcher.train(members, jobs, epochs=20, batch_size=8)
    torch.save({
        'model_state_dict': matcher.model.state_dict(),
        'graph_data': matcher.graph.to_pyg_data()
    }, 'job_matcher_model.pth')

@app.post("/sync_jobs_from_spring")
async def sync_jobs_from_spring():
    """Fetch jobs from Spring Boot and add to ML service"""
    try:
        response = requests.get('http://localhost:8080/api/jobs')
        response.raise_for_status()
        jobs = response.json()
        matcher.graph.nodes['job'].clear()
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
                seniority_level=determine_seniority_level(job_data.get('experienceRequired', 2))
            )
            matcher.graph.add_job_node(job)
        return {"status": "success", "jobs_synced": len(jobs)}
    except Exception as e:
        logger.error(f"Sync jobs from Spring error: {str(e)}")
        return {"status": "error", "message": str(e)}

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
        if 'job' in matcher.graph.nodes:
            matcher.graph.nodes['job'].clear()
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
        logger.error(f"Manual sync jobs error: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))

@app.on_event("startup")
async def startup_event():
    """Load saved model and fetch jobs from Spring Boot on startup"""
    try:
        checkpoint = torch.load('job_matcher_model.pth', map_location=matcher.device)
        matcher.model.load_state_dict(checkpoint['model_state_dict'])
        logger.info("Loaded saved model")
    except:
        logger.info("No saved model found, starting fresh")
    logger.info("Fetching jobs from Spring Boot...")
    spring_jobs = fetch_jobs_from_spring_boot()
    if spring_jobs:
        logger.info(f"Loading {len(spring_jobs)} jobs from Spring Boot")
        for job in spring_jobs:
            matcher.graph.add_job_node(job)
            logger.info(f"Added job: {job.title} (ID: {job.job_id})")
    else:
        logger.warning("No jobs fetched from Spring Boot")

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
