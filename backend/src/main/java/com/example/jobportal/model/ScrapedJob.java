package com.example.jobportal.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "scraped_jobs")
public class ScrapedJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String externalId;
    private String externalUrl;
    private String title;
    private String company;
    private String location;
    
    @Column(length = 5000)
    private String description;
    
    @Column(length = 2000)
    private String requirements;
    
    private String salary;
    private String jobType;
    private String jobUrl;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id")
    private CompanySource source;
    
    private LocalDateTime scrapedAt = LocalDateTime.now();
    private LocalDateTime lastSeenAt = LocalDateTime.now();
    private LocalDateTime postedDate;
    private LocalDateTime importedAt;
    
    private boolean imported = false;
    private boolean duplicate = false;
    private boolean active = true;
    private boolean hasChanges = false;
    
    @Column(length = 1000)
    private String rawData;
    
    private String contentHash;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "duplicate_of_id")
    private ScrapedJob duplicateOf;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imported_job_id")
    private Job importedJob;
}