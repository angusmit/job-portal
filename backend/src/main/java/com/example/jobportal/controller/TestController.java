package com.example.jobportal.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.jobportal.dto.LoginRequest;
import com.example.jobportal.model.User;
import com.example.jobportal.repository.UserRepository;

@RestController
@RequestMapping("/api/test")
public class TestController {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @GetMapping("/ping")
    public String ping() {
        return "Auth system is running!";
    }
    
    @GetMapping("/users")
    public List<Map<String, String>> getAllUsers() {
        List<Map<String, String>> userList = new ArrayList<>();
        
        List<User> users = userRepository.findAll();
        for (User user : users) {
            Map<String, String> userMap = new HashMap<>();
            userMap.put("id", user.getId().toString());
            userMap.put("username", user.getUsername());
            userMap.put("email", user.getEmail());
            userMap.put("role", user.getRole().toString());
            userList.add(userMap);
        }
        
        return userList;
    }
    
    @PostMapping("/test-login")
    public ResponseEntity<?> testLogin(@RequestBody LoginRequest loginRequest) {
        System.out.println("=== TEST LOGIN ENDPOINT ===");
        System.out.println("Username: " + loginRequest.getUsername());
        System.out.println("Password: " + loginRequest.getPassword());
        
        // Check if user exists
        User user = userRepository.findByUsername(loginRequest.getUsername()).orElse(null);
        
        if (user == null) {
            System.out.println("User not found!");
            return ResponseEntity.badRequest().body("User not found");
        }
        
        System.out.println("User found: " + user.getUsername());
        System.out.println("Stored password hash: " + user.getPassword());
        
        // Test password matching
        boolean matches = passwordEncoder.matches(loginRequest.getPassword(), user.getPassword());
        System.out.println("Password matches: " + matches);
        
        if (matches) {
            return ResponseEntity.ok("Login successful!");
        } else {
            return ResponseEntity.badRequest().body("Invalid password");
        }
    }
}
