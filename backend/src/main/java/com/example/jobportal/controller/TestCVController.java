// TestCVController.java - FOR DEVELOPMENT/TESTING ONLY
// Remove this in production!

package com.example.jobportal.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/test")
public class TestCVController {

    @Autowired
    private RestTemplate restTemplate;

    // Test endpoint that doesn't require authentication
    @PostMapping("/upload-cv-noauth")
    public ResponseEntity<?> uploadCVNoAuth(
            @RequestParam("file") MultipartFile file,
            HttpSession session) {
        
        try {
            System.out.println("Test CV Upload - File: " + file.getOriginalFilename());
            
            // Create multipart request to ML service
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });
            body.add("session_id", session.getId());
            
            HttpEntity<MultiValueMap<String, Object>> requestEntity = 
                new HttpEntity<>(body, headers);
            
            // Call ML service directly
            ResponseEntity<Map> response = restTemplate.exchange(
                "http://localhost:8000/parse_cv",
                HttpMethod.POST,
                requestEntity,
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> result = response.getBody();
                session.setAttribute("currentMemberId", result.get("member_id"));
                
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", result,
                    "message", "CV parsed successfully (test mode)"
                ));
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error", "Failed to process CV",
                    "details", e.getMessage()
                ));
        }
        
        return ResponseEntity.badRequest()
            .body(Map.of("error", "Failed to process CV"));
    }
    
    @GetMapping("/match-jobs-noauth")
    public ResponseEntity<?> getJobMatchesNoAuth(
            @RequestParam(defaultValue = "graduate_friendly") String mode,
            @RequestParam(defaultValue = "10") int limit,
            HttpSession session) {
        
        String memberId = (String) session.getAttribute("currentMemberId");
        
        if (memberId == null) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Please upload CV first"));
        }
        
        try {
            // Create request to ML service
            Map<String, Object> request = new HashMap<>();
            request.put("session_id", session.getId());
            request.put("member_id", memberId);
            request.put("mode", mode);
            request.put("top_k", limit);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                "http://localhost:8000/match_jobs",
                HttpMethod.POST,
                requestEntity,
                Map.class
            );
            
            return ResponseEntity.ok(response.getBody());
            
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error", "Failed to get matches",
                    "details", e.getMessage()
                ));
        }
    }
    
    // Test ML service connection
    @GetMapping("/check-ml-service")
    public ResponseEntity<?> checkMLService() {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                "http://localhost:8000/health",
                Map.class
            );
            
            return ResponseEntity.ok(Map.of(
                "ml_service_status", "connected",
                "health_response", response.getBody()
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "ml_service_status", "disconnected",
                "error", e.getMessage()
            ));
        }
    }
}
