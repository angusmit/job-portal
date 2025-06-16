package com.example.jobportal.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.jobportal.model.Job;
import com.example.jobportal.model.Resume;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CVMatchingService {
    
    // Weights for different matching criteria
    private static final double SKILL_WEIGHT = 0.4;
    private static final double EXPERIENCE_WEIGHT = 0.3;
    private static final double EDUCATION_WEIGHT = 0.2;
    private static final double KEYWORD_WEIGHT = 0.1;
    
    public Map<String, Object> calculateMatch(Resume resume, Job job) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Extract data from resume
            Map<String, Object> resumeData = resume.getParsedDataAsMap();
            List<String> resumeSkills = (List<String>) resumeData.getOrDefault("skills", new ArrayList<>());
            Double resumeExperience = resume.getExperienceYears() != null ? resume.getExperienceYears() : 0.0;
            String resumeEducation = resume.getEducationLevel() != null ? resume.getEducationLevel() : "";
            String resumeText = resume.getExtractedText() != null ? resume.getExtractedText().toLowerCase() : "";
            
            // Extract requirements from job
            String jobRequirements = job.getRequirements() != null ? job.getRequirements().toLowerCase() : "";
            String jobDescription = job.getDescription() != null ? job.getDescription().toLowerCase() : "";
            
            // Calculate individual scores
            double skillScore = calculateSkillMatch(resumeSkills, jobRequirements, jobDescription);
            double experienceScore = calculateExperienceMatch(resumeExperience, jobRequirements);
            double educationScore = calculateEducationMatch(resumeEducation, jobRequirements);
            double keywordScore = calculateKeywordMatch(resumeText, jobRequirements + " " + jobDescription);
            
            // Calculate weighted total score
            double totalScore = (skillScore * SKILL_WEIGHT) +
                               (experienceScore * EXPERIENCE_WEIGHT) +
                               (educationScore * EDUCATION_WEIGHT) +
                               (keywordScore * KEYWORD_WEIGHT);
            
            // Build detailed analysis
            Map<String, Object> analysis = new HashMap<>();
            analysis.put("skillMatch", buildSkillAnalysis(resumeSkills, jobRequirements, jobDescription));
            analysis.put("experienceMatch", buildExperienceAnalysis(resumeExperience, jobRequirements));
            analysis.put("educationMatch", buildEducationAnalysis(resumeEducation, jobRequirements));
            analysis.put("keywordMatch", buildKeywordAnalysis(resumeText, jobRequirements + " " + jobDescription));
            
            // Add recommendations
            List<String> recommendations = generateRecommendations(
                skillScore, experienceScore, educationScore, keywordScore, analysis
            );
            
            result.put("matchScore", Math.round(totalScore));
            result.put("skillScore", Math.round(skillScore));
            result.put("experienceScore", Math.round(experienceScore));
            result.put("educationScore", Math.round(educationScore));
            result.put("keywordScore", Math.round(keywordScore));
            result.put("analysis", analysis);
            result.put("recommendations", recommendations);
            result.put("isGoodMatch", totalScore >= 70);
            
        } catch (Exception e) {
            log.error("Error calculating match score", e);
            result.put("error", "Failed to calculate match score");
            result.put("matchScore", 0);
        }
        
        return result;
    }
    
    private double calculateSkillMatch(List<String> resumeSkills, String jobRequirements, String jobDescription) {
        if (resumeSkills.isEmpty()) {
            return 0.0;
        }
        
        Set<String> requiredSkills = extractSkillsFromText(jobRequirements + " " + jobDescription);
        if (requiredSkills.isEmpty()) {
            return 50.0; // Default score if no skills mentioned in job
        }
        
        Set<String> matchedSkills = new HashSet<>();
        for (String skill : resumeSkills) {
            if (requiredSkills.contains(skill.toLowerCase())) {
                matchedSkills.add(skill);
            }
        }
        
        return (matchedSkills.size() * 100.0) / requiredSkills.size();
    }
    
    private Set<String> extractSkillsFromText(String text) {
        Set<String> skills = new HashSet<>();
        String lowerText = text.toLowerCase();
        
        // Common technical skills to look for
        String[] commonSkills = {
            "java", "python", "javascript", "react", "angular", "spring", "node.js",
            "sql", "mongodb", "aws", "docker", "kubernetes", "git", "agile",
            "machine learning", "data analysis", "rest api", "microservices"
        };
        
        for (String skill : commonSkills) {
            if (lowerText.contains(skill)) {
                skills.add(skill);
            }
        }
        
        return skills;
    }
    
    private double calculateExperienceMatch(Double resumeExperience, String jobRequirements) {
        // Extract required experience from job
        Double requiredExperience = extractRequiredExperience(jobRequirements);
        
        if (requiredExperience == 0.0) {
            return 100.0; // No experience requirement
        }
        
        if (resumeExperience >= requiredExperience) {
            return 100.0;
        } else if (resumeExperience >= requiredExperience * 0.8) {
            return 80.0;
        } else if (resumeExperience >= requiredExperience * 0.6) {
            return 60.0;
        } else if (resumeExperience >= requiredExperience * 0.4) {
            return 40.0;
        } else {
            return 20.0;
        }
    }
    
    private Double extractRequiredExperience(String text) {
        // Look for patterns like "5+ years", "3-5 years", etc.
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "(\\d+)\\+?\\s*(?:-\\s*(\\d+))?\\s*years?", 
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher matcher = pattern.matcher(text);
        
        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        }
        
        return 0.0;
    }
    
    private double calculateEducationMatch(String resumeEducation, String jobRequirements) {
        String lowerRequirements = jobRequirements.toLowerCase();
        String lowerEducation = resumeEducation.toLowerCase();
        
        // Check for specific education requirements
        if (lowerRequirements.contains("phd") || lowerRequirements.contains("doctorate")) {
            return lowerEducation.contains("phd") ? 100.0 : 20.0;
        } else if (lowerRequirements.contains("master")) {
            if (lowerEducation.contains("phd")) return 100.0;
            if (lowerEducation.contains("master")) return 100.0;
            return 40.0;
        } else if (lowerRequirements.contains("bachelor")) {
            if (lowerEducation.contains("phd")) return 100.0;
            if (lowerEducation.contains("master")) return 100.0;
            if (lowerEducation.contains("bachelor")) return 100.0;
            return 60.0;
        }
        
        // No specific requirement mentioned
        return 80.0;
    }
    
    private double calculateKeywordMatch(String resumeText, String jobText) {
        // Extract important keywords from job
        Set<String> jobKeywords = extractKeywords(jobText);
        if (jobKeywords.isEmpty()) {
            return 50.0;
        }
        
        int matchCount = 0;
        for (String keyword : jobKeywords) {
            if (resumeText.contains(keyword)) {
                matchCount++;
            }
        }
        
        return (matchCount * 100.0) / jobKeywords.size();
    }
    
    private Set<String> extractKeywords(String text) {
        // Remove common words and extract important keywords
        Set<String> stopWords = new HashSet<>(Arrays.asList(
            "the", "is", "at", "which", "on", "and", "a", "an", "as", "are",
            "in", "for", "to", "of", "with", "from", "up", "about", "into",
            "through", "during", "before", "after", "above", "below", "between"
        ));
        
        String[] words = text.toLowerCase().split("\\s+");
        return Arrays.stream(words)
            .filter(word -> word.length() > 3 && !stopWords.contains(word))
            .limit(20) // Top 20 keywords
            .collect(Collectors.toSet());
    }
    
    private Map<String, Object> buildSkillAnalysis(List<String> resumeSkills, String jobRequirements, String jobDescription) {
        Map<String, Object> analysis = new HashMap<>();
        Set<String> requiredSkills = extractSkillsFromText(jobRequirements + " " + jobDescription);
        Set<String> matchedSkills = new HashSet<>();
        Set<String> missingSkills = new HashSet<>(requiredSkills);
        
        for (String skill : resumeSkills) {
            if (requiredSkills.contains(skill.toLowerCase())) {
                matchedSkills.add(skill);
                missingSkills.remove(skill.toLowerCase());
            }
        }
        
        analysis.put("matched", new ArrayList<>(matchedSkills));
        analysis.put("missing", new ArrayList<>(missingSkills));
        analysis.put("total", requiredSkills.size());
        
        return analysis;
    }
    
    private Map<String, Object> buildExperienceAnalysis(Double resumeExperience, String jobRequirements) {
        Map<String, Object> analysis = new HashMap<>();
        Double requiredExperience = extractRequiredExperience(jobRequirements);
        
        analysis.put("resumeYears", resumeExperience);
        analysis.put("requiredYears", requiredExperience);
        analysis.put("meetRequirement", resumeExperience >= requiredExperience);
        analysis.put("gap", Math.max(0, requiredExperience - resumeExperience));
        
        return analysis;
    }
    
    private Map<String, Object> buildEducationAnalysis(String resumeEducation, String jobRequirements) {
        Map<String, Object> analysis = new HashMap<>();
        
        analysis.put("resumeLevel", resumeEducation);
        analysis.put("meetsRequirement", calculateEducationMatch(resumeEducation, jobRequirements) >= 80);
        
        return analysis;
    }
    
    private Map<String, Object> buildKeywordAnalysis(String resumeText, String jobText) {
        Map<String, Object> analysis = new HashMap<>();
        Set<String> jobKeywords = extractKeywords(jobText);
        List<String> foundKeywords = new ArrayList<>();
        
        for (String keyword : jobKeywords) {
            if (resumeText.contains(keyword)) {
                foundKeywords.add(keyword);
            }
        }
        
        analysis.put("foundKeywords", foundKeywords);
        analysis.put("totalKeywords", jobKeywords.size());
        
        return analysis;
    }
    
    private List<String> generateRecommendations(double skillScore, double experienceScore, 
                                                double educationScore, double keywordScore,
                                                Map<String, Object> analysis) {
        List<String> recommendations = new ArrayList<>();
        
        if (skillScore < 70) {
            Map<String, Object> skillAnalysis = (Map<String, Object>) analysis.get("skillMatch");
            List<String> missingSkills = (List<String>) skillAnalysis.get("missing");
            if (!missingSkills.isEmpty()) {
                recommendations.add("Consider gaining experience in: " + String.join(", ", missingSkills.subList(0, Math.min(3, missingSkills.size()))));
            }
        }
        
        if (experienceScore < 70) {
            Map<String, Object> expAnalysis = (Map<String, Object>) analysis.get("experienceMatch");
            Double gap = (Double) expAnalysis.get("gap");
            if (gap > 0) {
                recommendations.add("Consider gaining " + gap.intValue() + " more years of relevant experience");
            }
        }
        
        if (educationScore < 70) {
            recommendations.add("Consider pursuing additional education or certifications relevant to this role");
        }
        
        if (keywordScore < 70) {
            recommendations.add("Tailor your resume to include more keywords from the job description");
        }
        
        if (recommendations.isEmpty() && skillScore + experienceScore + educationScore + keywordScore >= 320) {
            recommendations.add("Excellent match! Your profile aligns well with this position");
        }
        
        return recommendations;
    }
}