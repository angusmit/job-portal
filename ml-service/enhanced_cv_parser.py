# enhanced_cv_parser.py - Add this to your ml_service directory

import re
from typing import List, Dict, Tuple
from datetime import datetime
import spacy

# Load spaCy model for better NLP
try:
    nlp = spacy.load("en_core_web_sm")
except:
    import subprocess
    subprocess.run(["python", "-m", "spacy", "download", "en_core_web_sm"])
    nlp = spacy.load("en_core_web_sm")

# Comprehensive skill database with variations
SKILL_DATABASE = {
    # Programming Languages
    'python': ['python', 'py', 'python3', 'python2', 'pytorch', 'pandas', 'numpy', 'scipy'],
    'java': ['java', 'jvm', 'java8', 'java11', 'spring', 'springboot', 'spring boot', 'hibernate'],
    'javascript': ['javascript', 'js', 'es6', 'es5', 'ecmascript', 'node.js', 'nodejs', 'node'],
    'typescript': ['typescript', 'ts', 'type script'],
    'csharp': ['c#', 'csharp', 'c sharp', '.net', 'dotnet'],
    'cpp': ['c++', 'cpp', 'cplusplus'],
    'c': ['c programming', 'c language', ' c ', 'clang'],
    'go': ['golang', 'go language', ' go '],
    'rust': ['rust', 'rustlang'],
    'kotlin': ['kotlin', 'kt'],
    'swift': ['swift', 'swiftui'],
    'php': ['php', 'laravel', 'symfony'],
    'ruby': ['ruby', 'rails', 'ruby on rails', 'ror'],
    'scala': ['scala', 'akka'],
    'r': ['r programming', 'r language', ' r ', 'rstudio'],
    
    # Web Frameworks & Libraries
    'react': ['react', 'reactjs', 'react.js', 'redux', 'react native'],
    'angular': ['angular', 'angularjs', 'angular.js', 'angular2+'],
    'vue': ['vue', 'vuejs', 'vue.js', 'vuex', 'nuxt'],
    'django': ['django', 'django rest', 'drf'],
    'flask': ['flask', 'flask api'],
    'express': ['express', 'expressjs', 'express.js'],
    'fastapi': ['fastapi', 'fast api'],
    
    # Databases
    'sql': ['sql', 'mysql', 'postgresql', 'postgres', 'sqlite', 'mssql', 'oracle', 'plsql'],
    'mongodb': ['mongodb', 'mongo', 'mongoose'],
    'redis': ['redis', 'memcached'],
    'elasticsearch': ['elasticsearch', 'elastic search', 'elk'],
    'cassandra': ['cassandra', 'cql'],
    
    # Cloud & DevOps
    'aws': ['aws', 'amazon web services', 'ec2', 's3', 'lambda', 'dynamodb', 'cloudformation'],
    'azure': ['azure', 'microsoft azure', 'azure devops'],
    'gcp': ['gcp', 'google cloud', 'google cloud platform'],
    'docker': ['docker', 'containers', 'containerization', 'dockerfile'],
    'kubernetes': ['kubernetes', 'k8s', 'kubectl', 'helm'],
    'jenkins': ['jenkins', 'ci/cd', 'continuous integration'],
    'terraform': ['terraform', 'infrastructure as code', 'iac'],
    'ansible': ['ansible', 'configuration management'],
    
    # Data Science & ML
    'machine-learning': ['machine learning', 'ml', 'deep learning', 'dl', 'neural networks', 'ai', 'artificial intelligence'],
    'tensorflow': ['tensorflow', 'tf', 'keras', 'tf2'],
    'scikit-learn': ['scikit-learn', 'sklearn', 'sci-kit learn'],
    'nlp': ['nlp', 'natural language processing', 'text mining'],
    'computer-vision': ['computer vision', 'cv', 'image processing', 'opencv'],
    'data-analysis': ['data analysis', 'data analytics', 'business intelligence', 'bi'],
    'spark': ['spark', 'apache spark', 'pyspark', 'spark sql'],
    
    # Other Technical Skills
    'git': ['git', 'github', 'gitlab', 'bitbucket', 'version control', 'svn'],
    'linux': ['linux', 'unix', 'ubuntu', 'centos', 'debian', 'bash', 'shell scripting'],
    'agile': ['agile', 'scrum', 'kanban', 'jira', 'sprint'],
    'rest': ['rest', 'restful', 'rest api', 'api', 'web services'],
    'graphql': ['graphql', 'graph ql'],
    'microservices': ['microservices', 'micro services', 'service oriented'],
    'blockchain': ['blockchain', 'crypto', 'web3', 'smart contracts'],
    'security': ['security', 'cybersecurity', 'infosec', 'penetration testing'],
    'mobile': ['mobile', 'ios', 'android', 'react native', 'flutter'],
    'testing': ['testing', 'test automation', 'selenium', 'junit', 'pytest', 'unit testing', 'qa']
}

# Flatten skill variations for easy lookup
SKILL_VARIATIONS = {}
for main_skill, variations in SKILL_DATABASE.items():
    for variation in variations:
        SKILL_VARIATIONS[variation.lower()] = main_skill

def extract_skills_enhanced(text: str) -> List[str]:
    """Enhanced skill extraction with NLP and comprehensive skill database"""
    if not text:
        return []
    
    text_lower = text.lower()
    found_skills = set()
    
    # Method 1: Direct pattern matching with word boundaries
    for variation, main_skill in SKILL_VARIATIONS.items():
        # Use word boundaries for accurate matching
        pattern = r'\b' + re.escape(variation) + r'\b'
        if re.search(pattern, text_lower):
            found_skills.add(main_skill)
    
    # Method 2: Extract from specific sections
    skill_sections = re.findall(
        r'(?:skills?|technical skills?|core competenc\w+|expertise|technologies)[:\-\s]*([^\n]{1,500})',
        text_lower,
        re.IGNORECASE
    )
    
    for section in skill_sections:
        # Split by common delimiters
        tokens = re.split(r'[,;|•·\n]', section)
        for token in tokens:
            token = token.strip()
            if len(token) > 1:
                for variation, main_skill in SKILL_VARIATIONS.items():
                    if variation in token:
                        found_skills.add(main_skill)
    
    # Method 3: NLP-based extraction for better context understanding
    doc = nlp(text[:1000000])  # Limit text length for NLP processing
    
    # Look for technical terms and proper nouns
    for token in doc:
        token_lower = token.text.lower()
        if token_lower in SKILL_VARIATIONS:
            found_skills.add(SKILL_VARIATIONS[token_lower])
    
    # Method 4: Extract from job titles and descriptions
    job_indicators = ['developer', 'engineer', 'analyst', 'scientist', 'architect', 'designer']
    for indicator in job_indicators:
        if indicator in text_lower:
            # Add related skills based on job type
            if 'data' in text_lower and indicator in ['scientist', 'analyst', 'engineer']:
                found_skills.update(['python', 'sql', 'data-analysis'])
            elif 'full stack' in text_lower or 'fullstack' in text_lower:
                found_skills.update(['javascript', 'react', 'node.js'])
            elif 'backend' in text_lower or 'back-end' in text_lower:
                found_skills.update(['python', 'java', 'sql'])
            elif 'frontend' in text_lower or 'front-end' in text_lower:
                found_skills.update(['javascript', 'react', 'css'])
            elif 'devops' in text_lower:
                found_skills.update(['docker', 'kubernetes', 'aws'])
    
    # Convert to list and limit to reasonable number
    skills_list = list(found_skills)[:30]  # Limit to top 30 skills
    
    # If no skills found, extract most common tech terms
    if not skills_list:
        common_terms = ['python', 'java', 'javascript', 'sql', 'git']
        for term in common_terms:
            if term in text_lower:
                skills_list.append(term)
    
    return skills_list

def extract_experience_years_enhanced(text: str) -> int:
    """Enhanced experience extraction with multiple patterns"""
    if not text:
        return 0
    
    text_lower = text.lower()
    
    # Pattern 1: Direct year mentions (e.g., "5 years experience")
    year_patterns = [
        r'(\d+)\+?\s*(?:years?|yrs?)\s*(?:of\s*)?(?:experience|exp)',
        r'(?:experience|exp)[:\s]+(\d+)\+?\s*(?:years?|yrs?)',
        r'(\d+)\+?\s*(?:years?|yrs?)\s*(?:professional|work)',
        r'(?:total|overall)\s*(?:experience|exp)?[:\s]*(\d+)\+?\s*(?:years?|yrs?)'
    ]
    
    for pattern in year_patterns:
        matches = re.findall(pattern, text_lower)
        if matches:
            # Return the maximum years found
            years = [int(match) for match in matches if match.isdigit()]
            if years:
                return max(years)
    
    # Pattern 2: Date ranges (e.g., "2019 - 2024", "Jan 2020 - Present")
    current_year = datetime.now().year
    date_ranges = []
    
    # Year ranges
    year_range_pattern = r'(\d{4})\s*[-–—]\s*(\d{4}|present|current)'
    for match in re.finditer(year_range_pattern, text_lower):
        start_year = int(match.group(1))
        end_year = current_year if match.group(2) in ['present', 'current'] else int(match.group(2))
        if 1990 <= start_year <= current_year and start_year <= end_year:
            date_ranges.append(end_year - start_year)
    
    # Month-Year ranges
    month_year_pattern = r'(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\w*\s+(\d{4})\s*[-–—]\s*(?:(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\w*\s+)?(\d{4}|present|current)'
    for match in re.finditer(month_year_pattern, text_lower):
        start_year = int(match.group(1))
        end_year = current_year if match.group(2) in ['present', 'current'] else int(match.group(2))
        if 1990 <= start_year <= current_year and start_year <= end_year:
            date_ranges.append(end_year - start_year)
    
    if date_ranges:
        # Sum up all experience ranges (for multiple positions)
        total_experience = sum(date_ranges)
        return min(total_experience, 30)  # Cap at 30 years
    
    # Pattern 3: Education-based estimation
    education_patterns = {
        r'ph\.?d|phd|doctorate': 5,  # PhD typically means 5+ years
        r'master|m\.?s\.?|mba|m\.?tech': 2,  # Master's typically means 2+ years
        r'bachelor|b\.?s\.?|b\.?tech|b\.?e\.?': 0,  # Fresh graduate
        r'graduate|graduating|final year': 0,
        r'intern|internship': 0
    }
    
    for pattern, default_years in education_patterns.items():
        if re.search(pattern, text_lower):
            # Check for graduation year
            grad_pattern = rf'{pattern}.*?(\d{{4}})'
            grad_match = re.search(grad_pattern, text_lower)
            if grad_match:
                grad_year = int(grad_match.group(1))
                if 1990 <= grad_year <= current_year:
                    return current_year - grad_year
            return default_years
    
    # Pattern 4: Seniority keywords
    seniority_keywords = {
        'principal|staff|lead|head|director|vp|vice president': 10,
        'senior|sr\.?': 5,
        'mid-level|intermediate': 3,
        'junior|jr\.?': 1,
        'entry level|entry-level|fresher|graduate': 0,
        'intern|trainee': 0
    }
    
    for keywords, years in seniority_keywords.items():
        if re.search(keywords, text_lower):
            return years
    
    # Pattern 5: Count number of job positions (each position ~2 years average)
    job_titles = re.findall(
        r'(?:software|data|ml|ai|web|backend|frontend|full[\s-]?stack)\s*(?:developer|engineer|scientist|analyst|architect)',
        text_lower
    )
    if len(job_titles) > 1:
        return min(len(set(job_titles)) * 2, 15)
    
    # Default based on content analysis
    if len(text) > 2000 and len(found_skills) > 10:  # Detailed CV with many skills
        return 3  # Assume mid-level
    elif len(text) > 1000:
        return 1  # Assume junior
    
    return 0  # Default to entry-level

def determine_seniority_level_enhanced(experience_years: int, text: str = "") -> str:
    """Enhanced seniority determination with context"""
    text_lower = text.lower() if text else ""
    
    # Check for explicit seniority mentions
    if any(word in text_lower for word in ['principal', 'staff', 'director', 'head', 'lead architect']):
        return "senior"
    elif 'senior' in text_lower or 'sr.' in text_lower:
        return "senior"
    elif any(word in text_lower for word in ['junior', 'jr.', 'entry level', 'entry-level']):
        return "junior"
    elif any(word in text_lower for word in ['intern', 'trainee', 'fresher', 'graduate']):
        return "entry"
    
    # Fallback to experience-based determination
    if experience_years >= 7:
        return "senior"
    elif experience_years >= 3:
        return "mid"
    elif experience_years >= 1:
        return "junior"
    else:
        return "entry"

def extract_education(text: str) -> str:
    """Extract education information"""
    education_keywords = [
        'ph.d', 'phd', 'doctorate',
        'master', 'msc', 'ms', 'ma', 'mba', 'mtech',
        'bachelor', 'bsc', 'bs', 'ba', 'btech', 'be',
        'diploma', 'certification', 'certificate'
    ]
    
    text_lower = text.lower()
    found_education = []
    
    for keyword in education_keywords:
        if keyword in text_lower:
            # Try to extract the full education line
            pattern = rf'[^\n]*{keyword}[^\n]*'
            matches = re.findall(pattern, text_lower, re.IGNORECASE)
            if matches:
                found_education.extend(matches[:2])  # Limit to 2 matches per keyword
    
    if found_education:
        # Clean and return the most relevant education
        return '; '.join([edu.strip()[:100] for edu in found_education[:3]])
    
    return "Not specified"

def extract_title(text: str) -> str:
    """Extract job title or current role"""
    # Common job title patterns
    title_patterns = [
        r'(?:^|\n)([^\n]*(?:developer|engineer|scientist|analyst|architect|designer|manager|lead|consultant)[^\n]*)',
        r'(?:current role|position|title)[:\s]*([^\n]+)',
        r'(?:working as|work as|employed as)[:\s]*([^\n]+)',
        r'(?:^|\n)(?:i am a|i\'m a)\s+([^\n]+)'
    ]
    
    text_lower = text.lower()
    
    for pattern in title_patterns:
        matches = re.findall(pattern, text_lower, re.IGNORECASE)
        if matches:
            # Clean and return the first valid match
            title = matches[0].strip()
            if len(title) > 5 and len(title) < 100:
                return title.title()
    
    # Fallback: look for common titles
    common_titles = [
        'software engineer', 'data scientist', 'full stack developer',
        'backend developer', 'frontend developer', 'ml engineer',
        'devops engineer', 'product manager', 'data analyst'
    ]
    
    for title in common_titles:
        if title in text_lower:
            return title.title()
    
    return "Software Professional"