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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "http://localhost:3000")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminController {
    
    @Autowired
    private JobRepository jobRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    // Get all pending jobs for approval
    @GetMapping("/jobs/pending")
    public List<Job> getPendingJobs() {
        return jobRepository.findByApprovalStatus(ApprovalStatus.PENDING);
    }
    
    // Get all jobs with any status
    @GetMapping("/jobs/all")
    public List<Job> getAllJobs() {
        return jobRepository.findAll();
    }
    
    // Approve a job
    @PostMapping("/jobs/{id}/approve")
    public ResponseEntity<?> approveJob(@PathVariable Long id, Authentication authentication) {
        Job job = jobRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Job not found"));
        
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        User admin = userRepository.findById(userPrincipal.getId()).orElseThrow();
        
        job.setApprovalStatus(ApprovalStatus.APPROVED);
        job.setApprovedBy(admin);
        job.setApprovedDate(LocalDateTime.now());
        job.setRejectionReason(null);
        
        jobRepository.save(job);
        
        return ResponseEntity.ok(new MessageResponse("Job approved successfully"));
    }
    
    // Reject a job
    @PostMapping("/jobs/{id}/reject")
    public ResponseEntity<?> rejectJob(@PathVariable Long id, 
                                     @RequestBody Map<String, String> request,
                                     Authentication authentication) {
        Job job = jobRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Job not found"));
        
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        User admin = userRepository.findById(userPrincipal.getId()).orElseThrow();
        
        job.setApprovalStatus(ApprovalStatus.REJECTED);
        job.setApprovedBy(admin);
        job.setApprovedDate(LocalDateTime.now());
        job.setRejectionReason(request.get("reason"));
        
        jobRepository.save(job);
        
        return ResponseEntity.ok(new MessageResponse("Job rejected"));
    }
    
    // Get all users
    @GetMapping("/users")
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
    
    // Delete user
    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        
        userRepository.deleteById(id);
        return ResponseEntity.ok(new MessageResponse("User deleted successfully"));
    }
    
    // Get statistics
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        Map<String, Object> stats = Map.of(
            "totalJobs", jobRepository.count(),
            "pendingJobs", jobRepository.countByApprovalStatus(ApprovalStatus.PENDING),
            "approvedJobs", jobRepository.countByApprovalStatus(ApprovalStatus.APPROVED),
            "rejectedJobs", jobRepository.countByApprovalStatus(ApprovalStatus.REJECTED),
            "totalUsers", userRepository.count(),
            "employers", userRepository.countByRole("EMPLOYER"),
            "jobSeekers", userRepository.countByRole("JOB_SEEKER")
        );
        
        return ResponseEntity.ok(stats);
    }
}