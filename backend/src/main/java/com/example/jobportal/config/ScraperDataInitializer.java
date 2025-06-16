package com.example.jobportal.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.example.jobportal.model.CompanySource;
import com.example.jobportal.model.ScrapingFrequency;
import com.example.jobportal.model.User;
import com.example.jobportal.model.UserRole;
import com.example.jobportal.repository.CompanySourceRepository;
import com.example.jobportal.repository.UserRepository;


@Component
@Order(2) // Run after DataInitializer
public class ScraperDataInitializer implements CommandLineRunner {
    
    @Autowired
    private CompanySourceRepository companySourceRepository;
    
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;
    
    
    @Override
    public void run(String... args) throws Exception {
        // Only initialize if no sources exist
        if (companySourceRepository.count() > 0) {
            return;
        }

        User admin = userRepository.findByUsername("admin").orElseGet(() -> {
            User newAdmin = new User();
            newAdmin.setUsername("admin");
            newAdmin.setPassword(passwordEncoder.encode("admin123"));
            newAdmin.setEmail("admin@example.com");
            newAdmin.setRole(UserRole.ADMIN);
            return userRepository.save(newAdmin);
        });        
        // Create sample company sources with common career page patterns
        
        // Example 1: Generic career page pattern
        CompanySource source1 = new CompanySource();
        source1.setCompanyName("Example Tech Co");
        source1.setCareerPageUrl("https://example-careers.com/jobs");
        source1.setJobListSelector(".job-listing-item, .career-opportunity");
        source1.setJobTitleSelector(".job-title, h3.title");
        source1.setJobLocationSelector(".job-location, .location-tag");
        source1.setJobDescriptionSelector(".job-description, .job-summary");
        source1.setJobTypeSelector(".job-type, .employment-type");
        source1.setJobSalarySelector(".salary-range, .compensation");
        source1.setJobUrlSelector("a.job-link, .title a");
        source1.setFrequency(ScrapingFrequency.DAILY);
        source1.setActive(false); // Disabled by default
        source1.setCreatedBy(admin);
        companySourceRepository.save(source1);
        
        // Example 2: Greenhouse.io pattern
        CompanySource source2 = new CompanySource();
        source2.setCompanyName("Startup Inc (Greenhouse)");
        source2.setCareerPageUrl("https://boards.greenhouse.io/examplestartup");
        source2.setJobListSelector(".opening");
        source2.setJobTitleSelector(".opening a");
        source2.setJobLocationSelector(".location");
        source2.setJobDescriptionSelector(".content");
        source2.setJobTypeSelector("");
        source2.setJobSalarySelector("");
        source2.setJobUrlSelector("a");
        source2.setFrequency(ScrapingFrequency.WEEKLY);
        source2.setActive(false);
        source2.setCreatedBy(admin);
        companySourceRepository.save(source2);
        
        // Example 3: Lever.co pattern
        CompanySource source3 = new CompanySource();
        source3.setCompanyName("Growth Company (Lever)");
        source3.setCareerPageUrl("https://jobs.lever.co/examplecompany");
        source3.setJobListSelector(".posting");
        source3.setJobTitleSelector(".posting-title h5");
        source3.setJobLocationSelector(".posting-categories .location");
        source3.setJobDescriptionSelector(".posting-description");
        source3.setJobTypeSelector(".commitment");
        source3.setJobSalarySelector("");
        source3.setJobUrlSelector(".posting-title");
        source3.setFrequency(ScrapingFrequency.DAILY);
        source3.setActive(false);
        source3.setCreatedBy(admin);
        companySourceRepository.save(source3);
        
        System.out.println("Sample scraper sources initialized (disabled by default)");
        System.out.println("To enable scraping, activate sources in Admin Dashboard > Job Scraper");
    }
}