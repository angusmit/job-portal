# test_enhanced_system.py - Save in ml-service directory
import requests
import json
from datetime import datetime

BASE_URL = "http://localhost:8000"

def test_extraction():
    """Test the enhanced extraction capabilities"""
    print("=== Testing Enhanced Extraction ===\n")
    
    test_cases = [
        {
            "name": "Senior Developer CV",
            "text": """
                John Doe - Senior Full Stack Developer
                
                Experience:
                Lead Software Engineer at Microsoft (2020-2024)
                Senior Developer at Amazon (2017-2020)
                
                Skills: Python, JavaScript, TypeScript, React, Node.js, Django, 
                Docker, Kubernetes, AWS, PostgreSQL, MongoDB, Redis, GraphQL,
                Microservices, CI/CD, Machine Learning, TensorFlow
                
                Education: MS Computer Science, MIT (2017)
            """
        },
        {
            "name": "Graduate CV",
            "text": """
                Jane Smith
                Recent CS Graduate | Aspiring Software Developer
                
                Education:
                BS Computer Science, UC Berkeley (May 2024)
                GPA: 3.8/4.0
                
                Technical Skills:
                • Programming: Python, Java, JavaScript, C++
                • Web: HTML, CSS, React, Node.js
                • Databases: MySQL, MongoDB
                • Tools: Git, VS Code, Linux, Docker
                
                Internship:
                Software Engineering Intern - Google (Summer 2023)
                - Worked on frontend using React
                - Collaborated using Git and Agile
            """
        },
        {
            "name": "Simple CV",
            "text": """
                Python developer with 3 years experience.
                Worked with Django and Flask.
                Also know JavaScript and React.
                Looking for backend positions.
            """
        }
    ]
    
    for test in test_cases:
        print(f"Testing: {test['name']}")
        response = requests.post(
            f"{BASE_URL}/test_extraction",
            json={"text": test['text']}
        )
        
        if response.status_code == 200:
            data = response.json()
            print(f"  Skills found: {data['skill_count']} - {data['skills'][:5]}...")
            print(f"  Experience: {data['experience_years']} years")
            print(f"  Seniority: {data['seniority_level']}")
            print(f"  Title: {data['title']}")
            print(f"  Extraction successful\n")
        else:
            print(f"  Extraction failed: {response.status_code}\n")

def test_cv_upload_and_matching():
    """Test full CV upload and matching flow"""
    print("\n=== Testing CV Upload and Matching ===\n")
    
    # Create a test CV content
    cv_content = """
    Michael Johnson
    Senior Software Engineer | Full Stack Developer
    
    PROFESSIONAL EXPERIENCE
    
    Senior Software Engineer - Netflix (Jan 2021 - Present)
    • Led development of microservices using Python, Go, and Node.js
    • Architected scalable solutions on AWS using Docker and Kubernetes
    • Mentored junior developers and conducted code reviews
    • Tech stack: Python, Go, React, PostgreSQL, Redis, Kafka, AWS
    
    Software Engineer - Uber (Jun 2018 - Dec 2020)
    • Developed real-time data processing pipelines using Apache Spark
    • Built RESTful APIs using Django and FastAPI
    • Implemented CI/CD pipelines with Jenkins and GitLab
    • Tech stack: Python, Java, Spark, Cassandra, Docker
    
    Junior Developer - Startup XYZ (Jan 2017 - May 2018)
    • Full stack development using MEAN stack
    • Implemented responsive UI with React and Material-UI
    • Tech stack: JavaScript, Node.js, MongoDB, React
    
    TECHNICAL SKILLS
    
    Languages: Python, JavaScript, TypeScript, Java, Go, SQL
    Backend: Django, FastAPI, Flask, Node.js, Express, Spring Boot
    Frontend: React, Angular, Vue.js, HTML5, CSS3, Tailwind
    Databases: PostgreSQL, MySQL, MongoDB, Redis, Cassandra, DynamoDB
    Cloud & DevOps: AWS, GCP, Docker, Kubernetes, Terraform, Jenkins, CI/CD
    Big Data: Apache Spark, Kafka, Elasticsearch, Hadoop
    Other: GraphQL, REST APIs, Microservices, Agile, Git, Linux
    
    EDUCATION
    
    Master of Science in Computer Science
    Stanford University (2016)
    
    Bachelor of Science in Computer Science  
    UC Berkeley (2014)
    """
    
    # Save as text file for upload
    with open("test_cv.txt", "w") as f:
        f.write(cv_content)
    
    session_id = f"test-{datetime.now().timestamp()}"
    
    # Upload CV
    print("1. Uploading CV...")
    with open("test_cv.txt", "rb") as f:
        files = {'file': ('test_cv.txt', f, 'text/plain')}
        data = {'session_id': session_id}
        
        response = requests.post(f"{BASE_URL}/upload_cv", files=files, data=data)
        
        if response.status_code == 200:
            cv_data = response.json()
            print(f"  CV uploaded successfully")
            print(f"  Member ID: {cv_data['member_id']}")
            print(f"  Skills extracted: {len(cv_data['skills'])}")
            print(f"  Experience: {cv_data['experience_years']} years")
            print(f"  Seniority: {cv_data['seniority_level']}")
            
            # Test all matching modes
            member_id = cv_data['member_id']
            
            for mode in ['strict', 'flexible', 'graduate_friendly']:
                print(f"\n2. Testing {mode.upper()} matching mode...")
                
                match_response = requests.post(
                    f"{BASE_URL}/match_jobs",
                    json={
                        'session_id': session_id,
                        'member_id': member_id,
                        'mode': mode,
                        'top_k': 5
                    }
                )
                
                if match_response.status_code == 200:
                    matches = match_response.json()
                    print(f"  Found {matches['total_matches']} matches")
                    
                    if matches['matches']:
                        print("  Top matches:")
                        for i, match in enumerate(matches['matches'][:3], 1):
                            print(f"    {i}. {match['title']} at {match['company']}")
                            print(f"       Score: {match['score']:.2%}")
                            if 'match_details' in match:
                                print(f"       Why: {match['match_details'].get('why_matched', 'N/A')}")
                else:
                    print(f"  Matching failed: {match_response.status_code}")
        else:
            print(f"  CV upload failed: {response.status_code}")
            print(f"  Error: {response.text}")

def test_graduate_cv():
    """Test specifically with a graduate CV"""
    print("\n=== Testing Graduate CV Handling ===\n")
    
    graduate_cv = """
    Emily Chen
    Computer Science Graduate
    
    Education:
    Bachelor of Science in Computer Science
    University of Washington, Seattle
    Graduated: June 2024
    GPA: 3.7/4.0
    
    Relevant Coursework:
    • Data Structures and Algorithms
    • Web Development
    • Database Systems
    • Machine Learning
    • Software Engineering
    
    Technical Skills:
    Programming Languages: Python, Java, JavaScript, C++
    Web Technologies: HTML, CSS, React, Node.js
    Databases: MySQL, MongoDB
    Tools: Git, VS Code, Linux
    
    Projects:
    1. E-commerce Website (React, Node.js, MongoDB)
       - Built full-stack shopping platform
       - Implemented user authentication and payment processing
    
    2. Machine Learning Classifier (Python, Scikit-learn)
       - Developed sentiment analysis for movie reviews
       - Achieved 87% accuracy
    
    Internship Experience:
    Software Engineering Intern - Microsoft (Summer 2023)
    - Worked on Azure team developing cloud services
    - Used C# and Python for backend development
    """
    
    with open("graduate_cv.txt", "w") as f:
        f.write(graduate_cv)
    
    session_id = "test-graduate"
    
    # Upload graduate CV
    with open("graduate_cv.txt", "rb") as f:
        files = {'file': ('graduate_cv.txt', f, 'text/plain')}
        data = {'session_id': session_id}
        
        response = requests.post(f"{BASE_URL}/upload_cv", files=files, data=data)
        
        if response.status_code == 200:
            cv_data = response.json()
            print(f"Graduate CV Analysis:")
            print(f"  Experience: {cv_data['experience_years']} years (should be 0-1)")
            print(f"  Seniority: {cv_data['seniority_level']} (should be 'entry')")
            print(f"  Skills: {len(cv_data['skills'])} found")
            
            # Test graduate-friendly matching
            match_response = requests.post(
                f"{BASE_URL}/match_jobs",
                json={
                    'session_id': session_id,
                    'member_id': cv_data['member_id'],
                    'mode': 'graduate_friendly',
                    'top_k': 10
                }
            )
            
            if match_response.status_code == 200:
                matches = match_response.json()
                print(f"\n  Graduate-friendly mode found {matches['total_matches']} matches")
                print("  (Should find entry-level and junior positions)")
            else:
                print(f"  Matching failed for graduate CV")

def check_system_health():
    """Check if the enhanced system is properly configured"""
    print("=== System Health Check ===\n")
    
    response = requests.get(f"{BASE_URL}/health")
    if response.status_code == 200:
        data = response.json()
        print(f"ML Service is running")
        print(f"  Jobs loaded: {data['jobs_count']}")
        print(f"  Skill database: {'Loaded' if data['skill_database_loaded'] else 'Not loaded'}")
        print(f"  Total skills: {data['total_skills']}")
        print(f"  Enhanced extraction: {'Enabled' if data.get('extraction_enhanced') else 'Disabled'}")
        
        if data['jobs_count'] == 0:
            print("\nNo jobs loaded. Syncing from Spring Boot...")
            sync_response = requests.post(f"{BASE_URL}/sync_jobs")
            if sync_response.status_code == 200:
                print("Jobs synced successfully")
    else:
        print("ML Service is not responding")

if __name__ == "__main__":
    print("Enhanced CV Matching System Test")
    print("="*50 + "\n")
    
    # Check system health first
    check_system_health()
    
    # Test extraction
    test_extraction()
    
    # Test full flow
    test_cv_upload_and_matching()
    
    # Test graduate CV specifically
    test_graduate_cv()
    
    print("\n" + "="*50)
    print("Testing complete! Check the results above.")
    print("\nIf all tests passed, your enhanced system is working correctly!")
    print("If any tests failed, check the error messages and ml_service.py logs.")
