package com.example.jobportal.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.jobportal.dto.MessageResponse;
import com.example.jobportal.model.ApprovalStatus;
import com.example.jobportal.model.Job;
import com.example.jobportal.model.User;
import com.example.jobportal.repository.JobRepository;
import com.example.jobportal.repository.UserRepository;
import com.example.jobportal.security.UserPrincipal;

@RestController
@RequestMapping("/api/jobs")
public class JobController {
    
    @Autowired
    private JobRepository jobRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    // Get all approved jobs (public)
    @GetMapping
    public ResponseEntity<List<Job>> getAllJobs() {
        try {
            List<Job> jobs = jobRepository.findByApprovalStatus(ApprovalStatus.APPROVED);
            if (jobs == null) {
                jobs = new ArrayList<>();
            }
            return ResponseEntity.ok(jobs);
        } catch (Exception e) {
            System.err.println("Error fetching jobs: " + e.getMessage());
            return ResponseEntity.ok(new ArrayList<>());
        }
    }
    
    // Get job by id (public for approved, restricted for pending/rejected)
    @GetMapping("/{id}")
    public ResponseEntity<Job> getJobById(@PathVariable Long id, Authentication authentication) {
        Optional<Job> jobOptional = jobRepository.findById(id);
        if (!jobOptional.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        
        Job foundJob = jobOptional.get();
        
        // If job is not approved, check permissions
        if (foundJob.getApprovalStatus() != ApprovalStatus.APPROVED) {
            User user = null;
            if (authentication != null) {
                UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
                user = userRepository.findById(userPrincipal.getId()).orElse(null);
            }
            
            // Only admin or the employer who posted can see unapproved jobs
            if (user == null || 
                (!user.getRole().name().equals("ADMIN") && 
                 !foundJob.getPostedBy().getId().equals(user.getId()))) {
                return ResponseEntity.notFound().build();
            }
        }
        
        return ResponseEntity.ok(foundJob);
    }
    
    // Create new job (employers only) - starts as PENDING
    @PostMapping
    @PreAuthorize("hasAuthority('EMPLOYER')")
    public ResponseEntity<?> createJob(@RequestBody Job job, Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        User employer = userRepository.findById(userPrincipal.getId()).orElseThrow();
        
        job.setPostedBy(employer);
        job.setCompany(employer.getCompanyName());
        job.setApprovalStatus(ApprovalStatus.PENDING);
        job.setPostedDate(LocalDateTime.now());
        job.setActive(true);
        
        Job savedJob = jobRepository.save(job);
        
        return ResponseEntity.ok(new MessageResponse("Job posted successfully! It will be visible after admin approval."));
    }
    
    // Update job (employer who posted it only)
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('EMPLOYER')")
    public ResponseEntity<?> updateJob(@PathVariable Long id, @RequestBody Job jobDetails, 
                                       Authentication authentication) {
        Optional<Job> jobOptional = jobRepository.findById(id);
        if (!jobOptional.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        
        Job job = jobOptional.get();
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        
        // Check if the user is the one who posted the job
        if (!job.getPostedBy().getId().equals(userPrincipal.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("You can only edit your own job posts"));
        }
        
        // Update job fields
        job.setTitle(jobDetails.getTitle());
        job.setLocation(jobDetails.getLocation());
        job.setDescription(jobDetails.getDescription());
        job.setJobType(jobDetails.getJobType());
        job.setSalary(jobDetails.getSalary());
        job.setRequirements(jobDetails.getRequirements());
        
        // Reset approval status when job is edited (if it was previously approved)
        if (job.getApprovalStatus() == ApprovalStatus.APPROVED) {
            job.setApprovalStatus(ApprovalStatus.PENDING);
            job.setApprovedBy(null);
            job.setApprovedDate(null);
            job.setRejectionReason(null);
        }
        
        Job updatedJob = jobRepository.save(job);
        
        String message = job.getApprovalStatus() == ApprovalStatus.PENDING 
            ? "Job updated successfully! It will need admin approval again."
            : "Job updated successfully!";
            
        return ResponseEntity.ok(new MessageResponse(message));
    }
    
    // Delete job (employer who posted it or admin)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('EMPLOYER') or hasAuthority('ADMIN')")
    public ResponseEntity<?> deleteJob(@PathVariable Long id, Authentication authentication) {
        Optional<Job> jobOptional = jobRepository.findById(id);
        if (!jobOptional.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        
        Job job = jobOptional.get();
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(userPrincipal.getId()).orElseThrow();
        
        // Check if user is admin or the employer who posted the job
        if (!user.getRole().name().equals("ADMIN") && 
            !job.getPostedBy().getId().equals(userPrincipal.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("You don't have permission to delete this job"));
        }
        
        jobRepository.delete(job);
        return ResponseEntity.ok(new MessageResponse("Job deleted successfully"));
    }
    
    // Search approved jobs (public)
    @GetMapping("/search")
    public List<Job> searchJobs(@RequestParam String query) {
        return jobRepository.searchApprovedJobs(query);
    }
    
    // Filter approved jobs (public)
    @GetMapping("/filter")
    public List<Job> filterJobs(
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String jobType) {
        
        if (location != null && jobType != null) {
            return jobRepository.findByLocationContainingAndJobTypeContainingAndApprovalStatus(
                location, jobType, ApprovalStatus.APPROVED);
        } else if (location != null) {
            return jobRepository.findByLocationContainingAndApprovalStatus(location, ApprovalStatus.APPROVED);
        } else if (jobType != null) {
            return jobRepository.findByJobTypeContainingAndApprovalStatus(jobType, ApprovalStatus.APPROVED);
        }
        return jobRepository.findByApprovalStatus(ApprovalStatus.APPROVED);
    }
    
    // Get jobs posted by current employer
    @GetMapping("/my-jobs")
    @PreAuthorize("hasAuthority('EMPLOYER')")
    public List<Job> getMyJobs(Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        User employer = userRepository.findById(userPrincipal.getId()).orElseThrow();
        return jobRepository.findByPostedBy(employer);
    }
    
    // Save job for later (job seekers only)
    @PostMapping("/{id}/save")
    @PreAuthorize("hasAuthority('JOB_SEEKER')")
    public ResponseEntity<?> saveJob(@PathVariable Long id, Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(userPrincipal.getId()).orElseThrow();
        
        Optional<Job> jobOptional = jobRepository.findById(id);
        if (!jobOptional.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        
        Job job = jobOptional.get();
        user.getSavedJobs().add(job);
        userRepository.save(user);
        
        return ResponseEntity.ok(new MessageResponse("Job saved successfully"));
    }
    
    // Remove saved job (job seekers only)
    @DeleteMapping("/{id}/save")
    @PreAuthorize("hasAuthority('JOB_SEEKER')")
    public ResponseEntity<?> unsaveJob(@PathVariable Long id, Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(userPrincipal.getId()).orElseThrow();
        
        Optional<Job> jobOptional = jobRepository.findById(id);
        if (!jobOptional.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        
        Job job = jobOptional.get();
        user.getSavedJobs().remove(job);
        userRepository.save(user);
        
        return ResponseEntity.ok(new MessageResponse("Job removed from saved list"));
    }
    
    // Get saved jobs (job seekers only)
    @GetMapping("/saved")
    @PreAuthorize("hasAuthority('JOB_SEEKER')")
    public Set<Job> getSavedJobs(Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(userPrincipal.getId()).orElseThrow();
        return user.getSavedJobs();
    }
}
