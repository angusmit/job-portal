package com.example.jobportal.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank
    @Size(min = 3, max = 50)
    @Column(unique = true)
    private String username;
    
    @NotBlank
    @Email
    @Column(unique = true)
    private String email;
    
    @NotBlank
    @Size(min = 6)
    @JsonIgnore
    private String password;
    
    @Enumerated(EnumType.STRING)
    private UserRole role;
    
    private String firstName;
    private String lastName;
    private String phoneNumber;
    
    // Job Seeker specific fields
    @Column(length = 2000)
    private String resumeSummary;
    private String skills;
    private String experience;
    private String education;
    
    // Employer specific fields
    private String companyName;
    private String companyDescription;
    private String companyWebsite;
    private String companySize;
    
    private LocalDateTime createdAt = LocalDateTime.now();
    private boolean active = true;
    
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "saved_jobs",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "job_id"))
    @JsonIgnore
    private Set<Job> savedJobs = new HashSet<>();
    
    @OneToMany(mappedBy = "postedBy", cascade = CascadeType.ALL)
    @JsonIgnore
    private Set<Job> postedJobs = new HashSet<>();
}