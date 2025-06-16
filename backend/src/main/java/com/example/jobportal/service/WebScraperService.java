package com.example.jobportal.service;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.jobportal.model.ApprovalStatus;
import com.example.jobportal.model.CompanySource;
import com.example.jobportal.model.Job;
import com.example.jobportal.model.ScrapedJob;
import com.example.jobportal.model.User;
import com.example.jobportal.repository.CompanySourceRepository;
import com.example.jobportal.repository.JobRepository;
import com.example.jobportal.repository.ScrapedJobRepository;
import com.example.jobportal.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class WebScraperService {
    
    @Autowired
    private CompanySourceRepository companySourceRepository;
    
    @Autowired
    private ScrapedJobRepository scrapedJobRepository;
    
    @Autowired
    private JobRepository jobRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    private static final int TIMEOUT = 30000; // 30 seconds
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    
    /**
     * Scrape jobs from a specific company source
     */
    public Map<String, Object> scrapeCompanyJobs(CompanySource source) {
        Map<String, Object> result = new HashMap<>();
        List<ScrapedJob> scrapedJobs = new ArrayList<>();
        
        try {
            log.info("Starting scrape for company: {}", source.getCompanyName());
            
            // Connect to the career page
            Document doc = Jsoup.connect(source.getCareerPageUrl())
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT)
                    .get();
            
            // Find job listings
            Elements jobElements = doc.select(source.getJobListSelector());
            log.info("Found {} job elements", jobElements.size());
            
            for (Element jobElement : jobElements) {
                try {
                    ScrapedJob scrapedJob = extractJobData(jobElement, source);
                    if (scrapedJob != null) {
                        // Check for duplicates
                        String contentHash = generateContentHash(scrapedJob);
                        scrapedJob.setContentHash(contentHash);
                        
                        // Check if job already exists
                        Optional<ScrapedJob> existingJob = scrapedJobRepository
                                .findBySourceAndContentHash(source, contentHash);
                        
                        if (existingJob.isPresent()) {
                            // Update last seen time
                            ScrapedJob existing = existingJob.get();
                            existing.setLastSeenAt(LocalDateTime.now());
                            scrapedJobRepository.save(existing);
                        } else {
                            // New job found
                            scrapedJob.setSource(source);
                            scrapedJob = scrapedJobRepository.save(scrapedJob);
                            scrapedJobs.add(scrapedJob);
                        }
                    }
                } catch (Exception e) {
                    log.error("Error extracting job data: ", e);
                }
            }
            
            // Update source statistics
            source.setLastScrapedAt(LocalDateTime.now());
            source.setLastScrapeJobCount(scrapedJobs.size());
            source.setTotalJobsScraped(source.getTotalJobsScraped() + scrapedJobs.size());
            source.updateNextScheduledScrape();
            source.setLastError(null);
            source.setLastErrorAt(null);
            companySourceRepository.save(source);
            
            result.put("success", true);
            result.put("jobsScraped", scrapedJobs.size());
            result.put("newJobs", scrapedJobs);
            
            log.info("Scraping completed. Found {} new jobs", scrapedJobs.size());
            
        } catch (IOException e) {
            log.error("Error scraping {}: {}", source.getCompanyName(), e.getMessage());
            
            // Update error information
            source.setLastError(e.getMessage());
            source.setLastErrorAt(LocalDateTime.now());
            companySourceRepository.save(source);
            
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Extract job data from a job element
     */
    private ScrapedJob extractJobData(Element jobElement, CompanySource source) {
        ScrapedJob job = new ScrapedJob();
        
        // Extract title
        if (source.getJobTitleSelector() != null) {
            Element titleElement = jobElement.selectFirst(source.getJobTitleSelector());
            if (titleElement != null) {
                job.setTitle(titleElement.text().trim());
            }
        }
        
        // Extract location
        if (source.getJobLocationSelector() != null) {
            Element locationElement = jobElement.selectFirst(source.getJobLocationSelector());
            if (locationElement != null) {
                job.setLocation(locationElement.text().trim());
            }
        }
        
        // Extract job type
        if (source.getJobTypeSelector() != null) {
            Element typeElement = jobElement.selectFirst(source.getJobTypeSelector());
            if (typeElement != null) {
                job.setJobType(typeElement.text().trim());
            }
        }
        
        // Extract salary
        if (source.getJobSalarySelector() != null) {
            Element salaryElement = jobElement.selectFirst(source.getJobSalarySelector());
            if (salaryElement != null) {
                job.setSalary(salaryElement.text().trim());
            }
        }
        
        // Extract URL
        if (source.getJobUrlSelector() != null) {
            Element urlElement = jobElement.selectFirst(source.getJobUrlSelector());
            if (urlElement != null) {
                String url = urlElement.attr("href");
                if (!url.startsWith("http")) {
                    // Relative URL - make it absolute
                    try {
                        java.net.URL baseUrl = new java.net.URL(source.getCareerPageUrl());
                        java.net.URL absoluteUrl = new java.net.URL(baseUrl, url);
                        url = absoluteUrl.toString();
                    } catch (Exception e) {
                        log.error("Error creating absolute URL: ", e);
                    }
                }
                job.setExternalUrl(url);
                
                // Try to extract external ID from URL
                job.setExternalId(extractIdFromUrl(url));
            }
        }
        
        // Extract description (might need to visit detail page)
        if (source.getJobDescriptionSelector() != null) {
            Element descElement = jobElement.selectFirst(source.getJobDescriptionSelector());
            if (descElement != null) {
                job.setDescription(descElement.text().trim());
            } else if (job.getExternalUrl() != null) {
                // Try to get description from detail page
                try {
                    Document detailDoc = Jsoup.connect(job.getExternalUrl())
                            .userAgent(USER_AGENT)
                            .timeout(TIMEOUT)
                            .get();
                    Element detailDesc = detailDoc.selectFirst(source.getJobDescriptionSelector());
                    if (detailDesc != null) {
                        job.setDescription(detailDesc.text().trim());
                    }
                } catch (IOException e) {
                    log.warn("Could not fetch job details from: {}", job.getExternalUrl());
                }
            }
        }
        
        // Validate minimum required fields
        if (job.getTitle() == null || job.getTitle().isEmpty()) {
            return null;
        }
        
        return job;
    }
    
    /**
     * Generate content hash for duplicate detection
     */
    private String generateContentHash(ScrapedJob job) {
        try {
            String content = String.join("|",
                    job.getTitle() != null ? job.getTitle() : "",
                    job.getLocation() != null ? job.getLocation() : "",
                    job.getJobType() != null ? job.getJobType() : "",
                    job.getDescription() != null ? job.getDescription().substring(0, Math.min(500, job.getDescription().length())) : ""
            );
            
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(content.getBytes());
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            return UUID.randomUUID().toString();
        }
    }
    
    /**
     * Extract ID from URL
     */
    private String extractIdFromUrl(String url) {
        // Common patterns for job IDs in URLs
        List<Pattern> patterns = Arrays.asList(
                Pattern.compile("job[/-]?id[=/-]([a-zA-Z0-9]+)"),
                Pattern.compile("job[s]?/([a-zA-Z0-9]+)"),
                Pattern.compile("posting[s]?/([a-zA-Z0-9]+)"),
                Pattern.compile("id=([a-zA-Z0-9]+)")
        );
        
        for (Pattern pattern : patterns) {
            java.util.regex.Matcher matcher = pattern.matcher(url);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        
        // Use last part of URL as ID
        String[] parts = url.split("/");
        if (parts.length > 0) {
            return parts[parts.length - 1].replaceAll("[^a-zA-Z0-9]", "");
        }
        
        return UUID.randomUUID().toString();
    }
    
    /**
     * Import scraped jobs to main job board
     */
    public Map<String, Object> importScrapedJobs(List<Long> scrapedJobIds, Long adminUserId) {
        Map<String, Object> result = new HashMap<>();
        List<Job> importedJobs = new ArrayList<>();
        int skipped = 0;
        
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new RuntimeException("Admin user not found"));
        
        for (Long scrapedJobId : scrapedJobIds) {
            try {
                ScrapedJob scrapedJob = scrapedJobRepository.findById(scrapedJobId)
                        .orElseThrow(() -> new RuntimeException("Scraped job not found"));
                
                if (scrapedJob.isImported()) {
                    skipped++;
                    continue;
                }
                
                // Create new job
                Job job = new Job();
                job.setTitle(scrapedJob.getTitle());
                job.setCompany(scrapedJob.getSource().getCompanyName());
                job.setLocation(scrapedJob.getLocation() != null ? scrapedJob.getLocation() : "Not specified");
                job.setDescription(scrapedJob.getDescription() != null ? scrapedJob.getDescription() : "");
                job.setJobType(scrapedJob.getJobType() != null ? scrapedJob.getJobType() : "Full-time");
                job.setSalary(scrapedJob.getSalary());
                job.setRequirements(scrapedJob.getRequirements() != null ? scrapedJob.getRequirements() : "");
                job.setPostedBy(admin);
                job.setActive(true);
                job.setApprovalStatus(ApprovalStatus.APPROVED);
                job.setApprovedBy(admin.getUsername());
                job.setApprovedDate(LocalDateTime.now());
                
                job = jobRepository.save(job);
                
                // Mark scraped job as imported
                scrapedJob.setImported(true);
                scrapedJob.setImportedAt(LocalDateTime.now());
                scrapedJob.setImportedJob(job);
                scrapedJobRepository.save(scrapedJob);
                
                importedJobs.add(job);
                
            } catch (Exception e) {
                log.error("Error importing scraped job {}: {}", scrapedJobId, e.getMessage());
            }
        }
        
        result.put("imported", importedJobs.size());
        result.put("skipped", skipped);
        result.put("jobs", importedJobs);
        
        return result;
    }
    
    /**
     * Detect duplicate scraped jobs
     */
    public void detectDuplicates(CompanySource source) {
        List<ScrapedJob> jobs = scrapedJobRepository.findBySourceAndActiveTrue(source);
        
        for (int i = 0; i < jobs.size(); i++) {
            ScrapedJob job1 = jobs.get(i);
            
            for (int j = i + 1; j < jobs.size(); j++) {
                ScrapedJob job2 = jobs.get(j);
                
                if (areSimilar(job1, job2)) {
                    // Mark job2 as duplicate of job1
                    job2.setDuplicate(true);
                    job2.setDuplicateOf(job1);
                    scrapedJobRepository.save(job2);
                }
            }
        }
    }
    
    /**
     * Check if two jobs are similar (potential duplicates)
     */
    private boolean areSimilar(ScrapedJob job1, ScrapedJob job2) {
        // Simple similarity check - can be enhanced with fuzzy matching
        if (job1.getTitle().equalsIgnoreCase(job2.getTitle()) &&
            job1.getLocation() != null && job2.getLocation() != null &&
            job1.getLocation().equalsIgnoreCase(job2.getLocation())) {
            return true;
        }
        
        // Check content hash
        return job1.getContentHash() != null && 
               job1.getContentHash().equals(job2.getContentHash());
    }
    
    /**
     * Mark jobs not seen in recent scrapes as inactive
     */
    public void markInactiveJobs(CompanySource source, int daysThreshold) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(daysThreshold);
        
        List<ScrapedJob> oldJobs = scrapedJobRepository
                .findBySourceAndLastSeenAtBeforeAndActiveTrue(source, threshold);
        
        for (ScrapedJob job : oldJobs) {
            job.setActive(false);
            scrapedJobRepository.save(job);
        }
        
        log.info("Marked {} jobs as inactive for {}", oldJobs.size(), source.getCompanyName());
    }
}