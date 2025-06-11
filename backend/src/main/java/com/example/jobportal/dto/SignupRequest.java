package com.example.jobportal.dto;

import lombok.Data;
import jakarta.validation.constraints.*;

@Data
public class SignupRequest {
    @NotBlank
    @Size(min = 3, max = 50)
    private String username;
    
    @NotBlank
    @Size(max = 50)
    @Email
    private String email;
    
    @NotBlank
    @Size(min = 6, max = 40)
    private String password;
    
    private String role;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    
    // Employer specific fields
    private String companyName;
    private String companyDescription;
    private String companyWebsite;
}