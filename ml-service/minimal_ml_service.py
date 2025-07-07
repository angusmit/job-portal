"""
minimal_ml_service.py - A minimal working version for testing
Save this file and run: uvicorn minimal_ml_service:app --reload
"""

from fastapi import FastAPI, File, UploadFile, Form, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Dict, Optional
import io
import hashlib
import json
from datetime import datetime

app = FastAPI(title="Minimal Job Matching ML Service")

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# In-memory storage (replace Redis for testing)
storage = {}
jobs_db = []

# Models
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
    mode: str = "graduate_friendly"
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

# Simple text extraction
def extract_text_from_file(file_content: bytes, filename: str) -> str:
    """Simple text extraction - just decode as UTF-8 for testing"""
    try:
        # For testing, just try to decode as text
        return file_content.decode('utf-8', errors='ignore')
    except:
        # Return dummy text for testing
        return """
        John Doe
        Software Engineer
        Skills: Python, JavaScript, React, Node.js
        Experience: 2 years as Software Developer
        Education: BS Computer Science
        """

def extract_skills(text: str) -> List[str]:
    """Extract skills from text"""
    skills = []
    skill_keywords = [
        'python', 'java', 'javascript', 'react', 'node.js', 'angular', 'vue',
        'django', 'flask', 'spring', 'docker', 'kubernetes', 'aws', 'git',
        'sql', 'mongodb', 'postgresql', 'redis', 'machine-learning', 'tensorflow'
    ]
    
    text_lower = text.lower()
    for skill in skill_keywords:
        if skill in text_lower:
            skills.append(skill)
    
    return skills if skills else ['python', 'javascript']  # Default skills

def extract_experience_years(text: str) -> int:
    """Extract years of experience"""
    import re
    
    # Look for patterns like "X years"
    patterns = [
        r'(\d+)\s*years?\s*(?:of\s*)?experience',
        r'experience:\s*(\d+)\s*years?',
        r'(\d+)\s*years?\s*as'
    ]
    
    for pattern in patterns:
        match = re.search(pattern, text.lower())
        if match:
            return int(match.group(1))
    
    # Check for graduate/intern keywords
    if any(word in text.lower() for word in ['graduate', 'intern', 'student']):
        return 0
    
    return 2  # Default

def determine_seniority_level(experience_years: int) -> str:
    """Determine seniority based on experience"""
    if experience_years == 0:
        return "entry"
    elif experience_years <= 2:
        return "junior"
    elif experience_years <= 5:
        return "mid"
    else:
        return "senior"

@app.post("/upload_cv")
async def upload_cv(
    file: UploadFile = File(...),
    session_id: str = Form(...)
):
    """Upload and parse CV file"""
    try:
        # Read file
        file_content = await file.read()
        
        # Extract text
        text = extract_text_from_file(file_content, file.filename)
        
        # Extract info
        skills = extract_skills(text)
        experience_years = extract_experience_years(text)
        seniority_level = determine_seniority_level(experience_years)
        
        # Generate member ID
        member_id = f"m_{hashlib.md5(file_content).hexdigest()[:8]}"
        
        # Store in memory
        storage_key = f"{session_id}:{member_id}"
        storage[storage_key] = {
            "cv_text": text,
            "skills": skills,
            "experience_years": experience_years,
            "seniority_level": seniority_level,
            "uploaded_at": datetime.now().isoformat()
        }
        
        return CVParseResponse(
            member_id=member_id,
            extracted_text=text[:200] + "...",
            skills=skills,
            experience_years=experience_years,
            seniority_level=seniority_level,
            title="Software Engineer",
            location="Remote"
        )
        
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))

@app.post("/match_jobs", response_model=JobMatchResponse)
async def match_jobs(request: JobMatchRequest):
    """Get job matches"""
    try:
        # Get member data
        storage_key = f"{request.session_id}:{request.member_id}"
        member_data = storage.get(storage_key)
        
        if not member_data:
            raise HTTPException(status_code=404, detail="CV not found")
        
        # Simple matching logic
        matches = []
        for job in jobs_db:
            # Calculate simple match score
            score = 0.0
            
            # Skill matching
            member_skills = set(member_data["skills"])
            job_skills = set(job["required_skills"])
            if job_skills:
                skill_match = len(member_skills & job_skills) / len(job_skills)
                score += skill_match * 0.5
            
            # Seniority matching
            if request.mode == "strict":
                if member_data["seniority_level"] == job["seniority_level"]:
                    score += 0.3
            elif request.mode == "graduate_friendly":
                if member_data["seniority_level"] == "entry" and job["seniority_level"] in ["entry", "junior"]:
                    score += 0.3
                elif member_data["seniority_level"] == job["seniority_level"]:
                    score += 0.3
            else:  # flexible
                score += 0.2
            
            # Experience matching
            exp_diff = abs(member_data["experience_years"] - job["experience_required"])
            if exp_diff == 0:
                score += 0.2
            elif exp_diff <= 1:
                score += 0.1
            
            if score > 0:
                matches.append({
                    **job,
                    "score": min(score + 0.1, 1.0)  # Add base score
                })
        
        # Sort by score
        matches.sort(key=lambda x: x["score"], reverse=True)
        
        return JobMatchResponse(
            matches=matches[:request.top_k],
            total_matches=len(matches)
        )
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/add_job")
async def add_job(job_data: JobData):
    """Add a job"""
    job_dict = job_data.dict()
    jobs_db.append(job_dict)
    return {"status": "success", "job_id": job_data.job_id}

@app.get("/health")
async def health():
    """Health check"""
    return {
        "status": "healthy",
        "jobs_count": len(jobs_db),
        "sessions_count": len(storage)
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)