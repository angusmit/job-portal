import torch
import torch.nn as nn
import torch.nn.functional as F
import numpy as np
from collections import defaultdict
from dataclasses import dataclass, field
from typing import List, Optional, Tuple, Dict
from datetime import datetime, timedelta
import math
import random
import warnings
import sys

# Suppress the TqdmWarning about IProgress
warnings.filterwarnings("ignore", message="IProgress not found")

# Use standard tqdm
from tqdm import tqdm

# Check and install sentence-transformers if needed
try:
    # Temporarily suppress stderr to avoid tqdm warnings from sentence-transformers
    import io
    old_stderr = sys.stderr
    sys.stderr = io.StringIO()
    
    from sentence_transformers import SentenceTransformer
    
    # Restore stderr
    sys.stderr = old_stderr
except ImportError:
    # Restore stderr before installing
    sys.stderr = old_stderr if 'old_stderr' in locals() else sys.stderr
    print("Installing sentence-transformers...")
    import subprocess
    subprocess.check_call([sys.executable, "-m", "pip", "install", "sentence-transformers"])
    
    # Try importing again with suppressed stderr
    old_stderr = sys.stderr
    sys.stderr = io.StringIO()
    from sentence_transformers import SentenceTransformer
    sys.stderr = old_stderr

@dataclass
class Member:
    member_id: str
    cv_text: str
    skills: List[str] = field(default_factory=list)
    title: Optional[str] = None
    company: Optional[str] = None
    experience_years: int = 0
    location: Optional[str] = None
    last_active: datetime = field(default_factory=datetime.now)
    seniority_level: str = "entry"

@dataclass
class Job:
    job_id: str
    title: str
    description: str
    company: str
    required_skills: List[str] = field(default_factory=list)
    preferred_skills: List[str] = field(default_factory=list)
    experience_required: int = 0
    location: Optional[str] = None
    posted_date: datetime = field(default_factory=datetime.now)
    job_type: str = "full-time"
    seniority_level: str = "entry"

@dataclass
class Interaction:
    member_id: str
    job_id: str
    interaction_type: str
    timestamp: datetime
    duration: Optional[int] = None
    outcome: Optional[str] = None

class AdvancedJobMarketplaceGraph:
    def __init__(self):
        self.node_id_maps = defaultdict(dict)
        self.edges = defaultdict(list)
        self.nodes = defaultdict(dict)

    def add_member_node(self, member: Member):
        if member.member_id not in self.node_id_maps['member']:
            idx = len(self.node_id_maps['member'])
            self.node_id_maps['member'][member.member_id] = idx
            self.nodes['member'][idx] = member

    def add_job_node(self, job: Job):
        if job.job_id not in self.node_id_maps['job']:
            idx = len(self.node_id_maps['job'])
            self.node_id_maps['job'][job.job_id] = idx
            self.nodes['job'][idx] = job

    def add_edge(self, src_type, src_id, edge_type, dst_type, dst_id):
        if src_id in self.node_id_maps[src_type] and dst_id in self.node_id_maps[dst_type]:
            src_idx = self.node_id_maps[src_type][src_id]
            dst_idx = self.node_id_maps[dst_type][dst_id]
            self.edges[(src_type, edge_type, dst_type)].append((src_idx, dst_idx))

    def to_pyg_data(self):
        return {
            'node_id_maps': self.node_id_maps,
            'nodes': self.nodes,
            'edge_index_dict': self.edges
        }

class ImprovedLinkSAGE(nn.Module):
    def __init__(self, hidden_dim=256, dropout=0.3):  # Increased dropout
        super().__init__()
        self.hidden_dim = hidden_dim
        self.encoder = SentenceTransformer('all-MiniLM-L6-v2')
        
        # Seniority level embeddings
        self.seniority_embedding = nn.Embedding(4, 32)
        self.seniority_map = {"entry": 0, "junior": 1, "mid": 2, "senior": 3}
        
        # Improved architecture
        self.member_proj = nn.Sequential(
            nn.Linear(386 + 32, hidden_dim),
            nn.BatchNorm1d(hidden_dim),
            nn.ReLU(),
            nn.Dropout(dropout),
            nn.Linear(hidden_dim, hidden_dim),
            nn.BatchNorm1d(hidden_dim),
            nn.ReLU()
        )
        
        self.job_proj = nn.Sequential(
            nn.Linear(386 + 32, hidden_dim),
            nn.BatchNorm1d(hidden_dim),
            nn.ReLU(),
            nn.Dropout(dropout),
            nn.Linear(hidden_dim, hidden_dim),
            nn.BatchNorm1d(hidden_dim),
            nn.ReLU()
        )
        
        # Attention mechanism
        self.attention = nn.MultiheadAttention(hidden_dim, num_heads=4, dropout=dropout)
        
        # More complex match network with additional features
        self.match_network = nn.Sequential(
            nn.Linear(hidden_dim * 3 + 5, 256),  # +5 for additional features
            nn.LayerNorm(256),
            nn.ReLU(),
            nn.Dropout(dropout),
            nn.Linear(256, 128),
            nn.LayerNorm(128),
            nn.ReLU(),
            nn.Dropout(dropout),
            nn.Linear(128, 64),
            nn.ReLU(),
            nn.Dropout(dropout),
            nn.Linear(64, 1)
        )
        
        self._init_weights()

    def _init_weights(self):
        for m in self.modules():
            if isinstance(m, nn.Linear):
                nn.init.xavier_uniform_(m.weight)  # Changed from kaiming
                if m.bias is not None:
                    nn.init.constant_(m.bias, 0)
            elif isinstance(m, (nn.BatchNorm1d, nn.LayerNorm)):
                nn.init.constant_(m.weight, 1)
                nn.init.constant_(m.bias, 0)
            elif isinstance(m, nn.Embedding):
                nn.init.normal_(m.weight, mean=0, std=0.1)

    def normalize_features(self, experience_years, timestamp):
        """Normalize numeric features"""
        exp_norm = torch.sigmoid(torch.tensor(experience_years / 10.0, dtype=torch.float32))
        current_time = datetime.now().timestamp()
        days_ago = (current_time - timestamp) / (24 * 3600)
        time_norm = 1.0 - torch.exp(torch.tensor(-days_ago / 30.0, dtype=torch.float32))
        return exp_norm, time_norm

    def encode(self, graph_data):
        member_embeddings = {}
        job_embeddings = {}

        # Process members
        member_indices = sorted(graph_data['nodes']['member'].keys())
        if member_indices:
            member_texts = []
            member_features = []
            member_seniorities = []
            
            for idx in member_indices:
                member = graph_data['nodes']['member'][idx]
                member_texts.append(member.cv_text)
                
                exp_norm, time_norm = self.normalize_features(
                    member.experience_years, 
                    member.last_active.timestamp()
                )
                member_features.append(torch.tensor([exp_norm, time_norm]))
                
                # Add seniority level
                seniority_idx = self.seniority_map.get(member.seniority_level, 0)
                member_seniorities.append(seniority_idx)
            
            # Batch encode texts
            text_embs = self.encoder.encode(member_texts, convert_to_tensor=True)
            member_features = torch.stack(member_features)
            
            # Get seniority embeddings
            seniority_tensor = torch.tensor(member_seniorities, dtype=torch.long)
            seniority_embs = self.seniority_embedding(seniority_tensor)
            
            # Combine all features
            combined = torch.cat([text_embs, member_features, seniority_embs], dim=1)
            projected = self.member_proj(combined)
            
            for i, idx in enumerate(member_indices):
                member_embeddings[idx] = projected[i]

        # Process jobs
        job_indices = sorted(graph_data['nodes']['job'].keys())
        if job_indices:
            job_texts = []
            job_features = []
            job_seniorities = []
            
            for idx in job_indices:
                job = graph_data['nodes']['job'][idx]
                job_texts.append(f"{job.title} {job.description}")
                
                exp_norm, time_norm = self.normalize_features(
                    job.experience_required,
                    job.posted_date.timestamp()
                )
                job_features.append(torch.tensor([exp_norm, time_norm]))
                
                # Add seniority level
                seniority_idx = self.seniority_map.get(job.seniority_level, 0)
                job_seniorities.append(seniority_idx)
            
            # Batch encode texts
            text_embs = self.encoder.encode(job_texts, convert_to_tensor=True)
            job_features = torch.stack(job_features)
            
            # Get seniority embeddings
            seniority_tensor = torch.tensor(job_seniorities, dtype=torch.long)
            seniority_embs = self.seniority_embedding(seniority_tensor)
            
            # Combine all features
            combined = torch.cat([text_embs, job_features, seniority_embs], dim=1)
            projected = self.job_proj(combined)
            
            for i, idx in enumerate(job_indices):
                job_embeddings[idx] = projected[i]

        return member_embeddings, job_embeddings

    def compute_match_score(self, member_emb, job_emb, member=None, job=None):
        """Enhanced matching with multiple features"""
        if member_emb.dim() == 1:
            member_emb = member_emb.unsqueeze(0)
        if job_emb.dim() == 1:
            job_emb = job_emb.unsqueeze(0)
        
        # L2 normalize
        member_norm = F.normalize(member_emb, p=2, dim=-1)
        job_norm = F.normalize(job_emb, p=2, dim=-1)
        
        # Cross-attention
        batch_size = member_norm.size(0)
        member_seq = member_norm.unsqueeze(1)
        job_seq = job_norm.unsqueeze(1)
        
        attended, _ = self.attention(member_seq, job_seq, job_seq)
        attended = attended.squeeze(1)
        
        # Calculate additional features
        additional_features = []
        
        if member is not None and job is not None:
            # 1. Seniority difference
            level_map = {"entry": 0, "junior": 1, "mid": 2, "senior": 3}
            member_level = level_map.get(member.seniority_level, 2)
            job_level = level_map.get(job.seniority_level, 2)
            seniority_diff = (job_level - member_level) / 3.0
            
            # 2. Experience difference (normalized)
            exp_diff = (job.experience_required - member.experience_years) / 10.0
            
            # 3. Skill overlap ratio
            member_skills = set(member.skills)
            job_required_skills = set(job.required_skills)
            if len(job_required_skills) > 0:
                skill_overlap = len(member_skills & job_required_skills) / len(job_required_skills)
            else:
                skill_overlap = 0.0
            
            # 4. Location match (1 if same, 0 otherwise)
            location_match = 1.0 if member.location == job.location else 0.0
            
            # 5. Cosine similarity between embeddings
            cosine_sim = F.cosine_similarity(member_norm, job_norm, dim=-1).item()
            
            additional_features = torch.tensor([
                seniority_diff, exp_diff, skill_overlap, location_match, cosine_sim
            ], dtype=torch.float32).unsqueeze(0)
        else:
            # Default features if member/job info not available
            additional_features = torch.zeros(batch_size, 5)
        
        # Combine all features
        combined = torch.cat([member_norm, job_norm, attended, additional_features], dim=-1)
        
        # Pass through match network
        score = self.match_network(combined)
        
        if batch_size == 1:
            return score.squeeze(0)
        return score

class ImprovedJobMatcher:
    def __init__(self):
        self.graph = AdvancedJobMarketplaceGraph()
        self.device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
        self.model = ImprovedLinkSAGE().to(self.device)
        self.optimizer = torch.optim.AdamW(
            self.model.parameters(), 
            lr=0.0003,  # Lower learning rate
            weight_decay=0.01
        )
        
        # Cosine annealing scheduler
        self.scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(
            self.optimizer, T_max=30, eta_min=0.00005
        )
        
        self.bce_loss = nn.BCEWithLogitsLoss()
        self.ranking_loss = nn.MarginRankingLoss(margin=1.0)  # Increased margin

    def build_graph(self, members, jobs, interactions):
        print("Building graph...")
        for m in members:
            self.graph.add_member_node(m)
        for j in jobs:
            self.graph.add_job_node(j)
        for i in interactions:
            self.graph.add_edge('member', i.member_id, i.interaction_type, 'job', i.job_id)
        print(f"Graph built with {len(members)} members and {len(jobs)} jobs")
        print(f"Using device: {self.device}")

    def create_training_triplets(self, members, jobs):
        """Create more diverse training triplets with seniority awareness"""
        triplets = []
        
        # Define seniority progression
        level_map = {"entry": 0, "junior": 1, "mid": 2, "senior": 3}
        
        for member in members:
            member_skills = set(member.skills)
            member_level = level_map.get(member.seniority_level, 0)
            
            # Score all jobs for this member
            job_scores = []
            for job in jobs:
                job_skills = set(job.required_skills)
                job_level = level_map.get(job.seniority_level, 0)
                
                # Calculate comprehensive match score
                skill_overlap = len(member_skills & job_skills) / max(len(job_skills), 1)
                exp_diff = abs(member.experience_years - job.experience_required)
                level_diff = job_level - member_level
                
                # Location bonus
                location_bonus = 0.2 if member.location == job.location else 0
                
                # Seniority compatibility score
                # Heavily penalize inappropriate seniority matches
                if level_diff < -1:  # Overqualified
                    seniority_score = 0.3 / (abs(level_diff) + 1)
                elif level_diff > 1:  # Underqualified
                    seniority_score = 0.2 / (level_diff)
                elif level_diff == 0:  # Perfect match
                    seniority_score = 1.0
                else:  # Adjacent level (acceptable)
                    seniority_score = 0.8
                
                # Composite score with heavy weight on seniority
                score = skill_overlap * 0.3 + \
                        (1 - exp_diff/10) * 0.2 + \
                        seniority_score * 0.4 + \
                        location_bonus * 0.1
                
                job_scores.append((job, score))
            
            # Sort jobs by score
            job_scores.sort(key=lambda x: x[1], reverse=True)
            
            # Create triplets focusing on seniority-appropriate matches
            n_jobs = len(job_scores)
            if n_jobs >= 2:
                # Get appropriate and inappropriate jobs
                appropriate_jobs = [(j, s) for j, s in job_scores 
                                  if abs(level_map[j.seniority_level] - member_level) <= 1]
                inappropriate_jobs = [(j, s) for j, s in job_scores 
                                    if abs(level_map[j.seniority_level] - member_level) > 1]
                
                # Easy triplets: appropriate vs inappropriate seniority
                for pos_job, pos_score in appropriate_jobs[:3]:
                    for neg_job, neg_score in inappropriate_jobs:
                        if pos_score > neg_score:
                            triplets.append((member, pos_job, neg_job))
                
                # Medium triplets: within appropriate jobs
                for i in range(len(appropriate_jobs)-1):
                    for j in range(i+1, min(i+3, len(appropriate_jobs))):
                        if appropriate_jobs[i][1] > appropriate_jobs[j][1]:
                            triplets.append((member, appropriate_jobs[i][0], appropriate_jobs[j][0]))
                
                # Hard triplets: adjacent scores within same seniority
                same_level_jobs = [(j, s) for j, s in job_scores 
                                 if j.seniority_level == member.seniority_level]
                for i in range(len(same_level_jobs)-1):
                    if same_level_jobs[i][1] > same_level_jobs[i+1][1]:
                        triplets.append((member, same_level_jobs[i][0], same_level_jobs[i+1][0]))
        
        return triplets

    def train(self, members, jobs, epochs=50, batch_size=8):
        """Train with improved loss and regularization"""
        graph_data = self.graph.to_pyg_data()
        
        # Create training triplets
        triplets = self.create_training_triplets(members, jobs)
        
        if not triplets:
            print("No training triplets found!")
            return
        
        print(f"Training with {len(triplets)} triplets")
        
        best_loss = float('inf')
        
        for epoch in range(epochs):
            self.model.train()
            epoch_loss = 0.0
            num_batches = 0
            
            # Shuffle triplets
            random.shuffle(triplets)
            
            # Process in batches
            for i in range(0, len(triplets), batch_size):
                batch_triplets = triplets[i:i+batch_size]
                
                # Get embeddings
                member_embs, job_embs = self.model.encode(graph_data)
                
                batch_loss = 0.0
                valid_count = 0
                
                for member, pos_job, neg_job in batch_triplets:
                    m_idx = self.graph.node_id_maps['member'].get(member.member_id)
                    pos_j_idx = self.graph.node_id_maps['job'].get(pos_job.job_id)
                    neg_j_idx = self.graph.node_id_maps['job'].get(neg_job.job_id)
                    
                    if m_idx is None or pos_j_idx is None or neg_j_idx is None:
                        continue
                    
                    member_emb = member_embs[m_idx]
                    pos_job_emb = job_embs[pos_j_idx]
                    neg_job_emb = job_embs[neg_j_idx]
                    
                    # Compute scores with full context
                    pos_score = self.model.compute_match_score(
                        member_emb, pos_job_emb, member, pos_job
                    )
                    neg_score = self.model.compute_match_score(
                        member_emb, neg_job_emb, member, neg_job
                    )
                    
                    # Ensure tensors have proper shape
                    if pos_score.dim() == 0:
                        pos_score = pos_score.unsqueeze(0)
                    if neg_score.dim() == 0:
                        neg_score = neg_score.unsqueeze(0)
                    
                    # Ranking loss
                    ones = torch.ones_like(pos_score)
                    rank_loss = self.ranking_loss(pos_score, neg_score, ones)
                    
                    # BCE loss with adjusted labels
                    # Use soft labels instead of hard 0/1
                    pos_label = torch.ones_like(pos_score) * 0.9  # Not perfect 1.0
                    neg_label = torch.ones_like(neg_score) * 0.1  # Not perfect 0.0
                    
                    bce_pos = self.bce_loss(pos_score, pos_label)
                    bce_neg = self.bce_loss(neg_score, neg_label)
                    
                    # Combined loss with emphasis on ranking
                    loss = rank_loss * 2.0 + bce_pos + bce_neg
                    batch_loss += loss
                    valid_count += 1
                
                # Backpropagate
                if valid_count > 0:
                    batch_loss = batch_loss / valid_count
                    
                    self.optimizer.zero_grad()
                    batch_loss.backward()
                    torch.nn.utils.clip_grad_norm_(self.model.parameters(), 1.0)
                    self.optimizer.step()
                    
                    epoch_loss += batch_loss.item()
                    num_batches += 1
            
            # Update scheduler
            self.scheduler.step()
            
            # Calculate average loss
            avg_loss = epoch_loss / max(num_batches, 1)
            current_lr = self.optimizer.param_groups[0]['lr']
            
            print(f"Epoch {epoch+1}/{epochs}, Loss: {avg_loss:.4f}, LR: {current_lr:.6f}")
            
            # Save best model
            if avg_loss < best_loss:
                best_loss = avg_loss

    def get_recommendations(self, member_id, top_k=5, allow_cross_level=False):
        """Get recommendations with better scoring and seniority filtering"""
        self.model.eval()
        graph_data = self.graph.to_pyg_data()
        
        with torch.no_grad():
            member_embs, job_embs = self.model.encode(graph_data)
            
            if member_id not in self.graph.node_id_maps['member']:
                return []
            
            m_idx = self.graph.node_id_maps['member'][member_id]
            member = graph_data['nodes']['member'][m_idx]
            member_emb = member_embs[m_idx]
            
            # Define seniority compatibility rules
            level_map = {"entry": 0, "junior": 1, "mid": 2, "senior": 3}
            member_level = level_map.get(member.seniority_level, 0)
            
            # Define allowed job levels for each member level
            # More flexible for entry-level to handle real-world mismatches
            allowed_levels = {
                "entry": ["entry", "junior", "mid"],  # Graduates can see up to mid-level (0-3 years)
                "junior": ["junior", "mid"],          # Junior can apply to junior and mid
                "mid": ["mid", "senior"],             # Mid can apply to mid and senior  
                "senior": ["senior", "mid"]           # Senior can apply to senior and mid
            }
            
            # Additional filtering based on experience years
            # This handles cases where job is labeled "mid" but asks for 0-2 years
            flexible_allowed_levels = allowed_levels.copy()
            
            # If member is entry-level but job requires ≤2 years, allow it
            if member.seniority_level == "entry":
                for j_id, j_idx in graph_data['node_id_maps']['job'].items():
                    job = graph_data['nodes']['job'][j_idx]
                    if job.experience_required <= 2:  # New grad friendly
                        # This job is accessible to entry level
                        continue
            
            scores = []
            for j_id, j_idx in self.graph.node_id_maps['job'].items():
                job = graph_data['nodes']['job'][j_idx]
                job_emb = job_embs[j_idx]
                
                # Skip jobs that don't match seniority requirements
                if not allow_cross_level and job.seniority_level not in allowed_levels.get(member.seniority_level, []):
                    continue
                
                # Get raw score with full context
                raw_score = self.model.compute_match_score(
                    member_emb, job_emb, member, job
                )
                
                # Apply sigmoid with temperature scaling
                temperature = 2.0  # Makes sigmoid less saturated
                base_score = torch.sigmoid(raw_score / temperature).item()
                
                # Apply additional penalty for seniority mismatch (if cross-level is allowed)
                job_level = level_map.get(job.seniority_level, 0)
                level_diff = job_level - member_level
                
                # Penalty calculation
                if level_diff < -1:  # Overqualified by more than 1 level
                    penalty = 0.7 ** abs(level_diff + 1)
                elif level_diff > 1:  # Underqualified by more than 1 level
                    penalty = 0.6 ** (level_diff - 1)
                else:
                    penalty = 1.0  # No penalty for adjacent levels
                
                final_score = base_score * penalty
                
                scores.append({
                    'job_id': j_id, 
                    'score': final_score,
                    'raw_score': raw_score.item(),
                    'seniority_match': job.seniority_level == member.seniority_level
                })
            
            # Sort by score
            sorted_scores = sorted(scores, key=lambda x: x['score'], reverse=True)
            
            # Normalize scores to spread them out
            if len(sorted_scores) > 1:
                max_score = sorted_scores[0]['score']
                min_score = sorted_scores[-1]['score']
                score_range = max_score - min_score
                
                if score_range > 0.001:  # Avoid division by zero
                    for item in sorted_scores:
                        # Rescale to 0.3-0.95 range
                        normalized = (item['score'] - min_score) / score_range
                        item['score'] = 0.3 + (normalized * 0.65)
            
            return sorted_scores[:top_k]

def create_comprehensive_dataset():
    """Create dataset with same structure as before"""
    members = [
        # Senior developers
        Member("m1", "Senior Python developer with 8 years experience. Expert in Django, FastAPI, and microservices. Led teams of 5-10 developers.",
               ["python", "django", "fastapi", "docker", "kubernetes", "aws"], "Senior Engineer", "TechCorp", 8, "SF", seniority_level="senior"),
        
        Member("m2", "Senior Full Stack developer. 6 years with React, Node.js, and cloud platforms. Strong system design skills.",
               ["javascript", "react", "node.js", "typescript", "aws", "mongodb"], "Senior Full Stack", "WebCo", 6, "NYC", seniority_level="senior"),
        
        # Mid-level developers
        Member("m3", "Backend developer with 3 years experience. Proficient in Java, Spring Boot, and MySQL. Some cloud experience.",
               ["java", "spring", "mysql", "docker", "git"], "Backend Developer", "FinTech Inc", 3, "Chicago", seniority_level="mid"),
        
        Member("m4", "Frontend developer with 4 years experience. React specialist with UI/UX skills. Mobile development with React Native.",
               ["react", "javascript", "css", "react-native", "figma"], "Frontend Developer", "DesignHub", 4, "LA", seniority_level="mid"),
        
        # Junior developers
        Member("m5", "Junior developer with 1 year experience. Working with Python and basic web development. Eager to learn.",
               ["python", "html", "css", "sql", "git"], "Junior Developer", "StartupABC", 1, "Austin", seniority_level="junior"),
        
        Member("m6", "Junior Java developer. 2 years experience with Spring applications. Basic understanding of microservices.",
               ["java", "spring", "sql", "git"], "Junior Backend", "TechStart", 2, "Seattle", seniority_level="junior"),
        
        # Entry level / Graduates
        Member("m7", "Recent CS graduate with strong academic background. Internship experience with Python and machine learning.",
               ["python", "machine-learning", "sql", "git"], "Graduate", None, 0, "Boston", seniority_level="entry"),
        
        Member("m8", "Bootcamp graduate specializing in web development. Projects with JavaScript and React.",
               ["javascript", "react", "html", "css", "node.js"], "Bootcamp Grad", None, 0, "Denver", seniority_level="entry"),
        
        Member("m9", "Self-taught programmer with 6 months experience. Built several web apps with Python and Flask.",
               ["python", "flask", "html", "javascript"], "Junior Developer", None, 0.5, "Phoenix", seniority_level="entry"),
        
        # Specialized roles
        Member("m10", "Data Engineer with 5 years experience. Expert in data pipelines, ETL, and big data technologies.",
               ["python", "spark", "airflow", "sql", "aws", "kafka"], "Data Engineer", "DataCorp", 5, "SF", seniority_level="senior"),
    ]
    
    jobs = [
        # Senior positions
        Job("j1", "Senior Backend Engineer", "Lead backend development for our microservices architecture. Design scalable systems.",
            "TechGiant", ["python", "django", "docker", "kubernetes", "aws"], ["redis", "kafka"], 6, "SF", seniority_level="senior"),
        
        Job("j2", "Senior Full Stack Engineer", "Build end-to-end features for our SaaS platform. Own projects from conception to deployment.",
            "SaaSCo", ["javascript", "react", "node.js", "mongodb"], ["typescript", "graphql"], 5, "NYC", seniority_level="senior"),
        
        Job("j3", "Staff Software Engineer", "Technical leadership role. Drive architecture decisions and mentor team members.",
            "MegaCorp", ["java", "spring", "kubernetes", "aws"], ["kafka", "redis"], 8, "Seattle", seniority_level="senior"),
        
        # Mid-level positions
        Job("j4", "Backend Developer", "Build and maintain RESTful APIs. Work with microservices and cloud infrastructure.",
            "FinTech Pro", ["java", "spring", "mysql", "docker"], ["kubernetes", "aws"], 3, "Chicago", seniority_level="mid"),
        
        Job("j5", "Frontend Developer", "Create beautiful, responsive web applications. Collaborate with design team.",
            "DesignFirst", ["react", "javascript", "css", "typescript"], ["react-native", "figma"], 3, "LA", seniority_level="mid"),
        
        Job("j6", "Full Stack Developer", "Work on both frontend and backend of our web application. Varied technology stack.",
            "StartupXYZ", ["python", "react", "sql", "docker"], ["aws", "redis"], 4, "Austin", seniority_level="mid"),
        
        # Junior positions
        Job("j7", "Junior Backend Developer", "Great opportunity for early-career developers. Training provided on our tech stack.",
            "GrowthCo", ["python", "sql", "git"], ["docker", "aws"], 1, "Denver", seniority_level="junior"),
        
        Job("j8", "Junior Frontend Developer", "Join our frontend team. Work with modern JavaScript frameworks.",
            "WebAgency", ["javascript", "react", "css", "html"], ["typescript"], 1, "Boston", seniority_level="junior"),
        
        Job("j9", "Junior Software Engineer", "Generalist role perfect for recent graduates. Rotation through different teams.",
            "BigTech", ["java", "python", "sql"], ["spring", "docker"], 2, "Seattle", seniority_level="junior"),
        
        # Entry level positions
        Job("j10", "Graduate Software Developer", "12-month graduate program. Comprehensive training in full-stack development.",
            "EnterpriseInc", ["python", "javascript", "sql"], ["react", "docker"], 0, "Chicago", seniority_level="entry"),
        
        Job("j11", "Entry Level Developer", "Perfect for bootcamp graduates. Focus on web development with mentorship.",
            "StartupHub", ["javascript", "html", "css"], ["react", "node.js"], 0, "Denver", seniority_level="entry"),  # Changed location
        
        Job("j12", "Software Engineer I", "Entry level position for CS graduates. Work on interesting problems with great mentorship.",
            "TechCorp", ["python", "git"], ["django", "react"], 0, "SF", seniority_level="entry"),
        
        # Specialized positions
        Job("j13", "Data Engineer", "Build data pipelines and ETL processes. Work with big data technologies.",
            "DataDriven", ["python", "spark", "sql", "airflow"], ["kafka", "aws"], 4, "NYC", seniority_level="mid"),
        
        Job("j14", "Machine Learning Engineer", "Implement ML models in production. Strong engineering skills required.",
            "AI Startup", ["python", "machine-learning", "docker", "aws"], ["tensorflow", "kubernetes"], 3, "SF", seniority_level="mid"),
        
        Job("j15", "DevOps Engineer", "Manage cloud infrastructure and CI/CD pipelines. Automate everything.",
            "CloudFirst", ["python", "docker", "kubernetes", "aws"], ["terraform", "jenkins"], 4, "Remote", seniority_level="mid"),
    ]
    
    interactions = [
        Interaction("m1", "j1", "applied", datetime.now() - timedelta(days=2)),
        Interaction("m2", "j2", "applied", datetime.now() - timedelta(days=1)),
        Interaction("m3", "j4", "applied", datetime.now() - timedelta(days=3)),
        Interaction("m4", "j5", "saved", datetime.now() - timedelta(days=2)),
        Interaction("m5", "j7", "applied", datetime.now() - timedelta(days=1)),
        Interaction("m6", "j9", "viewed", datetime.now() - timedelta(hours=12)),
        Interaction("m7", "j10", "applied", datetime.now() - timedelta(days=1)),
        Interaction("m8", "j11", "applied", datetime.now() - timedelta(hours=6)),
        Interaction("m10", "j13", "applied", datetime.now() - timedelta(hours=3)),
    ]
    
    return members, jobs, interactions

def main():
    """Run comprehensive job matching demo"""
    print("Creating comprehensive dataset...")
    members, jobs, interactions = create_comprehensive_dataset()
    
    # Initialize matcher
    matcher = ImprovedJobMatcher()
    matcher.build_graph(members, jobs, interactions)
    
    # Train model
    print("\nTraining model with seniority-aware matching...")
    matcher.train(members, jobs, epochs=50, batch_size=8)
    
    # Test recommendations
    print("\n" + "="*80)
    print("JOB RECOMMENDATIONS (With Seniority Filtering)")
    print("="*80)
    
    test_members = [
        ("m1", "Senior Python Developer"),
        ("m4", "Mid-level Frontend Developer"),
        ("m7", "Recent CS Graduate"),
        ("m8", "Bootcamp Graduate"),
        ("m10", "Data Engineer")
    ]
    
    for member_id, description in test_members:
        member = next(m for m in members if m.member_id == member_id)
        print(f"\n{description} ({member.member_id})")
        print(f"Skills: {', '.join(member.skills[:5])}")
        print(f"Experience: {member.experience_years} years")
        print(f"Seniority: {member.seniority_level}")
        print(f"Location: {member.location}")
        print("-" * 60)
        
        # Get recommendations with seniority filtering
        recommendations = matcher.get_recommendations(member_id, top_k=5, allow_cross_level=False)
        
        if not recommendations:
            print("No suitable positions found at appropriate seniority level.")
            continue
            
        for i, rec in enumerate(recommendations, 1):
            job = next(j for j in jobs if j.job_id == rec['job_id'])
            score = rec['score']
            
            # Determine match quality based on normalized score
            if score > 0.85:
                match_quality = "EXCELLENT"
            elif score > 0.70:
                match_quality = "GOOD"
            elif score > 0.55:
                match_quality = "FAIR"
            else:
                match_quality = "WEAK"
            
            # Show if it's a perfect seniority match
            seniority_indicator = " ✓" if rec.get('seniority_match', False) else ""
            
            print(f"\n{i}. {job.title} at {job.company}{seniority_indicator}")
            print(f"   Score: {score:.3f} ({match_quality})")
            print(f"   Level: {job.seniority_level}, Experience: {job.experience_required}+ years")
            print(f"   Location: {job.location}")
            print(f"   Skills: {', '.join(job.required_skills[:4])}")
    
    # Show what happens with cross-level matching allowed
    print("\n\n" + "="*80)
    print("EXAMPLE: Entry-level candidate WITH cross-level matching allowed")
    print("="*80)
    
    member = next(m for m in members if m.member_id == "m8")
    print(f"\nBootcamp Graduate (m8) - CROSS-LEVEL ALLOWED")
    print(f"Seniority: {member.seniority_level}")
    print("-" * 60)
    
    cross_level_recs = matcher.get_recommendations("m8", top_k=3, allow_cross_level=True)
    for i, rec in enumerate(cross_level_recs, 1):
        job = next(j for j in jobs if j.job_id == rec['job_id'])
        print(f"\n{i}. {job.title} at {job.company}")
        print(f"   Score: {rec['score']:.3f}")
        print(f"   Level: {job.seniority_level} {'⚠️ MISMATCH' if job.seniority_level not in ['entry', 'junior'] else '✓'}")

if __name__ == '__main__':
    main()