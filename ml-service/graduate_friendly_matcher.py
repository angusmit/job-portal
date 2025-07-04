# Extension to the ImprovedJobMatcher for better graduate matching
import torch

def get_recommendations_graduate_friendly(self, member_id, top_k=5, mode="strict"):
    """
    Get recommendations with different matching modes:
    - "strict": Only exact seniority matches (original behavior)
    - "flexible": Allow adjacent seniority levels
    - "graduate_friendly": For entry-level, match by experience requirement instead of title
    - "experience_based": Match primarily by years of experience
    """
    self.model.eval()
    graph_data = self.graph.to_pyg_data()
    
    with torch.no_grad():
        member_embs, job_embs = self.model.encode(graph_data)
        
        if member_id not in self.graph.node_id_maps['member']:
            return []
        
        m_idx = self.graph.node_id_maps['member'][member_id]
        member = graph_data['nodes']['member'][m_idx]
        member_emb = member_embs[m_idx]
        
        scores = []
        for j_id, j_idx in self.graph.node_id_maps['job'].items():
            job = graph_data['nodes']['job'][j_idx]
            
            # Apply filtering based on mode
            should_include = False
            
            if mode == "strict":
                # Original strict seniority matching
                allowed_levels = {
                    "entry": ["entry", "junior"],
                    "junior": ["junior", "mid"],
                    "mid": ["mid", "senior"],
                    "senior": ["senior", "mid"]
                }
                should_include = job.seniority_level in allowed_levels.get(member.seniority_level, [])
                
            elif mode == "flexible":
                # Allow adjacent levels
                level_map = {"entry": 0, "junior": 1, "mid": 2, "senior": 3}
                member_level = level_map.get(member.seniority_level, 0)
                job_level = level_map.get(job.seniority_level, 0)
                should_include = abs(job_level - member_level) <= 1
                
            elif mode == "graduate_friendly":
                # Special handling for entry-level candidates
                if member.seniority_level == "entry":
                    # Include any job that requires ≤2 years experience
                    # regardless of its seniority label
                    should_include = job.experience_required <= 2
                else:
                    # Use flexible mode for non-entry candidates
                    level_map = {"entry": 0, "junior": 1, "mid": 2, "senior": 3}
                    member_level = level_map.get(member.seniority_level, 0)
                    job_level = level_map.get(job.seniority_level, 0)
                    should_include = abs(job_level - member_level) <= 1
                    
            elif mode == "experience_based":
                # Match based on experience range
                exp_diff = job.experience_required - member.experience_years
                
                # Allow applying to jobs that require up to 2 years more experience
                # and any job that requires less experience
                should_include = exp_diff <= 2
            
            if not should_include:
                continue
            
            # Calculate match score
            job_emb = job_embs[j_idx]
            raw_score = self.model.compute_match_score(
                member_emb, job_emb, member, job
            )
            
            # Apply sigmoid with temperature scaling
            temperature = 2.0
            base_score = torch.sigmoid(raw_score / temperature).item()
            
            # Additional scoring factors for graduate-friendly mode
            if mode == "graduate_friendly" and member.seniority_level == "entry":
                # Bonus for explicitly graduate-friendly job descriptions
                grad_keywords = ["graduate", "new grad", "entry level", "fresh", "0-1 year", "0-2 year", "training provided"]
                description_lower = job.description.lower()
                grad_friendly_bonus = sum(1 for keyword in grad_keywords if keyword in description_lower) * 0.05
                base_score = min(1.0, base_score + grad_friendly_bonus)
            
            scores.append({
                'job_id': j_id,
                'score': base_score,
                'raw_score': raw_score.item(),
                'job': job  # Include job object for analysis
            })
        
        # Sort by score
        sorted_scores = sorted(scores, key=lambda x: x['score'], reverse=True)
        
        # Normalize scores
        if len(sorted_scores) > 1:
            max_score = sorted_scores[0]['score']
            min_score = sorted_scores[-1]['score']
            score_range = max_score - min_score
            
            if score_range > 0.001:
                for item in sorted_scores:
                    normalized = (item['score'] - min_score) / score_range
                    item['score'] = 0.3 + (normalized * 0.65)
        
        # Remove job object before returning (to match original format)
        for item in sorted_scores:
            item.pop('job', None)
        
        return sorted_scores[:top_k]

def analyze_job_market_for_graduates(matcher, all_jobs):
    """Analyze which jobs are actually suitable for graduates"""
    print("\n" + "="*60)
    print("JOB MARKET ANALYSIS FOR GRADUATES")
    print("="*60)
    
    # Categorize jobs
    true_entry = []
    mislabeled_entry = []
    junior_but_grad_friendly = []
    not_suitable = []
    
    for job in all_jobs:
        is_entry_level = job.seniority_level == "entry"
        low_experience = job.experience_required <= 1
        grad_keywords = any(keyword in job.description.lower() 
                          for keyword in ["graduate", "new grad", "fresh", "entry level"])
        
        if is_entry_level and low_experience:
            true_entry.append(job)
        elif not is_entry_level and low_experience and grad_keywords:
            mislabeled_entry.append(job)
        elif job.seniority_level == "junior" and job.experience_required <= 2:
            junior_but_grad_friendly.append(job)
        else:
            not_suitable.append(job)
    
    print(f"\nTrue Entry-Level Jobs: {len(true_entry)}")
    for job in true_entry[:3]:
        print(f"  - {job.title} at {job.company}")
    
    print(f"\nMislabeled (Should be Entry): {len(mislabeled_entry)}")
    for job in mislabeled_entry[:3]:
        print(f"  - {job.title} ({job.seniority_level}) at {job.company}")
    
    print(f"\nJunior but Graduate-Friendly: {len(junior_but_grad_friendly)}")
    for job in junior_but_grad_friendly[:3]:
        print(f"  - {job.title} at {job.company} ({job.experience_required} years)")
    
    print(f"\nNot Suitable for New Graduates: {len(not_suitable)}")

def compare_matching_modes(matcher, test_graduate):
    """Compare different matching modes for a graduate"""
    print("\n" + "="*60)
    print("COMPARING MATCHING MODES FOR GRADUATE")
    print("="*60)
    
    modes = ["strict", "flexible", "graduate_friendly", "experience_based"]
    
    for mode in modes:
        print(f"\n{mode.upper()} MODE:")
        print("-" * 40)
        
        # Add the method to the matcher instance
        matcher.get_recommendations_graduate_friendly = get_recommendations_graduate_friendly.__get__(matcher)
        
        recs = matcher.get_recommendations_graduate_friendly(
            test_graduate.member_id, 
            top_k=5, 
            mode=mode
        )
        
        if not recs:
            print("  No matches found!")
        else:
            print(f"  Found {len(recs)} matches:")
            for i, rec in enumerate(recs, 1):
                # Find the job
                job = next((j for j in matcher.graph.nodes['job'].values() 
                          if matcher.graph.node_id_maps['job'][j.job_id] == 
                          list(matcher.graph.node_id_maps['job'].keys())[
                              list(matcher.graph.node_id_maps['job'].values()).index(
                                  matcher.graph.node_id_maps['job'][rec['job_id']]
                              )
                          ]), None)
                
                if job:
                    print(f"  {i}. {job.title} ({job.seniority_level}, "
                          f"{job.experience_required} yrs) - Score: {rec['score']:.3f}")

# Example usage
def test_graduate_matching():
    """Test the graduate-friendly matching"""
    from improved_cv_matcher import ImprovedJobMatcher, Member, Job
    
    # Create test data
    graduate = Member(
        "grad1", 
        "Recent CS graduate with strong foundation in algorithms and web development. "
        "Completed internship at tech company. Eager to learn and grow.",
        ["python", "javascript", "react", "algorithms", "git"],
        "CS Graduate",
        None,
        0,
        "San Francisco",
        seniority_level="entry"
    )
    
    # Mix of jobs that graduates might want
    jobs = [
        # Properly labeled entry-level
        Job("j1", "Graduate Software Engineer", "2024 graduate program for CS grads",
            "BigTech", ["python", "algorithms"], [], 0, "SF", seniority_level="entry"),
        
        # Junior role that's actually for new grads
        Job("j2", "Junior Developer", "Perfect for new grads! 0-1 year experience",
            "StartupA", ["javascript", "react"], [], 0, "SF", seniority_level="junior"),
        
        # Mid-level but accepts new grads
        Job("j3", "Software Engineer", "0-3 years experience. New grads welcome!",
            "TechCo", ["python", "javascript"], [], 1, "SF", seniority_level="mid"),
        
        # True junior role (1-2 years)
        Job("j4", "Junior Backend Developer", "1-2 years Python experience required",
            "WebCo", ["python", "django"], [], 2, "SF", seniority_level="junior"),
        
        # Not suitable for new grads
        Job("j5", "Senior Engineer", "5+ years required",
            "MegaCorp", ["python", "system-design"], [], 5, "SF", seniority_level="senior"),
    ]
    
    # Create and train matcher
    matcher = ImprovedJobMatcher()
    matcher.build_graph([graduate], jobs, [])
    
    # Quick train
    print("Training model...")
    matcher.train([graduate], jobs, epochs=10, batch_size=4)
    
    # Compare modes
    compare_matching_modes(matcher, graduate)
    
    # Analyze job market
    analyze_job_market_for_graduates(matcher, jobs)

if __name__ == "__main__":
    test_graduate_matching()