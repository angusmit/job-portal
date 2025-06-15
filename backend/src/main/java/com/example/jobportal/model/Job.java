package com.example.jobportal.model;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "jobs")
public class Job {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String title;
    
    @Column(nullable = false)
    private String company;
    
    @Column(nullable = false)
    private String location;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    private String jobType; // Full-time, Part-time, Contract, etc.
    
    private String salary;
    
    @Column(columnDefinition = "TEXT")
    private String requirements;
    
    @Column(nullable = false)
    private LocalDateTime postedDate = LocalDateTime.now();
    
    private boolean active = true;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posted_by_id", nullable = false)
    @JsonIgnoreProperties({"postedJobs", "savedJobs", "password", "hibernateLazyInitializer", "handler"})
    private User postedBy;
    
    @ManyToMany(mappedBy = "savedJobs")
    @JsonIgnoreProperties({"savedJobs", "postedJobs", "password"})
    private Set<User> savedByUsers = new HashSet<>();
    
    // New fields for approval workflow
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_id")
    @JsonIgnoreProperties({"postedJobs", "savedJobs", "password", "hibernateLazyInitializer", "handler"})
    private User approvedBy;
    
    private LocalDateTime approvedDate;
    
    private String rejectionReason;
    
    @PrePersist
    public void prePersist() {
        if (postedDate == null) {
            postedDate = LocalDateTime.now();
        }
        if (approvalStatus == null) {
            approvalStatus = ApprovalStatus.PENDING;
        }
    }
}