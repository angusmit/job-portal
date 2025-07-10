package com.example.jobportal.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import com.example.jobportal.model.Job;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class MLIntegrationService {
    
    @Value("${ml-service.url:http://localhost:8000}")
    private String mlServiceUrl;
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Response classes
    public static class CVParseResponse {
        private String memberId;
        private String extractedText;
        private List<String> skills;
        private int experienceYears;
        private String seniorityLevel;
        private String title;
        private String location;
        private String education;
        
        // Getters and setters
        public String getMemberId() { return memberId; }
        public void setMemberId(String memberId) { this.memberId = memberId; }
        
        public String getExtractedText() { return extractedText; }
        public void setExtractedText(String extractedText) { this.extractedText = extractedText; }
        
        public List<String> getSkills() { return skills; }
        public void setSkills(List<String> skills) { this.skills = skills; }
        
        public int getExperienceYears() { return experienceYears; }
        public void setExperienceYears(int experienceYears) { this.experienceYears = experienceYears; }
        
        public String getSeniorityLevel() { return seniorityLevel; }
        public void setSeniorityLevel(String seniorityLevel) { this.seniorityLevel = seniorityLevel; }
        
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        
        public String getEducation() { return education; }
        public void setEducation(String education) { this.education = education; }
    }
    
    public static class JobMatchResponse {
        private List<Map<String, Object>> matches;
        private int totalMatches;
        
        // Getters and setters
        public List<Map<String, Object>> getMatches() { return matches; }
        public void setMatches(List<Map<String, Object>> matches) { this.matches = matches; }
        
        public int getTotalMatches() { return totalMatches; }
        public void setTotalMatches(int totalMatches) { this.totalMatches = totalMatches; }
    }
    
    /**
     * Upload CV to ML service for parsing and analysis
     */
    public CVParseResponse uploadCVToMLService(MultipartFile file, String sessionId) throws Exception {
        try {
            // Prepare multipart request
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            
            // Add file
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });
            
            // Add session ID
            body.add("session_id", sessionId);
            
            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            
            // Create request
            HttpEntity<MultiValueMap<String, Object>> requestEntity = 
                new HttpEntity<>(body, headers);
            
            // Send request
            ResponseEntity<Map> response = restTemplate.exchange(
                mlServiceUrl + "/upload_cv",
                HttpMethod.POST,
                requestEntity,
                Map.class
            );
            
            // Parse response
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                CVParseResponse cvResponse = new CVParseResponse();
                Map<String, Object> responseBody = response.getBody();
                
                cvResponse.setMemberId((String) responseBody.get("member_id"));
                cvResponse.setExtractedText((String) responseBody.get("extracted_text"));
                cvResponse.setSkills((List<String>) responseBody.get("skills"));
                cvResponse.setExperienceYears((Integer) responseBody.get("experience_years"));
                cvResponse.setSeniorityLevel((String) responseBody.get("seniority_level"));
                cvResponse.setTitle((String) responseBody.get("title"));
                cvResponse.setLocation((String) responseBody.get("location"));
                cvResponse.setEducation((String) responseBody.get("education"));
                
                return cvResponse;
            } else {
                throw new Exception("Failed to upload CV to ML service");
            }
            
        } catch (Exception e) {
            System.err.println("Error uploading CV to ML service: " + e.getMessage());
            e.printStackTrace();
            throw new Exception("Failed to upload CV: " + e.getMessage());
        }
    }
    
    /**
     * Get job matches from ML service
     */
    public JobMatchResponse getJobMatches(String sessionId, String memberId, String mode, int limit) throws Exception {
        try {
            // Prepare request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("session_id", sessionId);
            requestBody.put("member_id", memberId);
            requestBody.put("mode", mode);
            requestBody.put("top_k", limit);
            
            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Create request
            HttpEntity<Map<String, Object>> requestEntity = 
                new HttpEntity<>(requestBody, headers);
            
            // Send request
            ResponseEntity<Map> response = restTemplate.exchange(
                mlServiceUrl + "/match_jobs",
                HttpMethod.POST,
                requestEntity,
                Map.class
            );
            
            // Parse response
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JobMatchResponse matchResponse = new JobMatchResponse();
                Map<String, Object> responseBody = response.getBody();
                
                matchResponse.setMatches((List<Map<String, Object>>) responseBody.get("matches"));
                matchResponse.setTotalMatches((Integer) responseBody.get("total_matches"));
                
                return matchResponse;
            } else {
                throw new Exception("Failed to get job matches from ML service");
            }
            
        } catch (Exception e) {
            System.err.println("Error getting job matches from ML service: " + e.getMessage());
            e.printStackTrace();
            throw new Exception("Failed to get job matches: " + e.getMessage());
        }
    }
    
    /**
     * Clear session CV data (note: actual cleanup happens on ML service side)
     */
    public void clearSessionCVData(String sessionId) {
        // The ML service handles cleanup based on Redis TTL
        // This is just a placeholder for any additional cleanup if needed
        System.out.println("CV data cleared for session: " + sessionId);
    }
    
    /**
     * Check ML service health
     */
    public boolean checkMLServiceHealth() {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                mlServiceUrl + "/health",
                Map.class
            );
            
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            System.err.println("ML service health check failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Sync jobs to ML service
     */
    public void syncJobToMLService(Job job) {
       try {
           HttpHeaders headers = new HttpHeaders();
           headers.setContentType(MediaType.APPLICATION_JSON);

           HttpEntity<Job> requestEntity = new HttpEntity<>(job, headers);

           restTemplate.exchange(
               mlServiceUrl + "/sync_jobs",
               HttpMethod.POST,
               requestEntity,
               Void.class
           );
       } catch (RestClientException e) {
           e.printStackTrace();
       }
    }
}