package com.example.jobportal.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class FileStorageService {
    
    @Value("${app.upload.dir:${user.home}/jobportal/uploads}")
    private String uploadDir;
    
    @Value("${app.upload.temp-dir:${user.home}/jobportal/temp}")
    private String tempDir;
    
    public void init() {
        try {
            Files.createDirectories(Paths.get(uploadDir));
            Files.createDirectories(Paths.get(tempDir));
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directories", e);
        }
    }
    
    /**
     * Store file temporarily for session-based processing
     * @param file The uploaded file
     * @param sessionId The session ID
     * @return The temporary file path
     */
    public String storeTemporaryFile(MultipartFile file, String sessionId) throws IOException {
        String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        Path sessionPath = Paths.get(tempDir, sessionId);
        Files.createDirectories(sessionPath);
        
        Path targetLocation = sessionPath.resolve(fileName);
        Files.copy(file.getInputStream(), targetLocation);
        
        log.info("Stored temporary file: {} for session: {}", fileName, sessionId);
        return targetLocation.toString();
    }
    
    /**
     * Store file permanently (only if user consents)
     * @param file The uploaded file
     * @param userId The user ID
     * @return The permanent file path
     */
    public String storePermanentFile(MultipartFile file, Long userId) throws IOException {
        String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        Path userPath = Paths.get(uploadDir, "user_" + userId);
        Files.createDirectories(userPath);
        
        Path targetLocation = userPath.resolve(fileName);
        Files.copy(file.getInputStream(), targetLocation);
        
        log.info("Stored permanent file: {} for user: {}", fileName, userId);
        return targetLocation.toString();
    }
    
    /**
     * Get file as byte array
     * @param filePath The file path
     * @return File content as bytes
     */
    public byte[] getFileContent(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        return Files.readAllBytes(path);
    }
    
    /**
     * Delete temporary session files
     * @param sessionId The session ID
     */
    public void deleteSessionFiles(String sessionId) {
        try {
            Path sessionPath = Paths.get(tempDir, sessionId);
            if (Files.exists(sessionPath)) {
                FileUtils.deleteDirectory(sessionPath.toFile());
                log.info("Deleted session files for: {}", sessionId);
            }
        } catch (IOException e) {
            log.error("Error deleting session files for: " + sessionId, e);
        }
    }
    
    /**
     * Delete a specific file
     * @param filePath The file path
     */
    public void deleteFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            Files.deleteIfExists(path);
            log.info("Deleted file: {}", filePath);
        } catch (IOException e) {
            log.error("Error deleting file: " + filePath, e);
        }
    }
    
    /**
     * Clean up expired temporary files (older than specified hours)
     * @param hoursOld Files older than this will be deleted
     */
    public void cleanupExpiredFiles(int hoursOld) {
        try {
            File tempDirectory = new File(tempDir);
            if (tempDirectory.exists() && tempDirectory.isDirectory()) {
                long cutoffTime = System.currentTimeMillis() - (hoursOld * 60 * 60 * 1000);
                
                File[] sessionDirs = tempDirectory.listFiles();
                if (sessionDirs != null) {
                    for (File sessionDir : sessionDirs) {
                        if (sessionDir.isDirectory() && sessionDir.lastModified() < cutoffTime) {
                            FileUtils.deleteDirectory(sessionDir);
                            log.info("Deleted expired session directory: {}", sessionDir.getName());
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.error("Error during cleanup of expired files", e);
        }
    }
    
    /**
     * Check if file exists
     * @param filePath The file path
     * @return true if file exists
     */
    public boolean fileExists(String filePath) {
        return Files.exists(Paths.get(filePath));
    }
    
    /**
     * Get file size in bytes
     * @param filePath The file path
     * @return File size in bytes
     */
    public long getFileSize(String filePath) throws IOException {
        return Files.size(Paths.get(filePath));
    }
    
    /**
     * Validate file type
     * @param file The uploaded file
     * @return true if file type is allowed
     */
    public boolean isValidFileType(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && (
            contentType.equals("application/pdf") ||
            contentType.equals("application/msword") ||
            contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document") ||
            contentType.equals("text/plain") ||
            contentType.equals("application/rtf")
        );
    }
    
    /**
     * Get file extension
     * @param fileName The file name
     * @return File extension
     */
    public String getFileExtension(String fileName) {
        int lastIndexOf = fileName.lastIndexOf(".");
        if (lastIndexOf == -1) {
            return "";
        }
        return fileName.substring(lastIndexOf + 1);
    }
}