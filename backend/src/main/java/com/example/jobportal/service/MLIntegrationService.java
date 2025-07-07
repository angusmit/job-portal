package com.example.jobportal.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.example.jobportal.model.Job;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class MLIntegrationService {

    private final RestTemplate restTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${ml.service.url:http://localhost:8000}")
    private String mlServiceUrl;

    // DTOs for ML Service Communication
    public static class CVParseResponse {
        public String memberId;
        public String extractedText;
        public List<String> skills;
        public int experienceYears;
        public String seniorityLevel;
        public String title;
        public String location;
    }

    public static class JobMatchRequest {
        public String sessionId;
        public String memberId;
        public String mode = "graduate_friendly";
        public int topK = 10;
    }

    public static class JobMatch {
        public String jobId;
        public String title;
        public String company;
        public String description;
        public List<String> requiredSkills;
        public int experienceRequired;
        public String location;
        public String seniorityLevel;
        public double matchScore;
        public String matchQuality;
    }

    public static class JobMatchResponse {
        public List<JobMatch> matches;
        public int totalMatches;
    }

    /**
     * Parse uploaded CV using ML service
     */
    public CVParseResponse parseCv(MultipartFile file, String sessionId) throws IOException {
        // Create multipart request
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        
        // Build form data
        LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new MultipartInputStreamFileResource(
            file.getInputStream(), file.getOriginalFilename()
        ));
        body.add("session_id", sessionId);
        
        HttpEntity<LinkedMultiValueMap<String, Object>> requestEntity = 
            new HttpEntity<>(body, headers);
        
        try {
            ResponseEntity<CVParseResponse> response = restTemplate.exchange(
                mlServiceUrl + "/parse_cv",
                HttpMethod.POST,
                requestEntity,
                CVParseResponse.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK) {
                CVParseResponse cvData = response.getBody();
                
                // Store parsed CV data in Redis for session
                String redisKey = "cv:session:" + sessionId;
                redisTemplate.opsForValue().set(
                    redisKey, 
                    cvData, 
                    30, 
                    TimeUnit.MINUTES
                );
                
                return cvData;
            }
        } catch (Exception e) {
            log.error("Error parsing CV: ", e);
            throw new RuntimeException("Failed to parse CV", e);
        }
        
        return null;
    }

    /**
     * Get job matches for parsed CV
     */
    public JobMatchResponse getJobMatches(String sessionId, String memberId, 
                                         String mode, int topK) {
        JobMatchRequest request = new JobMatchRequest();
        request.sessionId = sessionId;
        request.memberId = memberId;
        request.mode = mode != null ? mode : "graduate_friendly";
        request.topK = topK > 0 ? topK : 10;
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<JobMatchRequest> requestEntity = new HttpEntity<>(request, headers);
        
        try {
            ResponseEntity<JobMatchResponse> response = restTemplate.exchange(
                mlServiceUrl + "/match_jobs",
                HttpMethod.POST,
                requestEntity,
                JobMatchResponse.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK) {
                JobMatchResponse matches = response.getBody();
                
                // Cache results in Redis
                String cacheKey = "matches:" + sessionId + ":" + memberId;
                redisTemplate.opsForValue().set(
                    cacheKey,
                    matches,
                    30,
                    TimeUnit.MINUTES
                );
                
                return matches;
            }
        } catch (Exception e) {
            log.error("Error getting job matches: ", e);
            throw new RuntimeException("Failed to get job matches", e);
        }
        
        return new JobMatchResponse();
    }

    /**
     * Sync job to ML service when new job is posted
     */
    public void syncJobToMLService(Job job) {
        Map<String, Object> jobData = new HashMap<>();
        jobData.put("job_id", job.getId().toString());
        jobData.put("title", job.getTitle());
        jobData.put("description", job.getDescription());
        jobData.put("company", jobData.put("company", job.getPostedByCompany()));
        jobData.put("required_skills", parseSkills(job.getRequiredSkills()));
        jobData.put("preferred_skills", parseSkills(job.getPreferredSkills()));
        jobData.put("experience_required", job.getExperienceRequired());
        jobData.put("location", job.getLocation());
        jobData.put("seniority_level", job.getSeniorityLevel());
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(jobData, headers);
        
        try {
            restTemplate.exchange(
                mlServiceUrl + "/add_job",
                HttpMethod.POST,
                requestEntity,
                Map.class
            );
            log.info("Synced job {} to ML service", job.getId());
        } catch (Exception e) {
            log.error("Error syncing job to ML service: ", e);
        }
    }
    
    /**
     * Clear CV data on session end
     */
    public void clearSessionCVData(String sessionId) {
        String pattern = "cv:session:" + sessionId + "*";
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("Cleared CV data for session: {}", sessionId);
        }
    }
    
    private List<String> parseSkills(String skills) {
        if (skills == null || skills.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.asList(skills.split(",\\s*"));
    }
    
    // Helper class for multipart file upload
    private static class MultipartInputStreamFileResource 
            extends org.springframework.core.io.InputStreamResource {
        private final String filename;

        public MultipartInputStreamFileResource(InputStream inputStream, String filename) {
            super(inputStream);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return this.filename;
        }

        @Override
        public long contentLength() throws IOException {
            return -1; // Unknown length
        }
    }
}
