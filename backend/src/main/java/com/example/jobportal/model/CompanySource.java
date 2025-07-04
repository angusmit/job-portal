package com.example.jobportal.model;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "company_sources")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanySource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String companyName;

    @Column(nullable = false, unique = true)
    private String careerPageUrl;

    // Scraping frequency
    @Enumerated(EnumType.STRING)
    private ScrapingFrequency frequency = ScrapingFrequency.DAILY;

    private boolean active = true;

    // CSS selectors for scraping
    private String jobListSelector;
    private String jobTitleSelector;
    private String jobLocationSelector;
    private String jobDescriptionSelector;
    private String jobTypeSelector;
    private String jobSalarySelector;
    private String jobUrlSelector;
    private String jobRequirementsSelector;
    private String nextPageSelector;

    // Scraping tracking
    private LocalDateTime lastScrapedAt;
    private LocalDateTime nextScheduledScrape;
    private LocalDateTime lastErrorAt;

    private int totalJobsScraped = 0;
    private int successfulScrapes = 0;
    private int failedScrapes = 0;
    private int lastScrapeJobCount = 0;

    @Column(length = 1000)
    private String lastError;

    @OneToMany(mappedBy = "source", cascade = CascadeType.ALL)
    private List<ScrapedJob> scrapedJobs;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Schedule scraper logic
    public void updateNextScheduledScrape() {
        if (lastScrapedAt == null) {
            lastScrapedAt = LocalDateTime.now();
        }

        switch (frequency) {
            case HOURLY:
                nextScheduledScrape = lastScrapedAt.plusHours(1);
                break;
            case DAILY:
                nextScheduledScrape = lastScrapedAt.plusDays(1);
                break;
            case WEEKLY:
                nextScheduledScrape = lastScrapedAt.plusWeeks(1);
                break;
            case MONTHLY:
                nextScheduledScrape = lastScrapedAt.plusMonths(1);
                break;
            default:
                nextScheduledScrape = lastScrapedAt.plusDays(1);
        }
    }
}
