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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "scraped_jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
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

    private LocalDateTime scrapedAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime postedDate;
    private LocalDateTime importedAt;

    private boolean imported = false;
    private boolean duplicate = false;

    @Column(name = "is_active")
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

    @PrePersist
    public void prePersist() {
        if (scrapedAt == null) scrapedAt = LocalDateTime.now();
        if (lastSeenAt == null) lastSeenAt = LocalDateTime.now();
    }
}
