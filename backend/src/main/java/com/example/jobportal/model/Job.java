package com.example.jobportal.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;
import java.time.LocalDateTime;

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
    
    // New fields for approval system
    @Enumerated(EnumType.STRING)
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;
    
    private LocalDateTime approvedDate;
    
    private String rejectionReason;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posted_by")
    @JsonIgnore
    private User postedBy;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    @JsonIgnore
    private User approvedBy;
    
    // Add this method to get employer info without circular reference
    public String getPostedByUsername() {
        return postedBy != null ? postedBy.getUsername() : null;
    }
    
    public String getPostedByCompany() {
        return postedBy != null ? postedBy.getCompanyName() : company;
    }
    
    public Long getPostedById() {
        return postedBy != null ? postedBy.getId() : null;
    }
}
