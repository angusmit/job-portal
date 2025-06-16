package com.example.jobportal.controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.jobportal.dto.MessageResponse;
import com.example.jobportal.model.ApprovalStatus;
import com.example.jobportal.model.Job;
import com.example.jobportal.model.User;
import com.example.jobportal.model.UserRole;
import com.example.jobportal.repository.JobRepository;
import com.example.jobportal.repository.UserRepository;
import com.example.jobportal.security.UserPrincipal;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "http://localhost:3000")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminController {
    
    @Autowired
    private JobRepository jobRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    // ========== JOB APPROVAL ENDPOINTS ==========
    
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
        job.setApprovedBy(admin.getUsername());
        job.setApprovedDate(LocalDateTime.now());
        job.setRejectionReason(null);
        
        jobRepository.save(job);
        
        return ResponseEntity.ok(new MessageResponse("Job approved successfully"));
    }
    
    // Reject a job
    @PostMapping("/jobs/{id}/reject")
    public ResponseEntity<?> rejectJob(@PathVariable Long id, 
                                     @RequestBody Map<String, String> payload,
                                     Authentication authentication) {
        Job job = jobRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Job not found"));
        
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        User admin = userRepository.findById(userPrincipal.getId()).orElseThrow();
        
        job.setApprovalStatus(ApprovalStatus.REJECTED);
        job.setApprovedBy(admin.getUsername());
        job.setApprovedDate(LocalDateTime.now());
        job.setRejectionReason(payload.get("reason"));
        
        jobRepository.save(job);
        
        return ResponseEntity.ok(new MessageResponse("Job rejected"));
    }
    
    // Get job statistics
    @GetMapping("/jobs/stats")
    public ResponseEntity<?> getJobStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", jobRepository.count());
        stats.put("pending", jobRepository.countByApprovalStatus(ApprovalStatus.PENDING));
        stats.put("approved", jobRepository.countByApprovalStatus(ApprovalStatus.APPROVED));
        stats.put("rejected", jobRepository.countByApprovalStatus(ApprovalStatus.REJECTED));
        
        return ResponseEntity.ok(stats);
    }
    
    // ========== USER MANAGEMENT ENDPOINTS ==========
    
    // Get all users
    @GetMapping("/users")
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
    
    // Get users by role
    @GetMapping("/users/role/{role}")
    public List<User> getUsersByRole(@PathVariable String role) {
        UserRole userRole = UserRole.valueOf(role.toUpperCase());
        return userRepository.findByRole(userRole);
    }
    
    // Get user by ID
    @GetMapping("/users/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        return userRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    // Update user (admin can update any user)
    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, 
                                      @RequestBody Map<String, Object> updates) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Update basic fields
        if (updates.containsKey("email")) {
            user.setEmail((String) updates.get("email"));
        }
        if (updates.containsKey("firstName")) {
            user.setFirstName((String) updates.get("firstName"));
        }
        if (updates.containsKey("lastName")) {
            user.setLastName((String) updates.get("lastName"));
        }
        if (updates.containsKey("phoneNumber")) {
            user.setPhoneNumber((String) updates.get("phoneNumber"));
        }
        
        // Update role-specific fields
        if (user.getRole() == UserRole.EMPLOYER) {
            if (updates.containsKey("companyName")) {
                user.setCompanyName((String) updates.get("companyName"));
            }
            if (updates.containsKey("companyDescription")) {
                user.setCompanyDescription((String) updates.get("companyDescription"));
            }
            if (updates.containsKey("companyWebsite")) {
                user.setCompanyWebsite((String) updates.get("companyWebsite"));
            }
        }
        
        userRepository.save(user);
        
        return ResponseEntity.ok(new MessageResponse("User updated successfully"));
    }
    
    // Enable/Disable user
    @PostMapping("/users/{id}/toggle-status")
    public ResponseEntity<?> toggleUserStatus(@PathVariable Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Toggle the active status (you'll need to add this field to User model)
        user.setActive(!user.isActive());
        userRepository.save(user);
        
        String status = user.isActive() ? "enabled" : "disabled";
        return ResponseEntity.ok(new MessageResponse("User " + status + " successfully"));
    }
    
    // Reset user password
    @PostMapping("/users/{id}/reset-password")
    public ResponseEntity<?> resetUserPassword(@PathVariable Long id, 
                                             @RequestBody Map<String, String> payload) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        String newPassword = payload.get("newPassword");
        if (newPassword == null || newPassword.length() < 6) {
            return ResponseEntity.badRequest()
                .body(new MessageResponse("Password must be at least 6 characters"));
        }
        
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        
        return ResponseEntity.ok(new MessageResponse("Password reset successfully"));
    }
    
    // Delete user (soft delete by deactivating)
    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Don't allow deleting admin users
        if (user.getRole() == UserRole.ADMIN) {
            return ResponseEntity.badRequest()
                .body(new MessageResponse("Cannot delete admin users"));
        }
        
        // Soft delete by deactivating
        user.setActive(false);
        userRepository.save(user);
        
        return ResponseEntity.ok(new MessageResponse("User deactivated successfully"));
    }
    
    // Get user statistics
    @GetMapping("/users/stats")
    public ResponseEntity<?> getUserStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", userRepository.count());
        stats.put("jobSeekers", userRepository.findByRole(UserRole.JOB_SEEKER).size());
        stats.put("employers", userRepository.findByRole(UserRole.EMPLOYER).size());
        stats.put("admins", userRepository.findByRole(UserRole.ADMIN).size());
        
        return ResponseEntity.ok(stats);
    }
}