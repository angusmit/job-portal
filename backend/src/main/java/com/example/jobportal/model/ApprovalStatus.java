package com.example.jobportal.model;

/**
 * Enum representing the approval status of a job posting.
 * 
 * PENDING - Job is awaiting admin review
 * APPROVED - Job has been approved and is visible to job seekers
 * REJECTED - Job has been rejected by admin with a reason
 */
public enum ApprovalStatus {
    PENDING("Pending Approval"),
    APPROVED("Approved"),
    REJECTED("Rejected");
    
    private final String displayName;
    
    ApprovalStatus(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    @Override
    public String toString() {
        return this.displayName;
    }
}