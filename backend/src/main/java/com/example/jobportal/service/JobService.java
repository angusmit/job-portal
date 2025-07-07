package com.example.jobportal.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.jobportal.model.Job;
import com.example.jobportal.repository.JobRepository;

import jakarta.transaction.Transactional;

@Service
public class JobService {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private MLIntegrationService mlIntegrationService;
    
    @Transactional
    public Job createJob(Job job) {
        Job savedJob = jobRepository.save(job);
        
        // Sync to ML service
        mlIntegrationService.syncJobToMLService(savedJob);
        
        return savedJob;
    }
}