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
@Data
@NoArgsConstructor
@AllArgsConstructor
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
    
    @Column(length = 5000)
    private String description;
    
    @Column(nullable = false)
    private String jobType; // Full-time, Part-time, Contract, etc.
    
    private String salary;
    
    @Column(length = 2000)
    private String requirements;
    
    private LocalDateTime postedDate = LocalDateTime.now();
    
    private boolean active = true;
    
    // For admin approval workflow
    @Enumerated(EnumType.STRING)
    private JobStatus status = JobStatus.PENDING;
    
    @Enumerated(EnumType.STRING)
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;
    
    private LocalDateTime approvedAt;
    private LocalDateTime approvedDate;
    private String approvedBy;
    private String rejectionReason;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posted_by")
    @JsonIgnore
    private User postedBy;
    
    // Add this method to get employer info without circular reference
    public String getPostedByUsername() {
        return postedBy != null ? postedBy.getUsername() : null;
    }
    
    public String getPostedByCompany() {
        return postedBy != null ? postedBy.getCompanyName() : company;
    }
}