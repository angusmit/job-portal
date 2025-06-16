package com.example.jobportal.scheduler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.jobportal.model.CompanySource;
import com.example.jobportal.repository.CompanySourceRepository;
import com.example.jobportal.service.WebScraperService;

import lombok.extern.slf4j.Slf4j;

@Component
@EnableScheduling
@Slf4j
public class JobScraperScheduler {
    
    @Autowired
    private CompanySourceRepository companySourceRepository;
    
    @Autowired
    private WebScraperService webScraperService;
    
    /**
     * Run every 30 minutes to check for sources that need scraping
     */
    @Scheduled(fixedDelay = 1800000) // 30 minutes
    public void scrapeJobs() {
        log.info("Starting scheduled job scraping");
        
        try {
            // Find sources due for scraping
            List<CompanySource> sourcesToScrape = companySourceRepository
                    .findSourcesDueForScraping(LocalDateTime.now());
            
            log.info("Found {} sources to scrape", sourcesToScrape.size());
            
            for (CompanySource source : sourcesToScrape) {
                try {
                    log.info("Scraping jobs from: {}", source.getCompanyName());
                    
                    Map<String, Object> result = webScraperService.scrapeCompanyJobs(source);
                    
                    if ((boolean) result.getOrDefault("success", false)) {
                        int jobsScraped = (int) result.getOrDefault("jobsScraped", 0);
                        log.info("Successfully scraped {} new jobs from {}", 
                                jobsScraped, source.getCompanyName());
                    } else {
                        log.error("Failed to scrape {}: {}", 
                                source.getCompanyName(), result.get("error"));
                    }
                    
                    // Add delay between sources to be polite
                    Thread.sleep(5000); // 5 seconds
                    
                } catch (Exception e) {
                    log.error("Error scraping source {}: ", source.getCompanyName(), e);
                }
            }
            
        } catch (Exception e) {
            log.error("Error in job scraping scheduler: ", e);
        }
    }
    
    /**
     * Run daily to clean up old inactive jobs
     */
    @Scheduled(cron = "0 0 3 * * *") // Every day at 3 AM
    public void cleanupInactiveJobs() {
        log.info("Starting cleanup of inactive scraped jobs");
        
        try {
            List<CompanySource> activeSources = companySourceRepository.findByActiveTrue();
            
            for (CompanySource source : activeSources) {
                // Mark jobs not seen in 30 days as inactive
                webScraperService.markInactiveJobs(source, 30);
            }
            
            log.info("Completed cleanup of inactive jobs");
            
        } catch (Exception e) {
            log.error("Error in cleanup scheduler: ", e);
        }
    }
    
    /**
     * Run weekly to detect duplicate jobs
     */
    @Scheduled(cron = "0 0 4 * * SUN") // Every Sunday at 4 AM
    public void detectDuplicates() {
        log.info("Starting duplicate detection");
        
        try {
            List<CompanySource> activeSources = companySourceRepository.findByActiveTrue();
            
            for (CompanySource source : activeSources) {
                webScraperService.detectDuplicates(source);
            }
            
            log.info("Completed duplicate detection");
            
        } catch (Exception e) {
            log.error("Error in duplicate detection scheduler: ", e);
        }
    }
}