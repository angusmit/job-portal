package com.example.jobportal.controller;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.jobportal.dto.MessageResponse;
import com.example.jobportal.model.ApprovalStatus;
import com.example.jobportal.model.Job;
import com.example.jobportal.model.ProcessingStatus;
import com.example.jobportal.model.Resume;
import com.example.jobportal.model.User;
import com.example.jobportal.repository.JobRepository;
import com.example.jobportal.repository.ResumeRepository;
import com.example.jobportal.repository.UserRepository;
import com.example.jobportal.security.UserPrincipal;
import com.example.jobportal.service.CVMatchingService;
import com.example.jobportal.service.FileStorageService;
import com.example.jobportal.service.ResumeParserService;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/resume")
@CrossOrigin(origins = "http://localhost:3000")
public class ResumeController {
    
    @Autowired
    private ResumeRepository resumeRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private JobRepository jobRepository;
    
    @Autowired
    private FileStorageService fileStorageService;
    
    @Autowired
    private ResumeParserService resumeParserService;
    
    @Autowired
    private CVMatchingService cvMatchingService;
    
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    
    /**
     * Upload resume for parsing and temporary storage
     * Privacy-focused: Only stores in session by default
     */
    @PostMapping("/upload")
    @PreAuthorize("hasAuthority('JOB_SEEKER')")
    public ResponseEntity<?> uploadResume(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "permanent", defaultValue = "false") boolean permanent,
            Authentication authentication,
            HttpSession session) {
        
        try {
            // Validate file
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(new MessageResponse("Please select a file to upload"));
            }
            
            if (file.getSize() > MAX_FILE_SIZE) {
                return ResponseEntity.badRequest()
                    .body(new MessageResponse("File size exceeds 5MB limit"));
            }
            
            if (!fileStorageService.isValidFileType(file)) {
                return ResponseEntity.badRequest()
                    .body(new MessageResponse("Invalid file type. Allowed: PDF, DOC, DOCX, TXT, RTF"));
            }
            
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            User user = userRepository.findById(userPrincipal.getId()).orElseThrow();
            
            // Create resume record
            Resume resume = new Resume();
            resume.setUser(user);
            resume.setFileName(file.getOriginalFilename());
            resume.setFileType(file.getContentType());
            resume.setFileSize(file.getSize());
            resume.setSessionId(session.getId());
            resume.setTemporaryOnly(!permanent);
            resume.setStatus(ProcessingStatus.PROCESSING);
            
            // Store file
            String filePath;
            if (permanent && user.isActive()) {
                filePath = fileStorageService.storePermanentFile(file, user.getId());
                resume.setExpiresAt(null); // No expiry for permanent files
            } else {
                filePath = fileStorageService.storeTemporaryFile(file, session.getId());
                resume.setExpiresAt(LocalDateTime.now().plusHours(1)); // Expire after 1 hour
            }
            
            // Parse resume
            Map<String, Object> parsedData = resumeParserService.parseResume(
                new ByteArrayInputStream(file.getBytes()), 
                file.getOriginalFilename()
            );
            
            // Update resume with parsed data
            resume.setExtractedText((String) parsedData.getOrDefault("rawText", ""));
            resume.setParsedData(new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(parsedData));
            resume.setSkills(String.join(", ", (List<String>) parsedData.getOrDefault("skills", new ArrayList<>())));
            resume.setExperienceYears((Double) parsedData.getOrDefault("experienceYears", 0.0));
            resume.setEducationLevel((String) parsedData.getOrDefault("educationLevel", "Not Specified"));
            resume.setProcessedAt(LocalDateTime.now());
            resume.setStatus(ProcessingStatus.COMPLETED);
            
            Resume savedResume = resumeRepository.save(resume);
            
            // Store resume ID in session for quick access
            session.setAttribute("currentResumeId", savedResume.getId());
            
            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put("resumeId", savedResume.getId());
            response.put("fileName", savedResume.getFileName());
            response.put("skills", parsedData.get("skills"));
            response.put("experience", savedResume.getExperienceYears());
            response.put("education", savedResume.getEducationLevel());
            response.put("temporaryOnly", savedResume.isTemporaryOnly());
            response.put("expiresAt", savedResume.getExpiresAt());
            response.put("message", "Resume uploaded and parsed successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                .body(new MessageResponse("Failed to process resume: " + e.getMessage()));
        }
    }
    
    /**
     * Match resume against a specific job
     */
    @PostMapping("/match/{jobId}")
    @PreAuthorize("hasAuthority('JOB_SEEKER')")
    public ResponseEntity<?> matchResumeWithJob(
            @PathVariable Long jobId,
            @RequestParam(value = "resumeId", required = false) Long resumeId,
            Authentication authentication,
            HttpSession session) {
        
        try {
            // Get resume ID from parameter or session
            if (resumeId == null) {
                resumeId = (Long) session.getAttribute("currentResumeId");
            }
            
            if (resumeId == null) {
                return ResponseEntity.badRequest()
                    .body(new MessageResponse("No resume found. Please upload your resume first."));
            }
            
            Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new RuntimeException("Resume not found"));
            
            Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found"));
            
            // Check if job is approved
            if (job.getApprovalStatus() != ApprovalStatus.APPROVED) {
                return ResponseEntity.badRequest()
                    .body(new MessageResponse("This job is not available"));
            }
            
            // Calculate match
            Map<String, Object> matchResult = cvMatchingService.calculateMatch(resume, job);
            
            // Add job details to response
            matchResult.put("jobTitle", job.getTitle());
            matchResult.put("company", job.getCompany());
            matchResult.put("jobId", job.getId());
            
            return ResponseEntity.ok(matchResult);
            
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                .body(new MessageResponse("Failed to calculate match: " + e.getMessage()));
        }
    }
    
    /**
     * Get best matching jobs for uploaded resume
     */
    @GetMapping("/match/top-jobs")
    @PreAuthorize("hasAuthority('JOB_SEEKER')")
    public ResponseEntity<?> getTopMatchingJobs(
            @RequestParam(value = "limit", defaultValue = "10") int limit,
            Authentication authentication,
            HttpSession session) {
        
        try {
            Long resumeId = (Long) session.getAttribute("currentResumeId");
            if (resumeId == null) {
                return ResponseEntity.badRequest()
                    .body(new MessageResponse("No resume found. Please upload your resume first."));
            }
            
            Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new RuntimeException("Resume not found"));
            
            // Get all approved jobs
            List<Job> approvedJobs = jobRepository.findByApprovalStatus(ApprovalStatus.APPROVED);
            
            // Calculate match scores for all jobs
            List<Map<String, Object>> matches = new ArrayList<>();
            for (Job job : approvedJobs) {
                Map<String, Object> matchResult = cvMatchingService.calculateMatch(resume, job);
                matchResult.put("job", job);
                matches.add(matchResult);
            }
            
            // Sort by match score descending
            matches.sort((a, b) -> {
                Double scoreA = (Double) a.get("matchScore");
                Double scoreB = (Double) b.get("matchScore");
                return scoreB.compareTo(scoreA);
            });
            
            // Return top matches
            List<Map<String, Object>> topMatches = matches.stream()
                .limit(limit)
                .collect(java.util.stream.Collectors.toList());
            
            return ResponseEntity.ok(topMatches);
            
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                .body(new MessageResponse("Failed to find matching jobs: " + e.getMessage()));
        }
    }
    
    /**
     * Delete resume and associated files
     */
    @DeleteMapping("/{resumeId}")
    @PreAuthorize("hasAuthority('JOB_SEEKER')")
    public ResponseEntity<?> deleteResume(
            @PathVariable Long resumeId,
            Authentication authentication) {
        
        try {
            Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new RuntimeException("Resume not found"));
            
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            
            // Check ownership
            if (!resume.getUser().getId().equals(userPrincipal.getId())) {
                return ResponseEntity.status(403)
                    .body(new MessageResponse("You don't have permission to delete this resume"));
            }
            
            // Delete file if it exists
            if (resume.getSessionId() != null) {
                fileStorageService.deleteSessionFiles(resume.getSessionId());
            }
            
            // Delete resume record
            resumeRepository.delete(resume);
            
            return ResponseEntity.ok(new MessageResponse("Resume deleted successfully"));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(new MessageResponse("Failed to delete resume: " + e.getMessage()));
        }
    }
    
    /**
     * Get user's resumes
     */
    @GetMapping("/my-resumes")
    @PreAuthorize("hasAuthority('JOB_SEEKER')")
    public ResponseEntity<?> getMyResumes(Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(userPrincipal.getId()).orElseThrow();
        
        List<Resume> resumes = resumeRepository.findByUserAndStatusNot(user, ProcessingStatus.EXPIRED);
        
        // Convert to DTOs to avoid exposing sensitive data
        List<Map<String, Object>> resumeDTOs = resumes.stream()
            .map(resume -> {
                Map<String, Object> dto = new HashMap<>();
                dto.put("id", resume.getId());
                dto.put("fileName", resume.getFileName());
                dto.put("uploadedAt", resume.getUploadedAt());
                dto.put("skills", resume.getSkills());
                dto.put("experienceYears", resume.getExperienceYears());
                dto.put("educationLevel", resume.getEducationLevel());
                dto.put("temporaryOnly", resume.isTemporaryOnly());
                dto.put("expiresAt", resume.getExpiresAt());
                return dto;
            })
            .collect(java.util.stream.Collectors.toList());
        
        return ResponseEntity.ok(resumeDTOs);
    }
}