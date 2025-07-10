package com.example.jobportal.controller;

import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/match")
@CrossOrigin(origins = {"http://localhost:3000", "*"})
public class JobMatchingController {
    
    @Value("${ml-service.url:http://localhost:8000}")
    private String mlServiceUrl;
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @PostMapping("/upload-cv")
    public ResponseEntity<?> uploadCV(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "storageType", defaultValue = "temporary") String storageType,
            HttpSession session,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Please select a file to upload"));
        }
        
        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.toLowerCase().endsWith(".pdf") && 
            !filename.toLowerCase().endsWith(".docx") && 
            !filename.toLowerCase().endsWith(".txt"))) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Invalid file type. Please upload PDF, DOCX, or TXT files only"));
        }
        
        try {
            String sessionId = session.getId();
            
            // Prepare multipart request for ML service
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });
            body.add("session_id", sessionId);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            
            HttpEntity<MultiValueMap<String, Object>> requestEntity = 
                new HttpEntity<>(body, headers);
            
            // Call ML service
            ResponseEntity<Map> response = restTemplate.exchange(
                mlServiceUrl + "/upload_cv",
                HttpMethod.POST,
                requestEntity,
                Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> mlResponse = response.getBody();
                
                // Store member_id in session for subsequent calls
                String memberId = (String) mlResponse.get("member_id");
                session.setAttribute("currentMemberId", memberId);
                session.setAttribute("mlSessionId", sessionId);
                
                // Return success response with parsed CV data
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("data", mlResponse);
                result.put("message", "CV uploaded and analyzed successfully");
                
                return ResponseEntity.ok(result);
            } else {
                throw new RuntimeException("Invalid response from ML service");
            }
            
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode())
                .body(Map.of(
                    "success", false,
                    "error", "ML service error",
                    "details", e.getResponseBodyAsString()
                ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "success", false,
                    "error", "Error uploading CV",
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
        
        String memberId = (String) session.getAttribute("currentMemberId");
        String mlSessionId = (String) session.getAttribute("mlSessionId");
        
        if (memberId == null || mlSessionId == null) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Please upload your CV first"));
        }
        
        try {
            // Prepare request body for ML service
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("session_id", mlSessionId);
            requestBody.put("member_id", memberId);
            requestBody.put("mode", mode);
            requestBody.put("top_k", limit);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> requestEntity = 
                new HttpEntity<>(requestBody, headers);
            
            // Call ML service match_jobs endpoint
            ResponseEntity<Map> response = restTemplate.exchange(
                mlServiceUrl + "/match_jobs",
                HttpMethod.POST,
                requestEntity,
                Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return ResponseEntity.ok(response.getBody());
            } else {
                throw new RuntimeException("Invalid response from ML service");
            }
            
        } catch (HttpClientErrorException e) {
            String errorBody = e.getResponseBodyAsString();
            Map<String, Object> errorResponse = new HashMap<>();
            
            try {
                Map<String, Object> mlError = objectMapper.readValue(errorBody, Map.class);
                errorResponse.put("error", mlError.getOrDefault("detail", "Error getting job matches"));
            } catch (Exception parseEx) {
                errorResponse.put("error", "Error getting job matches");
            }
            
            errorResponse.put("details", errorBody);
            return ResponseEntity.status(e.getStatusCode()).body(errorResponse);
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error", "Error getting job matches",
                    "details", e.getMessage()
                ));
        }
    }
    
    @DeleteMapping("/clear-cv")
    public ResponseEntity<?> clearCV(HttpSession session) {
        session.removeAttribute("currentMemberId");
        session.removeAttribute("mlSessionId");
        
        return ResponseEntity.ok()
            .body(Map.of("message", "CV data cleared successfully"));
    }
    
    @PostMapping("/cv/upload")
    public ResponseEntity<?> uploadCVAlternate(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "storageType", defaultValue = "temporary") String storageType,
            HttpSession session,
            @AuthenticationPrincipal UserDetails userDetails) {
        return uploadCV(file, storageType, session, userDetails);
    }
    
    @GetMapping("/status")
    public ResponseEntity<?> getStatus(HttpSession session) {
        String memberId = (String) session.getAttribute("currentMemberId");
        String mlSessionId = (String) session.getAttribute("mlSessionId");
        
        Map<String, Object> status = new HashMap<>();
        status.put("hasCvUploaded", memberId != null);
        status.put("memberId", memberId);
        status.put("sessionId", session.getId());
        status.put("mlSessionId", mlSessionId);
        
        return ResponseEntity.ok(status);
    }
    
    @GetMapping("/health-check")
    public ResponseEntity<?> checkMLService() {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                mlServiceUrl + "/health",
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