package com.example.jobportal.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.example.jobportal.model.Job;
import com.example.jobportal.repository.JobRepository;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private JobRepository jobRepository;

    @Override
    public void run(String... args) throws Exception {
        // Create sample jobs
        createSampleJob("Software Engineer", "Tech Corp", "San Francisco, CA", 
            "We are looking for a talented Software Engineer to join our team...",
            "Full-time", "$120,000 - $150,000",
            "3+ years experience, Java, Spring Boot, React");

        createSampleJob("Frontend Developer", "Web Solutions Inc", "New York, NY", 
            "Seeking a creative Frontend Developer with strong React skills...",
            "Full-time", "$100,000 - $130,000",
            "2+ years experience, React, TypeScript, CSS");

        createSampleJob("Data Scientist", "AI Innovations", "Seattle, WA", 
            "Join our data science team to work on cutting-edge ML projects...",
            "Full-time", "$130,000 - $160,000",
            "Python, Machine Learning, SQL, Statistics");

        createSampleJob("UX Designer", "Design Studio", "Los Angeles, CA", 
            "Looking for a UX Designer to create amazing user experiences...",
            "Contract", "$80/hour",
            "Portfolio required, Figma, User Research");

        createSampleJob("DevOps Engineer", "Cloud Systems", "Austin, TX", 
            "Help us build and maintain our cloud infrastructure...",
            "Full-time", "$110,000 - $140,000",
            "AWS, Docker, Kubernetes, CI/CD");

        createSampleJob("Marketing Intern", "Startup Hub", "Boston, MA", 
            "Great opportunity for students to gain marketing experience...",
            "Internship", "$20/hour",
            "Currently enrolled in college, Social Media savvy");

        System.out.println("Sample jobs created successfully!");
    }

    private void createSampleJob(String title, String company, String location, 
                                String description, String jobType, String salary, 
                                String requirements) {
        Job job = new Job();
        job.setTitle(title);
        job.setCompany(company);
        job.setLocation(location);
        job.setDescription(description);
        job.setJobType(jobType);
        job.setSalary(salary);
        job.setRequirements(requirements);
        jobRepository.save(job);
    }
}