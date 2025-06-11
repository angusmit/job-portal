package com.example.jobportal.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.jobportal.model.User;
import com.example.jobportal.repository.UserRepository;

@RestController
@RequestMapping("/api/test")
@CrossOrigin(origins = "*")
public class TestController {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @GetMapping("/users")
    public List<String> getUsers() {
        List<User> users = userRepository.findAll();
        return users.stream()
            .map(user -> user.getUsername() + " - " + user.getEmail() + " - " + user.getRole())
            .toList();
    }
    
    @GetMapping("/check-password/{username}")
    public boolean checkPassword(@PathVariable String username, @RequestParam String password) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return false;
        }
        return passwordEncoder.matches(password, user.getPassword());
    }
    
    @PostMapping("/test-login")
    public Map<String, Object> testLogin(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");
        
        System.out.println("Test login - Username: " + username);
        
        User user = userRepository.findByUsername(username).orElse(null);
        
        Map<String, Object> response = new HashMap<>();
        response.put("userFound", user != null);
        if (user != null) {
            response.put("passwordMatch", passwordEncoder.matches(password, user.getPassword()));
            response.put("userRole", user.getRole());
            response.put("userId", user.getId());
        }
        
        return response;
    }
    
    @GetMapping("/ping")
    public String ping() {
        return "Auth system is running!";
    }
}