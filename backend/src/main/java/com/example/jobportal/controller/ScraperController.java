package com.example.jobportal.controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.jobportal.dto.MessageResponse;
import com.example.jobportal.model.CompanySource;
import com.example.jobportal.model.ScrapedJob;
import com.example.jobportal.model.User;
import com.example.jobportal.repository.CompanySourceRepository;
import com.example.jobportal.repository.ScrapedJobRepository;
import com.example.jobportal.repository.UserRepository;
import com.example.jobportal.security.UserPrincipal;
import com.example.jobportal.service.WebScraperService;

@RestController
@RequestMapping("/api/scraper")
@CrossOrigin(origins = "http://localhost:3000")
@PreAuthorize("hasAuthority('ADMIN')")
public class ScraperController {
    
    @Autowired
    private CompanySourceRepository companySourceRepository;
    
    @Autowired
    private ScrapedJobRepository scrapedJobRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private WebScraperService webScraperService;
    
    // ========== Company Source Management ==========
    
    /**
     * Get all company sources
     */
    @GetMapping("/sources")
    public List<CompanySource> getAllSources() {
        return companySourceRepository.findAll();
    }
    
    /**
     * Get active company sources
     */
    @GetMapping("/sources/active")
    public List<CompanySource> getActiveSources() {
        return companySourceRepository.findByActiveTrue();
    }
    
    /**
     * Create a new company source
     */
    @PostMapping("/sources")
    public ResponseEntity<?> createSource(@RequestBody CompanySource source, Authentication authentication) {
        try {
            // Check if source already exists
            if (companySourceRepository.findByCareerPageUrl(source.getCareerPageUrl()).isPresent()) {
                return ResponseEntity.badRequest()
                    .body(new MessageResponse("A source with this URL already exists"));
            }
            
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            User admin = userRepository.findById(userPrincipal.getId()).orElseThrow();
            
            source.setCreatedBy(admin);
            source.setActive(true);
            CompanySource savedSource = companySourceRepository.save(source);
            
            return ResponseEntity.ok(savedSource);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(new MessageResponse("Failed to create source: " + e.getMessage()));
        }
    }
    
    /**
     * Update a company source
     */
    @PutMapping("/sources/{id}")
    public ResponseEntity<?> updateSource(@PathVariable Long id, @RequestBody CompanySource sourceDetails) {
        try {
            CompanySource source = companySourceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Source not found"));
            
            source.setCompanyName(sourceDetails.getCompanyName());
            source.setCareerPageUrl(sourceDetails.getCareerPageUrl());
            source.setJobListSelector(sourceDetails.getJobListSelector());
            source.setJobTitleSelector(sourceDetails.getJobTitleSelector());
            source.setJobLocationSelector(sourceDetails.getJobLocationSelector());
            source.setJobDescriptionSelector(sourceDetails.getJobDescriptionSelector());
            source.setJobTypeSelector(sourceDetails.getJobTypeSelector());
            source.setJobSalarySelector(sourceDetails.getJobSalarySelector());
            source.setJobRequirementsSelector(sourceDetails.getJobRequirementsSelector());
            source.setJobUrlSelector(sourceDetails.getJobUrlSelector());
            source.setFrequency(sourceDetails.getFrequency());
            source.setActive(sourceDetails.isActive());
            
            CompanySource updatedSource = companySourceRepository.save(source);
            
            return ResponseEntity.ok(updatedSource);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(new MessageResponse("Failed to update source: " + e.getMessage()));
        }
    }
    
    /**
     * Delete a company source
     */
    @DeleteMapping("/sources/{id}")
    public ResponseEntity<?> deleteSource(@PathVariable Long id) {
        try {
            companySourceRepository.deleteById(id);
            return ResponseEntity.ok(new MessageResponse("Source deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(new MessageResponse("Failed to delete source: " + e.getMessage()));
        }
    }
    
    /**
     * Manually trigger scraping for a source
     */
    @PostMapping("/sources/{id}/scrape")
    public ResponseEntity<?> scrapeSource(@PathVariable Long id) {
        try {
            CompanySource source = companySourceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Source not found"));
            
            Map<String, Object> result = webScraperService.scrapeCompanyJobs(source);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(new MessageResponse("Failed to scrape source: " + e.getMessage()));
        }
    }
    
    // ========== Scraped Job Management ==========
    
    /**
     * Get scraped jobs for a source
     */
    @GetMapping("/sources/{sourceId}/jobs")
    public ResponseEntity<?> getScrapedJobs(@PathVariable Long sourceId,
                                          @RequestParam(defaultValue = "false") boolean activeOnly) {
        try {
            CompanySource source = companySourceRepository.findById(sourceId)
                .orElseThrow(() -> new RuntimeException("Source not found"));
            
            List<ScrapedJob> jobs;
            if (activeOnly) {
                jobs = scrapedJobRepository.findBySourceAndActiveTrue(source);
            } else {
                jobs = scrapedJobRepository.findBySource(source);
            }
            
            return ResponseEntity.ok(jobs);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(new MessageResponse("Failed to fetch scraped jobs: " + e.getMessage()));
        }
    }
    
    /**
     * Get all unimported scraped jobs
     */
    @GetMapping("/jobs/unimported")
    public List<ScrapedJob> getUnimportedJobs() {
        return scrapedJobRepository.findByImportedFalseAndActiveTrueAndDuplicateFalse();
    }
    
    /**
     * Import selected scraped jobs to main job board
     */
    @PostMapping("/jobs/import")
    public ResponseEntity<?> importJobs(@RequestBody List<Long> jobIds, Authentication authentication) {
        try {
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            
            Map<String, Object> result = webScraperService.importScrapedJobs(jobIds, userPrincipal.getId());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(new MessageResponse("Failed to import jobs: " + e.getMessage()));
        }
    }
    
    /**
     * Get scraper statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getScraperStats() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalSources", companySourceRepository.count());
        stats.put("activeSources", companySourceRepository.countByActiveTrue());
        stats.put("totalScrapedJobs", scrapedJobRepository.count());
        stats.put("activeScrapedJobs", scrapedJobRepository.findByImportedFalseAndActiveTrueAndDuplicateFalse().size());
        stats.put("importedJobs", scrapedJobRepository.countByImportedTrue());
        
        // Recent activity
        LocalDateTime lastWeek = LocalDateTime.now().minusDays(7);
        stats.put("jobsScrapedLastWeek", scrapedJobRepository.findRecentJobs(lastWeek).size());
        
        // Sources with errors
        stats.put("sourcesWithErrors", companySourceRepository.findByLastErrorNotNull().size());
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Get sources due for scraping
     */
    @GetMapping("/sources/due")
    public List<CompanySource> getSourcesDueForScraping() {
        return companySourceRepository.findSourcesDueForScraping(LocalDateTime.now());
    }
}