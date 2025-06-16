package com.example.jobportal.config;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Consumer;

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
            User admin = createUserIfNotExists(userRepository, "admin", user -> {
                user.setEmail("admin@jobportal.com");
                user.setPassword(passwordEncoder.encode("admin123"));
                user.setRole(UserRole.ADMIN);
                user.setFirstName("Admin");
                user.setLastName("User");
                user.setActive(true);
            });

            // Create Employer 1
            User employer1 = createUserIfNotExists(userRepository, "techcorp", user -> {
                user.setEmail("hr@techcorp.com");
                user.setPassword(passwordEncoder.encode("password123"));
                user.setRole(UserRole.EMPLOYER);
                user.setFirstName("Tech");
                user.setLastName("Corp");
                user.setCompanyName("TechCorp Solutions");
                user.setCompanyDescription("Leading technology solutions provider");
                user.setCompanyWebsite("https://techcorp.com");
                user.setActive(true);
            });

            // Create Employer 2
            User employer2 = createUserIfNotExists(userRepository, "webinc", user -> {
                user.setEmail("hr@webinc.com");
                user.setPassword(passwordEncoder.encode("password123"));
                user.setRole(UserRole.EMPLOYER);
                user.setFirstName("Web");
                user.setLastName("Inc");
                user.setCompanyName("Web Inc");
                user.setCompanyDescription("Web development specialists");
                user.setActive(true);
            });

            // Create Job Seeker 1
            createUserIfNotExists(userRepository, "johndoe", user -> {
                user.setEmail("john@example.com");
                user.setPassword(passwordEncoder.encode("password123"));
                user.setRole(UserRole.JOB_SEEKER);
                user.setFirstName("John");
                user.setLastName("Doe");
                user.setSkills("Java, Spring Boot, React");
                user.setExperience("5 years");
                user.setActive(true);
            });

            // Create Job Seeker 2
            createUserIfNotExists(userRepository, "janesmith", user -> {
                user.setEmail("jane@example.com");
                user.setPassword(passwordEncoder.encode("password123"));
                user.setRole(UserRole.JOB_SEEKER);
                user.setFirstName("Jane");
                user.setLastName("Smith");
                user.setSkills("Python, Django, JavaScript");
                user.setExperience("3 years");
                user.setActive(true);
            });

            // Check if jobs exist to avoid duplication
            if (jobRepository.count() == 0) {
                // Job 1 - Approved
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
                job1.setApprovedBy(admin.getUsername());
                job1.setApprovedDate(LocalDateTime.now());
                jobRepository.save(job1);

                // Job 2 - Approved
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
                job2.setApprovedBy(admin.getUsername());
                job2.setApprovedDate(LocalDateTime.now());
                jobRepository.save(job2);

                // Job 3 - Pending
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

                // Job 4 - Pending
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
            }

            System.out.println("Sample data initialized successfully!");
            System.out.println("Created users: admin, techcorp, webinc, johndoe, janesmith");
            System.out.println("Created jobs: 2 approved, 2 pending approval");
        };
    }

    private User createUserIfNotExists(UserRepository repo, String username, Consumer<User> setup) {
        Optional<User> existing = repo.findByUsername(username);
        if (existing.isPresent()) {
            return existing.get();
        }
        User user = new User();
        user.setUsername(username);
        setup.accept(user);
        return repo.save(user);
    }
}
