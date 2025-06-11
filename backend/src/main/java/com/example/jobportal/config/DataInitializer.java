package com.example.jobportal.config;

import com.example.jobportal.model.Job;
import com.example.jobportal.model.User;
import com.example.jobportal.model.UserRole;
import com.example.jobportal.model.ApprovalStatus;
import com.example.jobportal.repository.JobRepository;
import com.example.jobportal.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private JobRepository jobRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // Create sample users
        User admin = createUser("admin", "admin@jobportal.com", "admin123", 
                              UserRole.ADMIN, "Admin", "User");
        
        User employer1 = createUser("techcorp", "hr@techcorp.com", "password123", 
                                  UserRole.EMPLOYER, "John", "Smith");
        employer1.setCompanyName("Tech Corp");
        employer1.setCompanyDescription("Leading technology company");
        employer1.setCompanyWebsite("https://techcorp.com");
        userRepository.save(employer1);
        
        User employer2 = createUser("webinc", "hr@webinc.com", "password123", 
                                  UserRole.EMPLOYER, "Jane", "Doe");
        employer2.setCompanyName("Web Solutions Inc");
        employer2.setCompanyDescription("Web development experts");
        userRepository.save(employer2);
        
        User jobSeeker1 = createUser("johndoe", "john@example.com", "password123", 
                                   UserRole.JOB_SEEKER, "John", "Doe");
        jobSeeker1.setResumeSummary("Experienced software developer");
        jobSeeker1.setSkills("Java, Spring Boot, React");
        userRepository.save(jobSeeker1);
        
        User jobSeeker2 = createUser("janesmith", "jane@example.com", "password123", 
                                   UserRole.JOB_SEEKER, "Jane", "Smith");
        jobSeeker2.setResumeSummary("Frontend developer with 5 years experience");
        jobSeeker2.setSkills("React, TypeScript, CSS");
        userRepository.save(jobSeeker2);
        
        // Create sample jobs with employers
        createSampleJob("Software Engineer", employer1, "San Francisco, CA", 
            "We are looking for a talented Software Engineer to join our team...",
            "Full-time", "$120,000 - $150,000",
            "3+ years experience, Java, Spring Boot, React");

        createSampleJob("Frontend Developer", employer2, "New York, NY", 
            "Seeking a creative Frontend Developer with strong React skills...",
            "Full-time", "$100,000 - $130,000",
            "2+ years experience, React, TypeScript, CSS");

        createSampleJob("Data Scientist", employer1, "Seattle, WA", 
            "Join our data science team to work on cutting-edge ML projects...",
            "Full-time", "$130,000 - $160,000",
            "Python, Machine Learning, SQL, Statistics");

        createSampleJob("UX Designer", employer2, "Los Angeles, CA", 
            "Looking for a UX Designer to create amazing user experiences...",
            "Contract", "$80/hour",
            "Portfolio required, Figma, User Research");

        createSampleJob("DevOps Engineer", employer1, "Austin, TX", 
            "Help us build and maintain our cloud infrastructure...",
            "Full-time", "$110,000 - $140,000",
            "AWS, Docker, Kubernetes, CI/CD");

        createSampleJob("Marketing Intern", employer2, "Boston, MA", 
            "Great opportunity for students to gain marketing experience...",
            "Internship", "$20/hour",
            "Currently enrolled in college, Social Media savvy");

        System.out.println("Sample data created successfully!");
        System.out.println("Created users:");
        System.out.println("- Admin: admin/admin123");
        System.out.println("- Employers: techcorp/password123, webinc/password123");
        System.out.println("- Job Seekers: johndoe/password123, janesmith/password123");
    }
    
    private User createUser(String username, String email, String password, 
                          UserRole role, String firstName, String lastName) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setActive(true);
        return userRepository.save(user);
    }

    private void createSampleJob(String title, User employer, String location, 
                                String description, String jobType, String salary, 
                                String requirements) {
        Job job = new Job();
        job.setTitle(title);
        job.setCompany(employer.getCompanyName());
        job.setLocation(location);
        job.setDescription(description);
        job.setJobType(jobType);
        job.setSalary(salary);
        job.setRequirements(requirements);
        job.setPostedBy(employer);
        
        // Auto-approve first 3 jobs for demo purposes
        if (jobRepository.count() < 3) {
            job.setApprovalStatus(ApprovalStatus.APPROVED);
            job.setApprovedDate(LocalDateTime.now());
            job.setApprovedBy(userRepository.findByUsername("admin").orElse(null));
        } else {
            job.setApprovalStatus(ApprovalStatus.PENDING);
        }
        
        jobRepository.save(job);
    }
}