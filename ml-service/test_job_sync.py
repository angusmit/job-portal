# test_job_sync.py - Test script to verify job synchronization
import requests
import json

def test_spring_boot_jobs():
    """Check what jobs are available in Spring Boot"""
    print("=== Checking Spring Boot Jobs ===")
    try:
        response = requests.get('http://localhost:8080/api/jobs')
        if response.status_code == 200:
            jobs = response.json()
            print(f"Found {len(jobs)} jobs in Spring Boot:")
            for job in jobs:
                print(f"  ID: {job['id']} - {job['title']} at {job['company']}")
            return jobs
        else:
            print(f"Error: {response.status_code}")
            return []
    except Exception as e:
        print(f"Failed to connect to Spring Boot: {e}")
        return []

def sync_ml_jobs():
    """Sync jobs from Spring Boot to ML service"""
    print("\n=== Syncing Jobs to ML Service ===")
    try:
        response = requests.post('http://localhost:8000/sync_jobs')
        if response.status_code == 200:
            data = response.json()
            print(f"Status: {data['status']}")
            print(f"Message: {data['message']}")
            print("Synced jobs:")
            for job in data['jobs']:
                print(f"  ID: {job['id']} - {job['title']}")
        else:
            print(f"Error: {response.status_code}")
    except Exception as e:
        print(f"Failed to sync jobs: {e}")

def check_ml_jobs():
    """Check what jobs are loaded in ML service"""
    print("\n=== Checking ML Service Jobs ===")
    try:
        response = requests.get('http://localhost:8000/loaded_jobs')
        if response.status_code == 200:
            data = response.json()
            print(f"Total jobs in ML service: {data['total']}")
            for job in data['jobs']:
                print(f"  ID: {job['job_id']} - {job['title']} at {job['company']}")
                print(f"    Skills: {', '.join(job['skills'])}")
        else:
            print(f"Error: {response.status_code}")
    except Exception as e:
        print(f"Failed to check ML jobs: {e}")

def test_job_matching(cv_file_path="Simple_CV_John_Doe.pdf"):
    """Test job matching with a CV"""
    print("\n=== Testing Job Matching ===")
    
    # First upload CV
    session_id = "test-sync-123"
    
    with open(cv_file_path, 'rb') as f:
        files = {'file': (cv_file_path, f, 'application/pdf')}
        data = {'session_id': session_id}
        
        response = requests.post(
            'http://localhost:8000/upload_cv',
            files=files,
            data=data
        )
        
        if response.status_code == 200:
            cv_data = response.json()
            member_id = cv_data['member_id']
            print(f"CV uploaded successfully. Member ID: {member_id}")
            
            # Test matching
            match_response = requests.post(
                'http://localhost:8000/match_jobs',
                json={
                    'session_id': session_id,
                    'member_id': member_id,
                    'mode': 'flexible',
                    'top_k': 10
                }
            )
            
            if match_response.status_code == 200:
                matches = match_response.json()
                print(f"\nFound {matches['total_matches']} matches:")
                for match in matches['matches']:
                    print(f"\n  Job ID: {match['job_id']}")
                    print(f"  Title: {match['title']}")
                    print(f"  Company: {match['company']}")
                    print(f"  Score: {match['score']:.2%}")
                    print(f"  -> View at: http://localhost:3000/jobs/{match['job_id']}")
            else:
                print(f"Matching failed: {match_response.status_code}")
        else:
            print(f"CV upload failed: {response.status_code}")

if __name__ == "__main__":
    print("Job Synchronization Test Script")
    print("================================\n")
    
    # Step 1: Check Spring Boot jobs
    spring_jobs = test_spring_boot_jobs()
    
    # Step 2: Sync to ML service
    if spring_jobs:
        sync_ml_jobs()
    
    # Step 3: Verify ML service has the jobs
    check_ml_jobs()
    
    # Step 4: Test matching (optional)
    # Uncomment if you have a test CV file
    # test_job_matching("path/to/your/cv.pdf")