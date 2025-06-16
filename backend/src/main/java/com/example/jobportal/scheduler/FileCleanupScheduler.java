package com.example.jobportal.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.jobportal.model.ProcessingStatus;
import com.example.jobportal.model.Resume;
import com.example.jobportal.repository.ResumeRepository;
import com.example.jobportal.service.FileStorageService;

import lombok.extern.slf4j.Slf4j;

@Component
@EnableScheduling
@Slf4j
public class FileCleanupScheduler {
    
    @Autowired
    private ResumeRepository resumeRepository;
    
    @Autowired
    private FileStorageService fileStorageService;
    
    /**
     * Run every hour to clean up expired temporary files
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour
    public void cleanupExpiredFiles() {
        log.info("Starting cleanup of expired temporary files");
        
        try {
            // Find expired resumes
            List<Resume> expiredResumes = resumeRepository.findExpiredTemporaryResumes(LocalDateTime.now());
            
            for (Resume resume : expiredResumes) {
                // Delete associated files
                if (resume.getSessionId() != null) {
                    fileStorageService.deleteSessionFiles(resume.getSessionId());
                }
                
                // Update resume status
                resume.setStatus(ProcessingStatus.EXPIRED);
                resumeRepository.save(resume);
            }
            
            // Clean up orphaned session files (older than 2 hours)
            fileStorageService.cleanupExpiredFiles(2);
            
            log.info("Cleaned up {} expired resumes", expiredResumes.size());
            
        } catch (Exception e) {
            log.error("Error during file cleanup", e);
        }
    }
    
    /**
     * Run daily to clean up old expired resume records
     */
    @Scheduled(cron = "0 0 2 * * *") // Every day at 2 AM
    public void cleanupOldRecords() {
        log.info("Starting cleanup of old expired records");
        
        try {
            // Delete resume records that expired more than 7 days ago
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(7);
            resumeRepository.deleteByExpiresAtBefore(cutoffDate);
            
            log.info("Cleaned up old expired resume records");
            
        } catch (Exception e) {
            log.error("Error during record cleanup", e);
        }
    }
}