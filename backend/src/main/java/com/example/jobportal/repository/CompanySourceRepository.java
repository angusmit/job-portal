package com.example.jobportal.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.jobportal.model.CompanySource;
import com.example.jobportal.model.ScrapingFrequency;

@Repository
public interface CompanySourceRepository extends JpaRepository<CompanySource, Long> {
    
    // Find by company name
    Optional<CompanySource> findByCompanyName(String companyName);
    
    // Find by career page URL
    Optional<CompanySource> findByCareerPageUrl(String url);
    
    // Find active sources
    List<CompanySource> findByActiveTrue();
    
    // Find sources by frequency
    List<CompanySource> findByFrequency(ScrapingFrequency frequency);
    
    // Find sources due for scraping
    @Query("SELECT cs FROM CompanySource cs WHERE cs.active = true AND cs.nextScheduledScrape <= :now")
    List<CompanySource> findSourcesDueForScraping(@Param("now") LocalDateTime now);
    
    // Find sources with errors
    List<CompanySource> findByLastErrorNotNull();
    
    // Count active sources
    long countByActiveTrue();
}