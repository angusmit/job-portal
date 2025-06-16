package com.example.jobportal.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.jobportal.model.CompanySource;
import com.example.jobportal.model.ScrapedJob;

@Repository
public interface ScrapedJobRepository extends JpaRepository<ScrapedJob, Long> {
    
    // Find by source and content hash
    Optional<ScrapedJob> findBySourceAndContentHash(CompanySource source, String contentHash);
    
    // Find by source and external ID
    Optional<ScrapedJob> findBySourceAndExternalId(CompanySource source, String externalId);
    
    // Find by source
    List<ScrapedJob> findBySource(CompanySource source);

    // Find active jobs by source
    List<ScrapedJob> findBySourceAndActiveTrue(CompanySource source);
    
    // Find jobs not imported yet
    List<ScrapedJob> findByImportedFalseAndActiveTrueAndDuplicateFalse();
    
    // Find jobs not seen recently
    List<ScrapedJob> findBySourceAndLastSeenAtBeforeAndActiveTrue(
        CompanySource source, LocalDateTime threshold);
    
    // Count jobs by source
    long countBySource(CompanySource source);
    
    // Count active jobs by source
    long countBySourceAndActiveTrue(CompanySource source);
    
    // Count imported jobs
    long countByImportedTrue();
    
    // Find duplicate jobs
    List<ScrapedJob> findByDuplicateTrue();
    
    // Find jobs with changes
    List<ScrapedJob> findByHasChangesTrue();
    
    // Find recent jobs
    @Query("SELECT sj FROM ScrapedJob sj WHERE sj.lastSeenAt >= :since ORDER BY sj.lastSeenAt DESC")
    List<ScrapedJob> findRecentJobs(@Param("since") LocalDateTime since);
}