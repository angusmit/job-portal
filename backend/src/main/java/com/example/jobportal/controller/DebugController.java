package com.example.jobportal.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.jobportal.model.ApprovalStatus;
import com.example.jobportal.model.Job;
import com.example.jobportal.repository.JobRepository;

@RestController
@RequestMapping("/api/debug")
public class DebugController {
    
    @Autowired
    private JobRepository jobRepository;
    
    @GetMapping("/test-jobs")
    public Map<String, Object> testJobs() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            List<Job> allJobs = jobRepository.findAll();
            List<Job> approvedJobs = jobRepository.findByApprovalStatus(ApprovalStatus.APPROVED);
            
            result.put("totalJobs", allJobs.size());
            result.put("approvedJobs", approvedJobs.size());
            result.put("status", "success");
            
            // Create simple job info without circular references
            List<Map<String, Object>> simpleJobs = new ArrayList<>();
            for (Job job : approvedJobs) {
                Map<String, Object> simpleJob = new HashMap<>();
                simpleJob.put("id", job.getId());
                simpleJob.put("title", job.getTitle());
                simpleJob.put("company", job.getCompany());
                simpleJob.put("location", job.getLocation());
                simpleJob.put("approvalStatus", job.getApprovalStatus());
                simpleJobs.add(simpleJob);
            }
            result.put("jobs", simpleJobs);
            
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
            result.put("type", e.getClass().getSimpleName());
        }
        
        return result;
    }
}
