package com.example.jobportal.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.jobportal.model.ApplicationStatus;
import com.example.jobportal.model.Job;
import com.example.jobportal.model.JobApplication;
import com.example.jobportal.model.User;

@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {
    
    // Find application by job and applicant (to check for duplicates)
    Optional<JobApplication> findByJobAndApplicant(Job job, User applicant);
    
    // Find all applications for a job
    List<JobApplication> findByJob(Job job);
    
    // Find all applications by a user
    List<JobApplication> findByApplicant(User applicant);
    
    // Find applications by status
    List<JobApplication> findByStatus(ApplicationStatus status);
    
    // Find applications for jobs posted by an employer
    @Query("SELECT ja FROM JobApplication ja WHERE ja.job.postedBy = :employer")
    List<JobApplication> findByEmployer(@Param("employer") User employer);
    
    // Find applications for a specific job by an employer
    @Query("SELECT ja FROM JobApplication ja WHERE ja.job.id = :jobId AND ja.job.postedBy = :employer")
    List<JobApplication> findByJobIdAndEmployer(@Param("jobId") Long jobId, @Param("employer") User employer);
    
    // Count applications for a job
    long countByJob(Job job);
    
    // Count applications by status for a job
    long countByJobAndStatus(Job job, ApplicationStatus status);
    
    // Find top applications by match score for a job
    @Query("SELECT ja FROM JobApplication ja WHERE ja.job = :job ORDER BY ja.matchScore DESC")
    List<JobApplication> findTopApplicationsByJob(@Param("job") Job job);
    
    // Check if user has applied for a job
    boolean existsByJobAndApplicant(Job job, User applicant);
}