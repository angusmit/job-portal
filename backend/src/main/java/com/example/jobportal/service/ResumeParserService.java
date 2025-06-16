package com.example.jobportal.service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ResumeParserService {
    
    private final Tika tika = new Tika();
    
    // Common skills keywords to look for
    private static final Set<String> SKILL_KEYWORDS = new HashSet<>(Arrays.asList(
        // Programming Languages
        "java", "python", "javascript", "typescript", "c++", "c#", "ruby", "go", "rust", "kotlin",
        "swift", "php", "scala", "r", "matlab", "perl", "shell", "bash", "powershell",
        
        // Web Technologies
        "html", "css", "react", "angular", "vue", "node.js", "express", "django", "flask",
        "spring", "spring boot", "asp.net", "jquery", "bootstrap", "tailwind", "sass", "webpack",
        
        // Databases
        "sql", "mysql", "postgresql", "mongodb", "redis", "elasticsearch", "cassandra", "oracle",
        "sqlite", "dynamodb", "firebase", "neo4j",
        
        // Cloud & DevOps
        "aws", "azure", "gcp", "google cloud", "docker", "kubernetes", "jenkins", "git", "github",
        "gitlab", "ci/cd", "terraform", "ansible", "linux", "nginx", "apache",
        
        // Data Science & AI
        "machine learning", "deep learning", "tensorflow", "pytorch", "scikit-learn", "pandas",
        "numpy", "data analysis", "statistics", "nlp", "computer vision", "ai", "neural networks",
        
        // Other Technologies
        "rest api", "graphql", "microservices", "agile", "scrum", "jira", "blockchain",
        "iot", "security", "testing", "junit", "selenium", "jest"
    ));
    
    // Education levels
    private static final List<String> EDUCATION_LEVELS = Arrays.asList(
        "ph.d", "phd", "doctorate",
        "master", "m.s.", "ms", "mba", "m.a.",
        "bachelor", "b.s.", "bs", "b.a.", "ba", "b.tech", "b.e.",
        "associate",
        "diploma",
        "high school", "secondary"
    );
    
    public Map<String, Object> parseResume(InputStream inputStream, String fileName) {
        Map<String, Object> parsedData = new HashMap<>();
        
        try {
            // Extract text content
            String content = extractText(inputStream);
            parsedData.put("rawText", content);
            
            // Extract structured information
            parsedData.put("email", extractEmail(content));
            parsedData.put("phone", extractPhone(content));
            parsedData.put("skills", extractSkills(content));
            parsedData.put("experience", extractExperience(content));
            parsedData.put("education", extractEducation(content));
            parsedData.put("educationLevel", detectEducationLevel(content));
            parsedData.put("experienceYears", estimateExperienceYears(content));
            
            // Extract sections
            Map<String, String> sections = extractSections(content);
            parsedData.put("sections", sections);
            
            parsedData.put("success", true);
            
        } catch (Exception e) {
            log.error("Error parsing resume: " + fileName, e);
            parsedData.put("success", false);
            parsedData.put("error", e.getMessage());
        }
        
        return parsedData;
    }
    
    private String extractText(InputStream inputStream) throws Exception {
        BodyContentHandler handler = new BodyContentHandler(-1); // -1 for unlimited
        Metadata metadata = new Metadata();
        ParseContext parseContext = new ParseContext();
        AutoDetectParser parser = new AutoDetectParser();
        
        parser.parse(inputStream, handler, metadata, parseContext);
        return handler.toString();
    }
    
    private String extractEmail(String text) {
        Pattern pattern = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group() : "";
    }
    
    private String extractPhone(String text) {
        // Multiple phone patterns
        List<Pattern> patterns = Arrays.asList(
            Pattern.compile("\\(\\d{3}\\)\\s*\\d{3}-\\d{4}"), // (123) 456-7890
            Pattern.compile("\\d{3}-\\d{3}-\\d{4}"), // 123-456-7890
            Pattern.compile("\\d{3}\\.\\d{3}\\.\\d{4}"), // 123.456.7890
            Pattern.compile("\\+\\d{1,3}\\s*\\d{10}"), // +1 1234567890
            Pattern.compile("\\d{10}") // 1234567890
        );
        
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return matcher.group();
            }
        }
        return "";
    }
    
    private List<String> extractSkills(String text) {
        Set<String> foundSkills = new HashSet<>();
        String lowerText = text.toLowerCase();
        
        for (String skill : SKILL_KEYWORDS) {
            // Look for word boundaries to avoid partial matches
            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(skill) + "\\b", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(lowerText);
            if (matcher.find()) {
                foundSkills.add(skill);
            }
        }
        
        return new ArrayList<>(foundSkills);
    }
    
    private String detectEducationLevel(String text) {
        String lowerText = text.toLowerCase();
        
        for (String level : EDUCATION_LEVELS) {
            if (lowerText.contains(level)) {
                if (level.startsWith("ph") || level.equals("doctorate")) {
                    return "PhD";
                } else if (level.startsWith("master") || level.equals("mba") || level.equals("m.s.") || level.equals("ms")) {
                    return "Master's";
                } else if (level.startsWith("bachelor") || level.startsWith("b.")) {
                    return "Bachelor's";
                } else if (level.equals("associate")) {
                    return "Associate";
                } else if (level.equals("diploma")) {
                    return "Diploma";
                } else if (level.contains("high school") || level.equals("secondary")) {
                    return "High School";
                }
            }
        }
        
        return "Not Specified";
    }
    
    private Double estimateExperienceYears(String text) {
        // Look for patterns like "X years of experience" or "X+ years"
        List<Pattern> patterns = Arrays.asList(
            Pattern.compile("(\\d+)\\+?\\s*years?\\s*(?:of\\s*)?experience", Pattern.CASE_INSENSITIVE),
            Pattern.compile("experience\\s*:?\\s*(\\d+)\\s*years?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(\\d+)\\s*years?\\s*(?:of\\s*)?professional", Pattern.CASE_INSENSITIVE)
        );
        
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                try {
                    return Double.parseDouble(matcher.group(1));
                } catch (NumberFormatException e) {
                    // Continue to next pattern
                }
            }
        }
        
        // Try to estimate from work history dates
        return estimateFromWorkHistory(text);
    }
    
    private Double estimateFromWorkHistory(String text) {
        // Look for date ranges in work history
        Pattern yearPattern = Pattern.compile("(19|20)\\d{2}");
        Matcher matcher = yearPattern.matcher(text);
        
        List<Integer> years = new ArrayList<>();
        while (matcher.find()) {
            try {
                years.add(Integer.parseInt(matcher.group()));
            } catch (NumberFormatException e) {
                // Skip invalid years
            }
        }
        
        if (years.size() >= 2) {
            Collections.sort(years);
            int currentYear = Calendar.getInstance().get(Calendar.YEAR);
            int earliestYear = years.get(0);
            int latestYear = years.get(years.size() - 1);
            
            // If latest year is current or recent, calculate from earliest
            if (latestYear >= currentYear - 2) {
                return (double) (currentYear - earliestYear);
            } else {
                return (double) (latestYear - earliestYear);
            }
        }
        
        return 0.0;
    }
    
    private String extractExperience(String text) {
        // Look for experience section
        String[] experienceKeywords = {"experience", "work history", "employment", "professional experience"};
        return extractSection(text, experienceKeywords);
    }
    
    private String extractEducation(String text) {
        // Look for education section
        String[] educationKeywords = {"education", "academic", "qualification", "degree"};
        return extractSection(text, educationKeywords);
    }
    
    private String extractSection(String text, String[] keywords) {
        String lowerText = text.toLowerCase();
        int startIndex = -1;
        
        // Find the start of the section
        for (String keyword : keywords) {
            int index = lowerText.indexOf(keyword);
            if (index != -1 && (startIndex == -1 || index < startIndex)) {
                startIndex = index;
            }
        }
        
        if (startIndex == -1) {
            return "";
        }
        
        // Find the end of the section (next section or end of text)
        String[] allSectionKeywords = {
            "experience", "education", "skills", "projects", "certifications",
            "references", "interests", "hobbies", "achievements", "awards"
        };
        
        int endIndex = text.length();
        for (String keyword : allSectionKeywords) {
            int index = lowerText.indexOf(keyword, startIndex + 50); // +50 to skip current section header
            if (index != -1 && index < endIndex) {
                endIndex = index;
            }
        }
        
        return text.substring(startIndex, endIndex).trim();
    }
    
    private Map<String, String> extractSections(String text) {
        Map<String, String> sections = new HashMap<>();
        
        // Common section headers
        String[][] sectionKeywords = {
            {"summary", "profile", "objective"},
            {"experience", "work history", "employment"},
            {"education", "academic"},
            {"skills", "technical skills", "competencies"},
            {"projects", "project experience"},
            {"certifications", "certificates"},
            {"achievements", "accomplishments", "awards"}
        };
        
        for (String[] keywords : sectionKeywords) {
            String section = extractSection(text, keywords);
            if (!section.isEmpty()) {
                sections.put(keywords[0], section);
            }
        }
        
        return sections;
    }
}