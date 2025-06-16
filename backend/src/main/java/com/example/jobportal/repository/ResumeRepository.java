package com.example.jobportal.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.jobportal.model.ProcessingStatus;
import com.example.jobportal.model.Resume;
import com.example.jobportal.model.User;

@Repository
public interface ResumeRepository extends JpaRepository<Resume, Long> {
    
    // Find resumes by user
    List<Resume> findByUser(User user);
    
    // Find resumes by user excluding certain status
    List<Resume> findByUserAndStatusNot(User user, ProcessingStatus status);
    
    // Find by session ID
    List<Resume> findBySessionId(String sessionId);
    
    // Find expired temporary resumes
    @Query("SELECT r FROM Resume r WHERE r.temporaryOnly = true AND r.expiresAt < :now")
    List<Resume> findExpiredTemporaryResumes(@Param("now") LocalDateTime now);
    
    // Find resume by user and ID
    Optional<Resume> findByIdAndUser(Long id, User user);
    
    // Count resumes by user
    long countByUser(User user);
    
    // Find latest resume by user
    Optional<Resume> findFirstByUserOrderByUploadedAtDesc(User user);
    
    // Find resumes by status
    List<Resume> findByStatus(ProcessingStatus status);
    
    // Delete expired resumes
    void deleteByExpiresAtBefore(LocalDateTime dateTime);
}