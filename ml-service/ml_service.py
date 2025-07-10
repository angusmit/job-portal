# ml_service.py - Fixed version with proper error handling and type checking

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
    allow_origins=["http://localhost:3000", "http://localhost:8080", "*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Initialize Redis with error handling
try:
    redis_client = redis.Redis(host='localhost', port=6379, decode_responses=True)
    redis_client.ping()
    logger.info("Redis connection successful")
except Exception as e:
    logger.warning(f"Redis connection failed: {e}. Using in-memory storage.")
    redis_client = None

# Initialize models
matcher = ImprovedJobMatcher()

# In-memory storage as fallback
memory_storage = {}

# Request/Response Models
class CVParseRequest(BaseModel):
    session_id: str
    file_content: str
    file_type: str

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
    education: Optional[str] = None

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

# Storage helpers
def store_data(key: str, data: dict, expire_time: timedelta = timedelta(hours=1)):
    """Store data in Redis or memory"""
    if redis_client:
        try:
            redis_client.setex(key, expire_time, json.dumps(data))
        except:
            memory_storage[key] = {"data": data, "expire": datetime.now() + expire_time}
    else:
        memory_storage[key] = {"data": data, "expire": datetime.now() + expire_time}

def retrieve_data(key: str) -> Optional[dict]:
    """Retrieve data from Redis or memory"""
    if redis_client:
        try:
            data = redis_client.get(key)
            return json.loads(data) if data else None
        except:
            pass
    
    if key in memory_storage:
        if memory_storage[key]["expire"] > datetime.now():
            return memory_storage[key]["data"]
        else:
            del memory_storage[key]
    return None

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


# Add this method to ensure the graph is properly initialized
def ensure_graph_initialized():
    """Ensure the matcher graph is properly initialized"""
    if not hasattr(matcher, 'graph'):
        from improved_cv_matcher import JobMatchingGraph
        matcher.graph = JobMatchingGraph()
    
    if not hasattr(matcher.graph, 'nodes'):
        matcher.graph.nodes = {}
    
    if 'job' not in matcher.graph.nodes:
        matcher.graph.nodes['job'] = {}
    
    if 'member' not in matcher.graph.nodes:
        matcher.graph.nodes['member'] = {}

def fetch_jobs_from_spring_boot():
    """Fetch approved jobs from Spring Boot backend with robust error handling and skill/title extraction"""
    ensure_graph_initialized()

    try:
        urls_to_try = [
            'http://localhost:8080/api/jobs',
            'http://127.0.0.1:8080/api/jobs',
            'http://host.docker.internal:8080/api/jobs'  # Docker fallback
        ]

        jobs = []
        response = None

        for url in urls_to_try:
            try:
                response = requests.get(
                    url,
                    headers={'Accept': 'application/json'},
                    timeout=5
                )
                if response.status_code == 200:
                    logger.info(f"Connected to Spring Boot at {url}")
                    break
            except requests.exceptions.ConnectionError:
                logger.warning(f"Connection failed: {url}")
                continue

        if response and response.status_code == 200:
            jobs_data = response.json()
            logger.info(f"Fetched {len(jobs_data)} jobs from Spring Boot")

            for job_data in jobs_data:
                try:
                    title = str(job_data.get('title', '') or '')
                    description = str(job_data.get('description', '') or '')
                    requirements = str(job_data.get('requirements', '') or '')
                    company = str(job_data.get('company', '') or '')
                    location = str(job_data.get('location', '') or '')

                    all_text = f"{title} {description} {requirements}"

                    # Extract skills from text and title
                    skills = []
                    if all_text.strip():
                        skills = extract_skills_enhanced(all_text)
                        title_skills = extract_skills_from_title(title)
                        skills = list(set(skills + title_skills))

                    if skills:
                        logger.debug(f"Extracted skills for '{title}': {skills[:5]}...")

                    experience_years = 0
                    if requirements:
                        experience_years = extract_experience_years_enhanced(requirements)

                    if experience_years == 0 and title:
                        title_lower = title.lower()
                        if 'senior' in title_lower or 'lead' in title_lower:
                            experience_years = 5
                        elif 'mid' in title_lower or 'middle' in title_lower:
                            experience_years = 3
                        elif 'junior' in title_lower:
                            experience_years = 1
                        elif 'entry' in title_lower or 'graduate' in title_lower:
                            experience_years = 0

                    seniority = determine_seniority_level_enhanced(experience_years, all_text)

                    job = Job(
                        job_id=str(job_data.get('id', '')),
                        title=title,
                        description=description,
                        company=company,
                        required_skills=skills,
                        preferred_skills=[],
                        experience_required=experience_years,
                        location=location,
                        seniority_level=seniority
                    )
                    jobs.append(job)

                except Exception as e:
                    logger.error(f"Error processing job {job_data.get('id', 'unknown')}: {e}")
                    continue

            logger.info(f"Processed {len(jobs)} jobs successfully")
            return jobs
        else:
            logger.warning("Could not connect to any Spring Boot API endpoint")
            return []

    except Exception as e:
        logger.error(f"Unexpected error fetching jobs: {e}")
        return []


def extract_skills_from_title(title: str) -> List[str]:
    """Extract common tech skills based on keywords in job title"""
    skills = []
    title_lower = title.lower()

    title_skills = {
        'java': ['java'],
        'python': ['python'],
        'react': ['react', 'javascript'],
        'angular': ['angular', 'javascript'],
        'node': ['node.js', 'javascript'],
        'full stack': ['javascript', 'react', 'node.js'],
        'frontend': ['javascript', 'react', 'css', 'html'],
        'backend': ['java', 'python', 'sql'],
        'devops': ['docker', 'kubernetes', 'aws'],
        'data': ['python', 'sql', 'data-analysis'],
        'machine learning': ['python', 'machine-learning', 'tensorflow'],
        'ml': ['python', 'machine-learning'],
        'ai': ['python', 'machine-learning', 'ai'],
        '.net': ['csharp', '.net'],
        'android': ['java', 'kotlin', 'android'],
        'ios': ['swift', 'ios'],
        'cloud': ['aws', 'azure', 'gcp'],
        'aws': ['aws', 'cloud'],
    }

    for keyword, associated_skills in title_skills.items():
        if keyword in title_lower:
            skills.extend(associated_skills)

    return list(set(skills))


def get_sample_jobs():
    """Return sample jobs for testing when Spring Boot is not available"""
    sample_jobs = [
        Job(
            job_id="sample_1",
            title="Senior Software Engineer",
            description="We are looking for a Senior Software Engineer with expertise in Python and React",
            company="Tech Corp",
            required_skills=["python", "react", "javascript", "sql"],
            preferred_skills=["docker", "kubernetes"],
            experience_required=5,
            location="Remote",
            seniority_level="senior"
        ),
        Job(
            job_id="sample_2",
            title="Junior Data Analyst",
            description="Entry-level position for data analysis using Python and SQL",
            company="Data Inc",
            required_skills=["python", "sql", "data-analysis"],
            preferred_skills=["tableau", "excel"],
            experience_required=0,
            location="New York",
            seniority_level="junior"
        ),
        Job(
            job_id="sample_3",
            title="Full Stack Developer",
            description="Mid-level full stack developer position with React and Node.js",
            company="Web Solutions",
            required_skills=["javascript", "react", "node.js", "mongodb"],
            preferred_skills=["typescript", "aws"],
            experience_required=3,
            location="San Francisco",
            seniority_level="mid"
        )
    ]
    logger.info("Using sample jobs for testing")
    return sample_jobs

# API Endpoints
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
        
        # Generate member ID
        member_id = f"m_{hashlib.md5(file_content).hexdigest()[:8]}"
        
        # Store in storage
        cv_key = f"cv:{session_id}:{member_id}"
        cv_data = {
            "cv_text": text,
            "skills": skills,
            "experience_years": experience_years,
            "seniority_level": seniority_level,
            "title": title,
            "education": education,
            "uploaded_at": datetime.now().isoformat()
        }
        
        store_data(cv_key, cv_data)
        
        # Create member object and add to graph
        member = Member(
            member_id=member_id,
            cv_text=text,
            skills=skills,
            title=title or "Not specified",
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
            location="Remote",
            education=education
        )
    
    except Exception as e:
        logger.error(f"Error in upload_cv: {str(e)}")
        raise HTTPException(status_code=400, detail=str(e))

@app.post("/match_jobs", response_model=JobMatchResponse)
async def match_jobs(request: JobMatchRequest):
    """Get job matches with enhanced matching logic"""
    try:
        # Initialize graph nodes if not exists
        if 'job' not in matcher.graph.nodes:
            matcher.graph.nodes['job'] = {}
        if 'member' not in matcher.graph.nodes:
            matcher.graph.nodes['member'] = {}
        
        # Check current job count
        job_count = len(matcher.graph.nodes.get('job', {}))
        logger.info(f"Current jobs in graph: {job_count}")
        
        # Always try to sync jobs from Spring Boot
        if job_count == 0:
            logger.info("No jobs in graph, syncing from Spring Boot...")
            spring_jobs = fetch_jobs_from_spring_boot()
            for job in spring_jobs:
                matcher.graph.add_job_node(job)
            job_count = len(matcher.graph.nodes.get('job', {}))
            logger.info(f"After sync, jobs in graph: {job_count}")
        
        # Retrieve CV data
        cv_key = f"cv:{request.session_id}:{request.member_id}"
        cv_data = retrieve_data(cv_key)
        
        if not cv_data:
            raise HTTPException(status_code=404, detail="CV not found in session")
        
        logger.info(f"Processing match request for member {request.member_id}")
        logger.info(f"CV Skills: {cv_data['skills'][:5]}...")
        logger.info(f"Experience: {cv_data['experience_years']} years, Level: {cv_data['seniority_level']}")
        
        # Create Member object
        member = Member(
            member_id=request.member_id,
            cv_text=cv_data["cv_text"],
            skills=cv_data["skills"],
            title=cv_data.get("title", "Not specified"),
            experience_years=cv_data["experience_years"],
            seniority_level=cv_data["seniority_level"],
            location="Remote"
        )
        
        # Ensure member is in graph
        matcher.graph.add_member_node(member)
        
        # Get all jobs and calculate match scores
        jobs = list(matcher.graph.nodes.get('job', {}).values())
        matches = []
        
        for job in jobs:
            score = calculate_enhanced_match_score(member, job, request.mode)
            
            # Log detailed matching for debugging
            if score > 0:
                logger.debug(f"Job {job.job_id} - {job.title}: Score = {score}")
            
            # Include all jobs with score > 0
            if score > 0:
                match_details = calculate_match_details(member, job)
                matches.append({
                    "job_id": job.job_id,
                    "title": job.title,
                    "company": job.company,
                    "location": job.location,
                    "required_skills": job.required_skills,
                    "preferred_skills": job.preferred_skills,
                    "experience_required": job.experience_required,
                    "seniority_level": job.seniority_level,
                    "score": round(score, 3),
                    "match_details": match_details
                })
        
        # Sort by score descending
        matches.sort(key=lambda x: x['score'], reverse=True)
        top_matches = matches[:request.top_k]
        
        logger.info(f"Found {len(matches)} total matches, returning top {len(top_matches)}")
        
        return JobMatchResponse(
            matches=top_matches,
            total_matches=len(matches)
        )
    
    except Exception as e:
        logger.error(f"Error in match_jobs: {str(e)}")
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))

def calculate_enhanced_match_score(member: Member, job: Job, mode: str) -> float:
    """Enhanced match score calculation with better skill matching"""
    score = 0.0
    
    # Normalize skills for comparison
    member_skills_normalized = normalize_skills(member.skills)
    job_skills_normalized = normalize_skills(job.required_skills)
    
    # 1. Skill Matching (40% weight)
    if job_skills_normalized:
        # Direct matches
        direct_matches = len(member_skills_normalized & job_skills_normalized)
        
        # Fuzzy matches (e.g., "js" matches "javascript")
        fuzzy_matches = count_fuzzy_skill_matches(member_skills_normalized, job_skills_normalized)
        
        total_matches = direct_matches + (fuzzy_matches * 0.5)
        skill_match_ratio = min(total_matches / len(job_skills_normalized), 1.0)
        score += skill_match_ratio * 0.4
    else:
        # If no required skills specified, give partial credit
        score += 0.2
    
    # 2. Experience Matching (30% weight)
    exp_diff = abs(member.experience_years - job.experience_required)
    
    if mode == "graduate_friendly":
        # More lenient for graduates
        if member.experience_years <= 1:
            if job.experience_required <= 3:
                score += 0.3
            elif job.experience_required <= 5:
                score += 0.2
            else:
                score += 0.1
        else:
            # Standard experience matching
            if exp_diff == 0:
                score += 0.3
            elif exp_diff <= 1:
                score += 0.25
            elif exp_diff <= 2:
                score += 0.2
            elif exp_diff <= 3:
                score += 0.1
    elif mode == "flexible":
        # Flexible matching allows more deviation
        if exp_diff == 0:
            score += 0.3
        elif exp_diff <= 2:
            score += 0.25
        elif exp_diff <= 4:
            score += 0.15
        else:
            score += 0.05
    else:  # strict mode
        # Strict matching requires close experience match
        if exp_diff == 0:
            score += 0.3
        elif exp_diff <= 1:
            score += 0.2
        elif exp_diff <= 2:
            score += 0.1
    
    # 3. Seniority Level Matching (20% weight)
    seniority_score = calculate_seniority_match(member.seniority_level, job.seniority_level, mode)
    score += seniority_score * 0.2
    
    # 4. Title Similarity (10% weight)
    if member.title and job.title and member.title != "Not specified":
        title_score = calculate_title_similarity(member.title, job.title)
        score += title_score * 0.1
    
    # Ensure minimum score for any skill match
    if score < 0.1 and len(member_skills_normalized & job_skills_normalized) > 0:
        score = 0.1
    
    return min(score, 1.0)

def normalize_skills(skills: List[str]) -> set:
    """Normalize skills for better matching"""
    normalized = set()
    for skill in skills:
        skill_lower = skill.lower().strip()
        # Map variations to canonical form
        if skill_lower in SKILL_VARIATIONS:
            normalized.add(SKILL_VARIATIONS[skill_lower])
        else:
            normalized.add(skill_lower)
    return normalized

def count_fuzzy_skill_matches(member_skills: set, job_skills: set) -> int:
    """Count fuzzy matches between skill sets"""
    fuzzy_count = 0
    
    # Common abbreviations and variations
    skill_mappings = {
        'js': 'javascript',
        'ts': 'typescript',
        'py': 'python',
        'node': 'nodejs',
        'node.js': 'nodejs',
        'react.js': 'react',
        'vue.js': 'vue',
        'angular.js': 'angular',
        'postgres': 'postgresql',
        'mongo': 'mongodb'
    }
    
    for member_skill in member_skills:
        for job_skill in job_skills:
            # Check if one is abbreviation of other
            if member_skill in skill_mappings and skill_mappings[member_skill] == job_skill:
                fuzzy_count += 1
            elif job_skill in skill_mappings and skill_mappings[job_skill] == member_skill:
                fuzzy_count += 1
            # Check if one contains the other
            elif len(member_skill) > 3 and len(job_skill) > 3:
                if member_skill in job_skill or job_skill in member_skill:
                    fuzzy_count += 1
    
    return fuzzy_count

def calculate_seniority_match(member_level: str, job_level: str, mode: str) -> float:
    """Calculate seniority level match score"""
    level_map = {
        "entry": 0,
        "junior": 1,
        "mid": 2,
        "senior": 3,
        "lead": 4,
        "principal": 5
    }
    
    member_num = level_map.get(member_level, 0)
    job_num = level_map.get(job_level, 0)
    diff = abs(member_num - job_num)
    
    if mode == "strict":
        return 1.0 if diff == 0 else 0.0
    elif mode == "flexible":
        if diff == 0:
            return 1.0
        elif diff == 1:
            return 0.7
        elif diff == 2:
            return 0.3
        else:
            return 0.0
    else:  # graduate_friendly
        if member_num <= 1:  # entry or junior
            if job_num <= 2:  # entry, junior, or mid
                return 1.0
            elif job_num == 3:  # senior
                return 0.5
            else:
                return 0.2
        else:
            return 1.0 if diff <= 1 else 0.5 if diff == 2 else 0.0

def calculate_title_similarity(member_title: str, job_title: str) -> float:
    """Calculate similarity between job titles"""
    member_words = set(member_title.lower().split())
    job_words = set(job_title.lower().split())
    
    # Remove common words
    common_words = {'the', 'a', 'an', 'and', 'or', 'but', 'in', 'on', 'at', 'to', 'for'}
    member_words -= common_words
    job_words -= common_words
    
    if not member_words or not job_words:
        return 0.0
    
    # Calculate Jaccard similarity
    intersection = len(member_words & job_words)
    union = len(member_words | job_words)
    
    return intersection / union if union > 0 else 0.0

def calculate_match_details(member: Member, job: Job) -> dict:
    """Calculate detailed match information"""
    member_skills_normalized = normalize_skills(member.skills)
    job_skills_normalized = normalize_skills(job.required_skills)
    
    matched_skills = list(member_skills_normalized & job_skills_normalized)
    missing_skills = list(job_skills_normalized - member_skills_normalized)
    
    return {
        "skill_match": len(matched_skills),
        "total_required_skills": len(job_skills_normalized),
        "matched_skills": matched_skills[:10],  # Limit for response size
        "missing_skills": missing_skills[:5],   # Show top missing skills
        "experience_diff": member.experience_years - job.experience_required,
        "experience_match": "exact" if member.experience_years == job.experience_required 
                          else "over" if member.experience_years > job.experience_required 
                          else "under",
        "seniority_match": member.seniority_level == job.seniority_level
    }

@app.get("/health")
async def health():
    """Health check with system info"""
    job_count = len(matcher.graph.nodes.get('job', {}))
    member_count = len(matcher.graph.nodes.get('member', {}))
    
    return {
        "status": "healthy",
        "jobs_count": job_count,
        "members_count": member_count,
        "skill_database_loaded": len(SKILL_VARIATIONS) > 0,
        "total_skills": len(SKILL_VARIATIONS),
        "redis_connected": redis_client is not None,
        "matching_modes": ["strict", "flexible", "graduate_friendly"],
        "extraction_enhanced": True
    }

@app.post("/sync_jobs")
async def sync_jobs():
    """Manually sync jobs from Spring Boot"""
    try:
        # Clear existing jobs
        if 'job' not in matcher.graph.nodes:
            matcher.graph.nodes['job'] = {}
        else:
            matcher.graph.nodes['job'].clear()
        
        # Fetch new jobs
        spring_jobs = fetch_jobs_from_spring_boot()
        
        if not spring_jobs:
            logger.warning("No jobs fetched from Spring Boot")
            return {
                "status": "warning",
                "message": "No jobs found in Spring Boot",
                "jobs_synced": 0
            }
        
        # Add jobs to graph
        for job in spring_jobs:
            matcher.graph.add_job_node(job)
            logger.info(f"Added job: {job.title} (ID: {job.job_id}, Skills: {len(job.required_skills)})")
        
        return {
            "status": "success",
            "message": f"Synced {len(spring_jobs)} jobs from Spring Boot",
            "jobs_synced": len(spring_jobs),
            "jobs": [{"id": job.job_id, "title": job.title, "skills": job.required_skills} for job in spring_jobs]
        }
    except Exception as e:
        logger.error(f"Sync error: {str(e)}")
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/debug/jobs")
async def debug_jobs():
    """Debug endpoint to see what jobs are loaded"""
    ensure_graph_initialized()
    
    jobs = list(matcher.graph.nodes.get('job', {}).values())
    return {
        "total_jobs": len(jobs),
        "jobs": [
            {
                "job_id": job.job_id,
                "title": job.title,
                "company": job.company,
                "skills": job.required_skills,
                "experience": job.experience_required,
                "seniority": job.seniority_level
            }
            for job in jobs[:10]  # Show first 10 jobs
        ]
    }


@app.post("/reload_jobs")
async def reload_jobs():
    """Force reload all jobs from Spring Boot"""
    ensure_graph_initialized()
    
    # Clear existing jobs
    matcher.graph.nodes['job'].clear()
    
    # Fetch and load new jobs
    jobs = fetch_jobs_from_spring_boot()
    
    if jobs:
        for job in jobs:
            matcher.graph.add_job_node(job)
        
        return {
            "status": "success",
            "message": f"Reloaded {len(jobs)} jobs",
            "jobs_count": len(jobs)
        }
    else:
        return {
            "status": "error",
            "message": "No jobs could be loaded from Spring Boot",
            "jobs_count": 0
        }

@app.on_event("startup")
async def startup_event():
    """Load jobs on startup"""
    logger.info("Starting ML Service...")
    
    # Initialize graph nodes
    if not hasattr(matcher.graph, 'nodes'):
        matcher.graph.nodes = {}
    if 'job' not in matcher.graph.nodes:
        matcher.graph.nodes['job'] = {}
    if 'member' not in matcher.graph.nodes:
        matcher.graph.nodes['member'] = {}
    
    # Load jobs
    try:
        spring_jobs = fetch_jobs_from_spring_boot()
        if spring_jobs:
            for job in spring_jobs:
                matcher.graph.add_job_node(job)
            logger.info(f"Loaded {len(spring_jobs)} jobs on startup")
        else:
            logger.warning("No jobs loaded on startup - Spring Boot may not be running")
    except Exception as e:
        logger.error(f"Failed to load jobs on startup: {e}")



if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)