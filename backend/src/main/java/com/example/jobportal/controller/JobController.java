package com.example.jobportal.controller;

import com.example.jobportal.model.Job;
import com.example.jobportal.model.User;
import com.example.jobportal.model.ApprovalStatus;
import com.example.jobportal.repository.JobRepository;
import com.example.jobportal.repository.UserRepository;
import com.example.jobportal.security.UserPrincipal;
import com.example.jobportal.dto.MessageResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/jobs")
@CrossOrigin(origins = "http://localhost:3000")
public class JobController {
    
    @Autowired
    private JobRepository jobRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    // Get all approved jobs (public) or all jobs if admin
    @GetMapping
    public List<Job> getAllJobs(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            User user = userRepository.findById(userPrincipal.getId()).orElse(null);
            
            // Admin sees all jobs
            if (user != null && user.getRole().name().equals("ADMIN")) {
                return jobRepository.findAll();
            }
            
            // Employers see their own jobs (approved or not) + all approved jobs
            if (user != null && user.getRole().name().equals("EMPLOYER")) {
                return jobRepository.findByPostedByOrApprovalStatus(user, ApprovalStatus.APPROVED);
            }
        }
        
        // Public and job seekers see only approved jobs
        return jobRepository.findByApprovalStatus(ApprovalStatus.APPROVED);
    }
    
    // Get job by ID (public for approved jobs)
    @GetMapping("/{id}")
    public ResponseEntity<Job> getJobById(@PathVariable Long id, Authentication authentication) {
        Optional<Job> job = jobRepository.findById(id);
        
        if (!job.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        
        Job foundJob = job.get();
        
        // Check if user can view this job
        if (foundJob.getApprovalStatus() != ApprovalStatus.APPROVED) {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.notFound().build();
            }
            
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            User user = userRepository.findById(userPrincipal.getId()).orElse(null);
            
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
        Job savedJob = jobRepository.save(job);
        
        return ResponseEntity.ok(new MessageResponse("Job posted successfully! It will be visible after admin approval."));
    }
    
    // Update job (employer who posted it only)
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('EMPLOYER')")
    public ResponseEntity<Job> updateJob(@PathVariable Long id, @RequestBody Job jobDetails, 
                                       Authentication authentication) {
        Optional<Job> jobOptional = jobRepository.findById(id);
        if (!jobOptional.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        
        Job job = jobOptional.get();
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        
        // Check if the user is the one who posted the job
        if (!job.getPostedBy().getId().equals(userPrincipal.getId())) {
            return ResponseEntity.status(403).build();
        }
        
        job.setTitle(jobDetails.getTitle());
        job.setLocation(jobDetails.getLocation());
        job.setDescription(jobDetails.getDescription());
        job.setJobType(jobDetails.getJobType());
        job.setSalary(jobDetails.getSalary());
        job.setRequirements(jobDetails.getRequirements());
        
        // Reset approval status when job is edited
        if (job.getApprovalStatus() == ApprovalStatus.APPROVED) {
            job.setApprovalStatus(ApprovalStatus.PENDING);
            job.setApprovedBy(null);
            job.setApprovedDate(null);
        }
        
        Job updatedJob = jobRepository.save(job);
        return ResponseEntity.ok(updatedJob);
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
            return ResponseEntity.status(403).build();
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
}