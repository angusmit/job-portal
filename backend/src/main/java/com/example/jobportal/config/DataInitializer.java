package com.example.jobportal.config;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.jobportal.model.ApprovalStatus;
import com.example.jobportal.model.Job;
import com.example.jobportal.model.User;
import com.example.jobportal.model.UserRole;
import com.example.jobportal.repository.JobRepository;
import com.example.jobportal.repository.UserRepository;

@Configuration
public class DataInitializer {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Bean
    CommandLineRunner initDatabase(UserRepository userRepository, JobRepository jobRepository) {
        return args -> {
            // Create Admin user
            User admin = new User();
            admin.setUsername("admin");
            admin.setEmail("admin@jobportal.com");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setRole(UserRole.ADMIN);
            admin.setFirstName("Admin");
            admin.setLastName("User");
            admin.setActive(true);
            userRepository.save(admin);

            // Create Employer users
            User employer1 = new User();
            employer1.setUsername("techcorp");
            employer1.setEmail("hr@techcorp.com");
            employer1.setPassword(passwordEncoder.encode("password123"));
            employer1.setRole(UserRole.EMPLOYER);
            employer1.setFirstName("Tech");
            employer1.setLastName("Corp");
            employer1.setCompanyName("TechCorp Solutions");
            employer1.setCompanyDescription("Leading technology solutions provider");
            employer1.setCompanyWebsite("https://techcorp.com");
            employer1.setActive(true);
            userRepository.save(employer1);

            User employer2 = new User();
            employer2.setUsername("webinc");
            employer2.setEmail("hr@webinc.com");
            employer2.setPassword(passwordEncoder.encode("password123"));
            employer2.setRole(UserRole.EMPLOYER);
            employer2.setFirstName("Web");
            employer2.setLastName("Inc");
            employer2.setCompanyName("Web Inc");
            employer2.setCompanyDescription("Web development specialists");
            employer2.setActive(true);
            userRepository.save(employer2);

            // Create Job Seeker users
            User jobSeeker1 = new User();
            jobSeeker1.setUsername("johndoe");
            jobSeeker1.setEmail("john@example.com");
            jobSeeker1.setPassword(passwordEncoder.encode("password123"));
            jobSeeker1.setRole(UserRole.JOB_SEEKER);
            jobSeeker1.setFirstName("John");
            jobSeeker1.setLastName("Doe");
            jobSeeker1.setSkills("Java, Spring Boot, React");
            jobSeeker1.setExperience("5 years");
            jobSeeker1.setActive(true);
            userRepository.save(jobSeeker1);

            User jobSeeker2 = new User();
            jobSeeker2.setUsername("janesmith");
            jobSeeker2.setEmail("jane@example.com");
            jobSeeker2.setPassword(passwordEncoder.encode("password123"));
            jobSeeker2.setRole(UserRole.JOB_SEEKER);
            jobSeeker2.setFirstName("Jane");
            jobSeeker2.setLastName("Smith");
            jobSeeker2.setSkills("Python, Django, JavaScript");
            jobSeeker2.setExperience("3 years");
            jobSeeker2.setActive(true);
            userRepository.save(jobSeeker2);

            // Create sample jobs (mix of approved and pending)
            Job job1 = new Job();
            job1.setTitle("Senior Java Developer");
            job1.setCompany("TechCorp Solutions");
            job1.setLocation("New York, NY");
            job1.setDescription("We are looking for an experienced Java developer to join our team...");
            job1.setJobType("Full-time");
            job1.setSalary("$120,000 - $150,000");
            job1.setRequirements("5+ years Java experience, Spring Boot, Microservices");
            job1.setPostedBy(employer1);
            job1.setPostedDate(LocalDateTime.now());
            job1.setActive(true);
            job1.setApprovalStatus(ApprovalStatus.APPROVED);
            job1.setApprovedBy(admin);
            job1.setApprovedDate(LocalDateTime.now());
            jobRepository.save(job1);

            Job job2 = new Job();
            job2.setTitle("Frontend Developer");
            job2.setCompany("Web Inc");
            job2.setLocation("San Francisco, CA");
            job2.setDescription("Looking for a talented frontend developer with React expertise...");
            job2.setJobType("Full-time");
            job2.setSalary("$100,000 - $130,000");
            job2.setRequirements("3+ years React, TypeScript, CSS");
            job2.setPostedBy(employer2);
            job2.setPostedDate(LocalDateTime.now());
            job2.setActive(true);
            job2.setApprovalStatus(ApprovalStatus.APPROVED);
            job2.setApprovedBy(admin);
            job2.setApprovedDate(LocalDateTime.now());
            jobRepository.save(job2);

            Job job3 = new Job();
            job3.setTitle("Python Developer");
            job3.setCompany("TechCorp Solutions");
            job3.setLocation("Remote");
            job3.setDescription("Remote Python developer position for our data team...");
            job3.setJobType("Full-time");
            job3.setSalary("$110,000 - $140,000");
            job3.setRequirements("Python, Django, PostgreSQL, AWS");
            job3.setPostedBy(employer1);
            job3.setPostedDate(LocalDateTime.now());
            job3.setActive(true);
            job3.setApprovalStatus(ApprovalStatus.PENDING);
            jobRepository.save(job3);

            Job job4 = new Job();
            job4.setTitle("DevOps Engineer");
            job4.setCompany("Web Inc");
            job4.setLocation("Austin, TX");
            job4.setDescription("Seeking a DevOps engineer to manage our cloud infrastructure...");
            job4.setJobType("Contract");
            job4.setSalary("$130,000 - $160,000");
            job4.setRequirements("Kubernetes, AWS, CI/CD, Terraform");
            job4.setPostedBy(employer2);
            job4.setPostedDate(LocalDateTime.now());
            job4.setActive(true);
            job4.setApprovalStatus(ApprovalStatus.PENDING);
            jobRepository.save(job4);

            System.out.println("Sample data initialized successfully!");
            System.out.println("Created users: admin, techcorp, webinc, johndoe, janesmith");
            System.out.println("Created jobs: 2 approved, 2 pending approval");
        };
    }
}