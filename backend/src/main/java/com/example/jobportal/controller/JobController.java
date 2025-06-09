package com.example.jobportal.controller;

import com.example.jobportal.model.Job;
import com.example.jobportal.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/jobs")
@CrossOrigin(origins = "http://localhost:3000")
public class JobController {
    
    @Autowired
    private JobRepository jobRepository;
    
    // Get all jobs
    @GetMapping
    public List<Job> getAllJobs() {
        return jobRepository.findAll();
    }
    
    // Get job by ID
    @GetMapping("/{id}")
    public ResponseEntity<Job> getJobById(@PathVariable Long id) {
        Optional<Job> job = jobRepository.findById(id);
        return job.map(ResponseEntity::ok)
                  .orElse(ResponseEntity.notFound().build());
    }
    
    // Create new job
    @PostMapping
    public Job createJob(@RequestBody Job job) {
        return jobRepository.save(job);
    }
    
    // Search jobs
    @GetMapping("/search")
    public List<Job> searchJobs(@RequestParam String query) {
        return jobRepository.searchJobs(query);
    }
    
    // Filter jobs by location
    @GetMapping("/filter")
    public List<Job> filterJobs(
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String jobType) {
        
        if (location != null && jobType != null) {
            return jobRepository.findByLocationContainingAndJobTypeContaining(location, jobType);
        } else if (location != null) {
            return jobRepository.findByLocationContaining(location);
        } else if (jobType != null) {
            return jobRepository.findByJobTypeContaining(jobType);
        }
        return jobRepository.findAll();
    }
}