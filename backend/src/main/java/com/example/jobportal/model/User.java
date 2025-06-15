package com.example.jobportal.model;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Entity
@Table(name = "users", 
       uniqueConstraints = {
           @UniqueConstraint(columnNames = "username"),
           @UniqueConstraint(columnNames = "email")
       })
@Data
@EqualsAndHashCode(exclude = {"savedJobs", "postedJobs"})
@ToString(exclude = {"savedJobs", "postedJobs", "password"})
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String username;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(nullable = false)
    @JsonIgnore
    private String password;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;
    
    private String firstName;
    private String lastName;
    private String phoneNumber;
    
    // Job Seeker specific fields
    private String resumeSummary;
    private String skills;
    private String experience;
    private String education;
    
    // Employer specific fields
    private String companyName;
    @Column(columnDefinition = "TEXT")
    private String companyDescription;
    private String companyWebsite;
    
    // Account status
    @Column(nullable = false)
    private boolean active = true;
    
    // Timestamps
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    private LocalDateTime lastLogin;
    
    // Job relationships
    @ManyToMany
    @JoinTable(
        name = "user_saved_jobs",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "job_id")
    )
    @JsonIgnore
    private Set<Job> savedJobs = new HashSet<>();
    
    @OneToMany(mappedBy = "postedBy", cascade = CascadeType.ALL)
    @JsonIgnore
    private Set<Job> postedJobs = new HashSet<>();
    
    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (!active) {
            active = true;
        }
    }
}