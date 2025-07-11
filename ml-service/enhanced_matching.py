# enhanced_matching.py - Add this to your ml_service directory

import torch
import numpy as np
from typing import List, Dict, Tuple
from dataclasses import dataclass

@dataclass
class MatchExplanation:
    """Detailed explanation for why a job matches"""
    skill_overlap: float
    matched_skills: List[str]
    missing_skills: List[str]
    experience_match: str
    seniority_match: str
    location_match: str
    overall_reason: str

class EnhancedJobMatcher:
    """Enhanced matching logic for the GNN model"""
    
    def __init__(self, base_matcher):
        self.matcher = base_matcher  # The original GNN matcher
        self.skill_weight = 0.4
        self.experience_weight = 0.2
        self.seniority_weight = 0.2
        self.embedding_weight = 0.2
    
    def calculate_skill_overlap(self, member_skills: List[str], job_skills: List[str]) -> Tuple[float, List[str], List[str]]:
        """Calculate skill overlap with partial matching"""
        if not job_skills:
            return 1.0, member_skills, []  # If no skills required, perfect match
        
        member_skills_set = set(skill.lower() for skill in member_skills)
        job_skills_set = set(skill.lower() for skill in job_skills)
        
        # Direct matches
        matched_skills = member_skills_set.intersection(job_skills_set)
        
        # Fuzzy matching for similar skills
        skill_similarity_map = {
            'python': ['django', 'flask', 'fastapi', 'pandas', 'numpy'],
            'javascript': ['react', 'angular', 'vue', 'node.js', 'typescript'],
            'java': ['spring', 'hibernate', 'kotlin'],
            'sql': ['mysql', 'postgresql', 'mongodb'],
            'machine-learning': ['tensorflow', 'pytorch', 'scikit-learn', 'nlp', 'computer-vision'],
            'aws': ['cloud', 'azure', 'gcp'],
            'docker': ['kubernetes', 'containers'],
        }
        
        # Add fuzzy matches
        for member_skill in member_skills_set:
            for job_skill in job_skills_set:
                # Check if skills are related
                for main_skill, related in skill_similarity_map.items():
                    if member_skill == main_skill and job_skill in related:
                        matched_skills.add(job_skill)
                    elif job_skill == main_skill and member_skill in related:
                        matched_skills.add(job_skill)
        
        missing_skills = job_skills_set - matched_skills
        
        # Calculate overlap percentage
        if len(job_skills_set) > 0:
            overlap = len(matched_skills) / len(job_skills_set)
        else:
            overlap = 1.0
        
        return overlap, list(matched_skills), list(missing_skills)
    
    def calculate_experience_match(self, member_exp: int, job_exp: int, mode: str) -> Tuple[float, str]:
        """Calculate experience match with flexible thresholds"""
        diff = abs(member_exp - job_exp)
        
        if mode == "graduate_friendly":
            # Very flexible for graduates
            if member_exp <= 1 and job_exp <= 3:
                return 1.0, "Perfect for entry-level/graduates"
            elif diff <= 3:
                return 0.8, f"Close match ({diff} years difference)"
            else:
                return 0.5, f"Stretch opportunity ({diff} years difference)"
        
        elif mode == "flexible":
            # Allow ±2 years difference
            if diff == 0:
                return 1.0, "Perfect experience match"
            elif diff <= 2:
                return 0.8, f"Good match ({diff} years difference)"
            elif diff <= 4:
                return 0.6, f"Acceptable match ({diff} years difference)"
            else:
                return 0.3, f"Large gap ({diff} years difference)"
        
        else:  # strict
            # Strict matching
            if diff == 0:
                return 1.0, "Exact experience match"
            elif diff == 1:
                return 0.7, f"Close match ({diff} year difference)"
            else:
                return 0.3, f"Mismatch ({diff} years difference)"
    
    def calculate_seniority_match(self, member_level: str, job_level: str, mode: str) -> Tuple[float, str]:
        """Calculate seniority level match"""
        levels = ["entry", "junior", "mid", "senior"]
        
        if member_level not in levels:
            member_level = "junior"
        if job_level not in levels:
            job_level = "mid"
        
        member_idx = levels.index(member_level)
        job_idx = levels.index(job_level)
        diff = abs(member_idx - job_idx)
        
        if mode == "graduate_friendly":
            if member_level == "entry" and job_level in ["entry", "junior"]:
                return 1.0, "Great for new graduates"
            elif diff <= 1:
                return 0.8, "Good level match"
            else:
                return 0.5, "Growth opportunity"
        
        elif mode == "flexible":
            if diff == 0:
                return 1.0, "Perfect level match"
            elif diff == 1:
                return 0.7, "Adjacent level"
            else:
                return 0.4, "Different level"
        
        else:  # strict
            if diff == 0:
                return 1.0, "Exact level match"
            else:
                return 0.3, "Level mismatch"
    
    def get_enhanced_recommendations(self, member_id: str, top_k: int = 10, mode: str = "flexible") -> List[Dict]:
        """Get job recommendations with enhanced matching logic"""
        
        # Get member data
        member = self.matcher.graph.nodes['member'].get(member_id)
        if not member:
            return []
        
        # Get base GNN embeddings and scores
        with torch.no_grad():
            # Get node features from graph
            x, edge_index = self.matcher.graph.to_pyg_data()
            
            # Get embeddings from GNN
            embeddings = self.matcher.model(x, edge_index)
            
            # Get member embedding
            member_idx = self.matcher.graph.member_to_idx[member_id]
            member_embedding = embeddings[member_idx]
            
            # Calculate scores for all jobs
            job_scores = []
            
            for job_id, job in self.matcher.graph.nodes['job'].items():
                job_idx = self.matcher.graph.job_to_idx[job_id]
                job_embedding = embeddings[job_idx + len(self.matcher.graph.nodes['member'])]
                
                # GNN similarity score (cosine similarity)
                gnn_score = torch.cosine_similarity(
                    member_embedding.unsqueeze(0),
                    job_embedding.unsqueeze(0)
                ).item()
                
                # Calculate component scores
                skill_overlap, matched_skills, missing_skills = self.calculate_skill_overlap(
                    member.skills, job.required_skills
                )
                
                exp_score, exp_reason = self.calculate_experience_match(
                    member.experience_years, job.experience_required, mode
                )
                
                seniority_score, seniority_reason = self.calculate_seniority_match(
                    member.seniority_level, job.seniority_level, mode
                )
                
                # Combine scores with flexible thresholds based on mode
                if mode == "strict":
                    # All components must score well
                    if skill_overlap < 0.7 or exp_score < 0.7 or seniority_score < 0.7:
                        continue
                    min_threshold = 0.6
                elif mode == "flexible":
                    # Allow some weakness if others compensate
                    if skill_overlap < 0.4 and exp_score < 0.5:
                        continue
                    min_threshold = 0.4
                else:  # graduate_friendly
                    # Very lenient for graduates
                    if member.experience_years <= 1:
                        min_threshold = 0.3
                    else:
                        min_threshold = 0.35
                
                # Calculate final score
                final_score = (
                    self.skill_weight * skill_overlap +
                    self.experience_weight * exp_score +
                    self.seniority_weight * seniority_score +
                    self.embedding_weight * max(0, gnn_score)  # Ensure non-negative
                )
                
                # Apply threshold
                if final_score >= min_threshold:
                    # Create explanation
                    if skill_overlap >= 0.8:
                        overall_reason = "Excellent skill match"
                    elif skill_overlap >= 0.6:
                        overall_reason = "Good skill alignment"
                    elif member.experience_years <= 1 and job.experience_required <= 2:
                        overall_reason = "Great entry-level opportunity"
                    elif exp_score >= 0.8:
                        overall_reason = "Experience level matches well"
                    else:
                        overall_reason = "Potential fit with growth opportunity"
                    
                    explanation = MatchExplanation(
                        skill_overlap=skill_overlap,
                        matched_skills=matched_skills,
                        missing_skills=missing_skills[:3],  # Top 3 missing skills
                        experience_match=exp_reason,
                        seniority_match=seniority_reason,
                        location_match="Location flexible",
                        overall_reason=overall_reason
                    )
                    
                    job_scores.append({
                        'job_id': job.job_id,
                        'score': final_score,
                        'gnn_score': gnn_score,
                        'skill_score': skill_overlap,
                        'exp_score': exp_score,
                        'seniority_score': seniority_score,
                        'explanation': explanation,
                        'job': job
                    })
            
            # Sort by score and return top k
            job_scores.sort(key=lambda x: x['score'], reverse=True)
            
            # Format results
            results = []
            for item in job_scores[:top_k]:
                job = item['job']
                explanation = item['explanation']
                
                results.append({
                    'job_id': job.job_id,
                    'title': job.title,
                    'company': job.company,
                    'location': job.location,
                    'required_skills': job.required_skills,
                    'preferred_skills': job.preferred_skills,
                    'experience_required': job.experience_required,
                    'seniority_level': job.seniority_level,
                    'score': float(item['score']),
                    'match_details': {
                        'skill_match': f"{explanation.skill_overlap:.0%}",
                        'matched_skills': explanation.matched_skills,
                        'skills_to_learn': explanation.missing_skills,
                        'experience_fit': explanation.experience_match,
                        'level_fit': explanation.seniority_match,
                        'why_matched': explanation.overall_reason
                    }
                })
            
            return results

# Update the matching function to use enhanced logic
def get_recommendations_enhanced(self, member_id: str, top_k: int = 10, mode: str = "flexible") -> List[Dict]:
    """Enhanced recommendation function with better matching"""
    enhanced_matcher = EnhancedJobMatcher(self)
    return enhanced_matcher.get_enhanced_recommendations(member_id, top_k, mode)