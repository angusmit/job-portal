package com.example.jobportal.repository;

import com.example.jobportal.model.Job;
import com.example.jobportal.model.User;
import com.example.jobportal.model.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobRepository extends JpaRepository<Job, Long> {
    
    // Search in title, company, or description (only approved jobs)
    @Query("SELECT j FROM Job j WHERE j.approvalStatus = 'APPROVED' AND (" +
           "LOWER(j.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(j.company) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(j.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Job> searchApprovedJobs(@Param("query") String query);
    
    // Search all jobs regardless of status (for admin)
    @Query("SELECT j FROM Job j WHERE " +
           "LOWER(j.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(j.company) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(j.description) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Job> searchJobs(@Param("query") String query);
    
    // Filter by approval status
    List<Job> findByApprovalStatus(ApprovalStatus status);
    
    // Filter by location and approval status
    List<Job> findByLocationContainingAndApprovalStatus(String location, ApprovalStatus status);
    
    // Filter by job type and approval status
    List<Job> findByJobTypeContainingAndApprovalStatus(String jobType, ApprovalStatus status);
    
    // Filter by location, job type and approval status
    List<Job> findByLocationContainingAndJobTypeContainingAndApprovalStatus(
        String location, String jobType, ApprovalStatus status);
    
    // Find jobs posted by a specific employer
    List<Job> findByPostedBy(User employer);
    
    // Find jobs by employer or approval status (for employer to see their own + approved)
    @Query("SELECT j FROM Job j WHERE j.postedBy = :employer OR j.approvalStatus = :status")
    List<Job> findByPostedByOrApprovalStatus(@Param("employer") User employer, 
                                           @Param("status") ApprovalStatus status);
    
    // Count by approval status
    long countByApprovalStatus(ApprovalStatus status);
    
    // Old methods for backward compatibility
    List<Job> findByLocationContaining(String location);
    List<Job> findByJobTypeContaining(String jobType);
    List<Job> findByLocationContainingAndJobTypeContaining(String location, String jobType);
}