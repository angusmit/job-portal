package com.example.jobportal.model;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

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
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "resumes")
public class Resume {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(nullable = false)
    private String fileName;
    
    @Column(nullable = false)
    private String filePath;
    
    @Column(nullable = false)
    private String fileType;
    
    private Long fileSize;
    
    @Column(length = 10000)
    private String parsedContent;
    
    @Column(length = 10000)
    private String extractedText;
    
    @Column(length = 5000)
    private String parsedData; // JSON string containing parsed data
    
    @Column(length = 2000)
    private String extractedSkills;
    
    private String skills;
    private Double experienceYears;
    private String educationLevel;
    
    private String extractedEducation;
    private String extractedExperience;
    private String extractedEmail;
    private String extractedPhone;
    
    @Enumerated(EnumType.STRING)
    private ProcessingStatus processingStatus = ProcessingStatus.PENDING;
    
    @Enumerated(EnumType.STRING)
    private ProcessingStatus status = ProcessingStatus.PENDING;
    
    private boolean temporaryOnly = false;
    
    private String sessionId;
    
    private LocalDateTime uploadedAt = LocalDateTime.now();
    private LocalDateTime processedAt;
    private LocalDateTime expiresAt;
    
    @Column(length = 1000)
    private String processingError;
    
    // Helper method to get parsed data as Map
    @Transient
    public Map<String, Object> getParsedDataAsMap() {
        if (parsedData == null || parsedData.isEmpty()) {
            return new HashMap<>();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(parsedData, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}