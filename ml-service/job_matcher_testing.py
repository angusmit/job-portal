import torch
from datetime import datetime, timedelta
from dataclasses import dataclass, field
from typing import List, Optional
from improved_cv_matcher import ImprovedJobMatcher, Member, Job, Interaction
import random

# Import your matcher classes (assuming they're in a file called job_matcher.py)
# from job_matcher import ImprovedJobMatcher, Member, Job, Interaction

def create_test_scenarios():
    """Create comprehensive test scenarios for different match rates"""
    
    # Test Case 1: Perfect Match Scenario
    perfect_match_members = [
        Member("pm1", "Python developer with 3 years Django experience. Built REST APIs.",
               ["python", "django", "rest", "sql"], "Backend Dev", "TechCo", 3, "NYC", seniority_level="mid"),
    ]
    
    perfect_match_jobs = [
        Job("pj1", "Python Django Developer", "Build REST APIs with Django",
            "StartupX", ["python", "django", "rest", "sql"], [], 3, "NYC", seniority_level="mid"),
    ]
    
    # Test Case 2: Graduate/Entry Mismatch Scenario (Real-world problem)
    graduate_members = [
        Member("gm1", "Recent CS graduate, strong in algorithms and data structures. Internship at Google.",
               ["python", "java", "algorithms", "git"], "Graduate", None, 0, "SF", seniority_level="entry"),
        
        Member("gm2", "MSc Computer Science, thesis on machine learning. Looking for first full-time role.",
               ["python", "machine-learning", "tensorflow", "sql"], "Graduate Student", None, 0, "Boston", seniority_level="entry"),
        
        Member("gm3", "Bootcamp graduate with full-stack projects. 6-month internship experience.",
               ["javascript", "react", "node.js", "mongodb"], "Bootcamp Grad", None, 0.5, "Austin", seniority_level="entry"),
    ]
    
    # Mix of entry/junior titles that graduates might apply to
    graduate_jobs = [
        # True entry-level positions
        Job("gj1", "Graduate Software Engineer", "2024 graduate program",
            "BigTech", ["python", "java", "git"], [], 0, "SF", seniority_level="entry"),
        
        Job("gj2", "Junior Developer", "Great for new grads! We provide training.",
            "StartupY", ["javascript", "react", "git"], [], 0, "Austin", seniority_level="junior"),
        
        Job("gj3", "Software Engineer I", "Entry level position for recent graduates",
            "MegaCorp", ["python", "sql", "git"], [], 0, "Boston", seniority_level="entry"),
        
        # Mislabeled positions (common in real world)
        Job("gj4", "Software Developer", "Looking for fresh graduates with strong fundamentals",
            "TechCo", ["java", "algorithms", "git"], [], 0, "SF", seniority_level="junior"),
        
        Job("gj5", "Associate Software Engineer", "New grad position, 0-2 years experience",
            "FinTech", ["python", "sql", "git"], [], 1, "NYC", seniority_level="junior"),
        
        # Positions that require some experience
        Job("gj6", "Backend Developer", "1-2 years experience required",
            "WebCo", ["python", "django", "docker"], [], 2, "Remote", seniority_level="junior"),
    ]
    
    # Test Case 3: Poor Match Scenario
    poor_match_members = [
        Member("mm1", "Frontend developer specializing in React",
               ["react", "javascript", "css", "html"], "Frontend Dev", "UICompany", 3, "LA", seniority_level="mid"),
    ]
    
    poor_match_jobs = [
        Job("mj1", "Senior Data Scientist", "PhD required, 5+ years experience",
            "DataCorp", ["python", "machine-learning", "statistics", "phd"], [], 5, "NYC", seniority_level="senior"),
        
        Job("mj2", "DevOps Engineer", "Kubernetes and cloud infrastructure",
            "CloudCo", ["kubernetes", "docker", "aws", "terraform"], [], 4, "Seattle", seniority_level="mid"),
    ]
    
    # Test Case 4: Overqualified Scenario
    overqualified_members = [
        Member("om1", "Senior engineer with 10 years experience. Ex-Google staff engineer.",
               ["python", "java", "system-design", "leadership"], "Staff Engineer", "Google", 10, "SF", seniority_level="senior"),
    ]
    
    overqualified_jobs = [
        Job("oj1", "Junior Developer", "Fresh graduate position",
            "StartupZ", ["python", "git"], [], 0, "SF", seniority_level="junior"),
        
        Job("oj2", "Intern - Software Engineering", "Summer internship for students",
            "InternCo", ["python", "java"], [], 0, "SF", seniority_level="entry"),
    ]
    
    # Test Case 5: Career Transition Scenario
    transition_members = [
        Member("tm1", "Data analyst transitioning to data engineering. Strong SQL and Python.",
               ["sql", "python", "excel", "tableau"], "Data Analyst", "AnalyticsCo", 3, "Chicago", seniority_level="mid"),
    ]
    
    transition_jobs = [
        Job("tj1", "Junior Data Engineer", "Great for analysts moving to engineering",
            "DataCo", ["sql", "python", "airflow"], ["spark"], 1, "Chicago", seniority_level="junior"),
        
        Job("tj2", "Data Engineer", "SQL and Python required",
            "TechData", ["sql", "python", "spark", "airflow"], [], 3, "Chicago", seniority_level="mid"),
    ]
    
    return {
        "perfect_match": (perfect_match_members, perfect_match_jobs),
        "graduate_scenario": (graduate_members, graduate_jobs),
        "poor_match": (poor_match_members, poor_match_jobs),
        "overqualified": (overqualified_members, overqualified_jobs),
        "career_transition": (transition_members, transition_jobs),
    }

def test_matching_scenarios(matcher_class=None):
    """Test different matching scenarios and analyze results"""
    
    if matcher_class is None:
        # Import your matcher class here
        from improved_cv_matcher import ImprovedJobMatcher as matcher_class
    
    scenarios = create_test_scenarios()
    
    print("="*80)
    print("COMPREHENSIVE JOB MATCHING TEST RESULTS")
    print("="*80)
    
    for scenario_name, (members, jobs) in scenarios.items():
        print(f"\n\n{'='*60}")
        print(f"SCENARIO: {scenario_name.upper().replace('_', ' ')}")
        print(f"{'='*60}")
        
        # Create matcher and train
        matcher = matcher_class()
        
        # Build graph with scenario data
        all_members = members + create_dummy_members()  # Add dummy data for better training
        all_jobs = jobs + create_dummy_jobs()
        
        matcher.build_graph(all_members, all_jobs, [])
        
        # Quick training
        print(f"Training on {len(all_members)} members and {len(all_jobs)} jobs...")
        matcher.train(all_members, all_jobs, epochs=20, batch_size=8)
        
        # Test each member in the scenario
        for member in members:
            print(f"\n{'-'*50}")
            print(f"CANDIDATE: {member.title or 'Unknown'} ({member.member_id})")
            print(f"Level: {member.seniority_level}, Experience: {member.experience_years} years")
            print(f"Skills: {', '.join(member.skills[:4])}")
            print(f"\nRecommendations:")
            
            # Get recommendations with and without cross-level
            strict_recs = matcher.get_recommendations(member.member_id, top_k=5, allow_cross_level=False)
            
            if not strict_recs:
                print("  No matches found with strict seniority filtering!")
                
                # Try with cross-level allowed
                cross_recs = matcher.get_recommendations(member.member_id, top_k=3, allow_cross_level=True)
                if cross_recs:
                    print("\n  With cross-level matching:")
                    for i, rec in enumerate(cross_recs, 1):
                        job = next(j for j in all_jobs if j.job_id == rec['job_id'])
                        print(f"  {i}. {job.title} ({job.seniority_level}) - Score: {rec['score']:.3f}")
            else:
                for i, rec in enumerate(strict_recs, 1):
                    job = next(j for j in all_jobs if j.job_id == rec['job_id'])
                    
                    # Analyze match quality
                    skill_match = len(set(member.skills) & set(job.required_skills)) / max(len(job.required_skills), 1)
                    exp_match = 1 - abs(member.experience_years - job.experience_required) / 10
                    
                    print(f"\n  {i}. {job.title} at {job.company}")
                    print(f"     Score: {rec['score']:.3f}")
                    print(f"     Level: {job.seniority_level} vs {member.seniority_level}")
                    print(f"     Skill Match: {skill_match:.1%}, Experience Match: {exp_match:.1%}")

def create_dummy_members():
    """Create additional members for better training"""
    from datetime import datetime
    return [
        Member("d1", "Senior Python developer", ["python", "django"], "Senior Dev", "Co1", 7, "NYC", datetime.now(), "senior"),
        Member("d2", "Junior React developer", ["react", "javascript"], "Junior Dev", "Co2", 1, "LA", datetime.now(), "junior"),
        Member("d3", "Mid-level full stack", ["python", "react", "sql"], "Full Stack", "Co3", 4, "Austin", datetime.now(), "mid"),
        Member("d4", "Entry level graduate", ["java", "python"], "Graduate", None, 0, "Boston", datetime.now(), "entry"),
        Member("d5", "Senior data engineer", ["python", "spark", "sql"], "Data Eng", "Co5", 8, "SF", datetime.now(), "senior"),
    ]

def create_dummy_jobs():
    """Create additional jobs for better training"""
    from datetime import datetime
    return [
        Job("d1", "Senior Backend Engineer", "Python required", "Co1", ["python", "django"], [], 5, "NYC", datetime.now(), "full-time", "senior"),
        Job("d2", "Junior Frontend Dev", "React and JS", "Co2", ["react", "javascript"], [], 1, "LA", datetime.now(), "full-time", "junior"),
        Job("d3", "Mid Full Stack", "Python and React", "Co3", ["python", "react"], [], 3, "Austin", datetime.now(), "full-time", "mid"),
        Job("d4", "Graduate Developer", "For new grads", "Co4", ["python", "java"], [], 0, "Boston", datetime.now(), "full-time", "entry"),
        Job("d5", "Data Engineer", "Spark and SQL", "Co5", ["python", "spark", "sql"], [], 4, "SF", datetime.now(), "full-time", "mid"),
    ]

def test_custom_query(matcher, member_data, all_jobs):
    """Test a custom member query"""
    print("\n" + "="*60)
    print("CUSTOM QUERY TEST")
    print("="*60)
    
    # Create custom member
    custom_member = Member(
        member_id="custom1",
        cv_text=member_data['cv_text'],
        skills=member_data['skills'],
        title=member_data.get('title', 'Job Seeker'),
        company=member_data.get('company'),
        experience_years=member_data['experience'],
        location=member_data.get('location', 'Remote'),
        seniority_level=member_data['seniority']
    )
    
    # Add to graph
    matcher.graph.add_member_node(custom_member)
    
    print(f"\nCustom Profile:")
    print(f"Seniority: {custom_member.seniority_level}")
    print(f"Experience: {custom_member.experience_years} years")
    print(f"Skills: {', '.join(custom_member.skills)}")
    
    # Get recommendations
    recs = matcher.get_recommendations("custom1", top_k=10, allow_cross_level=False)
    
    print(f"\nTop Recommendations:")
    for i, rec in enumerate(recs, 1):
        job = next(j for j in all_jobs if j.job_id == rec['job_id'])
        print(f"{i}. {job.title} ({job.seniority_level}) - Score: {rec['score']:.3f}")

# Example usage
if __name__ == "__main__":
    # Run all test scenarios
    test_matching_scenarios()
    
    # Test a custom graduate profile
    print("\n\n" + "="*80)
    print("TESTING CUSTOM GRADUATE PROFILE")
    print("="*80)
    
    # This is how you'd test a specific profile
    custom_grad = {
        'cv_text': "Recent computer science graduate with internship experience at a Fortune 500 company. Strong foundation in algorithms and software development.",
        'skills': ['python', 'java', 'algorithms', 'git', 'sql'],
        'title': 'CS Graduate',
        'experience': 0,
        'seniority': 'entry',
        'location': 'San Francisco'
    }
    
    # You would load your trained matcher here
    # matcher = load_trained_matcher()
    # test_custom_query(matcher, custom_grad, all_jobs)