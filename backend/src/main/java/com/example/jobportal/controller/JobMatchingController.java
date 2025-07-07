package com.example.jobportal.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.jobportal.service.MLIntegrationService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
public class JobMatchingController {

    private final MLIntegrationService mlIntegrationService;

    @PostMapping("/upload-cv")
    public ResponseEntity<?> uploadCV(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "storageType", defaultValue = "temporary") String storageType,
            HttpSession session,
            @AuthenticationPrincipal UserDetails userDetails) {

        try {
            // Log request
            System.out.println("CV Upload request from user: " + (userDetails != null ? userDetails.getUsername() : "anonymous"));
            System.out.println("File: " + file.getOriginalFilename());
            System.out.println("Storage type: " + storageType);

            String sessionId = session.getId();

            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Please upload a CV file"));
            }

            String filename = file.getOriginalFilename();
            if (!isValidFileType(filename)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Only PDF, DOCX, and TXT files are supported"));
            }

            // Parse CV using ML service
            MLIntegrationService.CVParseResponse cvData = mlIntegrationService.parseCv(file, sessionId);

            // Store member ID and storage type in session
            session.setAttribute("currentMemberId", cvData.memberId);
            session.setAttribute("cvStorageType", storageType);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", cvData,
                "message", "CV uploaded and parsed successfully"
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Error processing CV",
                "details", e.getMessage()
            ));
        }
    }

    @GetMapping("/jobs")
    public ResponseEntity<?> getJobMatches(
            @RequestParam(defaultValue = "graduate_friendly") String mode,
            @RequestParam(defaultValue = "10") int limit,
            HttpSession session,
            @AuthenticationPrincipal UserDetails userDetails) {

        String sessionId = session.getId();
        String memberId = (String) session.getAttribute("currentMemberId");

        System.out.println("Getting job matches for user: " + (userDetails != null ? userDetails.getUsername() : "anonymous"));
        System.out.println("Member ID: " + memberId);

        if (memberId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please upload your CV first"));
        }

        try {
            MLIntegrationService.JobMatchResponse matches = mlIntegrationService.getJobMatches(sessionId, memberId, mode, limit);
            return ResponseEntity.ok(matches);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Error getting job matches",
                "details", e.getMessage()
            ));
        }
    }

    @DeleteMapping("/clear-cv")
    public ResponseEntity<?> clearCV(HttpSession session) {
        String sessionId = session.getId();
        mlIntegrationService.clearSessionCVData(sessionId);
        session.removeAttribute("currentMemberId");
        session.removeAttribute("cvStorageType");

        return ResponseEntity.ok(Map.of("message", "CV data cleared successfully"));
    }

    // Optional: CV Screening Upload Endpoint
    @PostMapping("/cv/upload")
    public ResponseEntity<?> uploadCVScreening(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "storageType", defaultValue = "temporary") String storageType,
            HttpSession session,
            @AuthenticationPrincipal UserDetails userDetails) {

        return uploadCV(file, storageType, session, userDetails);
    }

    private boolean isValidFileType(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase();
        return lower.endsWith(".pdf") || lower.endsWith(".docx") || lower.endsWith(".txt");
    }
}

