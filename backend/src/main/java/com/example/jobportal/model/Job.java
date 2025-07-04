package com.example.jobportal.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Job {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String company;

    @Column(nullable = false)
    private String location;

    @Column(length = 5000)
    private String description;

    @Column(nullable = false)
    private String jobType; // Full-time, Part-time, etc.

    private String salary;

    @Column(length = 2000)
    private String requirements;

    @Column(columnDefinition = "TEXT")
    private String requiredSkills;

    @Column(columnDefinition = "TEXT")
    private String preferredSkills;

    private Integer experienceRequired = 0;

    private String seniorityLevel = "entry"; // entry, junior, mid, senior

    @Column(name = "posted_date")
    private LocalDateTime postedDate = LocalDateTime.now();

    private boolean active = true;

    // Admin workflow fields
    @Enumerated(EnumType.STRING)
    private JobStatus status = JobStatus.PENDING;

    @Enumerated(EnumType.STRING)
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    private LocalDateTime approvedAt;
    private LocalDateTime approvedDate;
    private String approvedBy;
    private String rejectionReason;

    // ML integration + employer link
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posted_by")
    @JsonIgnore
    private User postedBy;

    // Helper methods
    public String getPostedByUsername() {
        return postedBy != null ? postedBy.getUsername() : "Unknown";
    }

    public String getPostedByCompany() {
        return postedBy != null && postedBy.getCompanyName() != null
                ? postedBy.getCompanyName()
                : company != null ? company : "Unknown Company";
    }
}
