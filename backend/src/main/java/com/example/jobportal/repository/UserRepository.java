package com.example.jobportal.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.jobportal.model.User;
import com.example.jobportal.model.UserRole;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    
    Optional<User> findByEmail(String email);
    
    Boolean existsByUsername(String username);
    
    Boolean existsByEmail(String email);
    
    List<User> findByRole(UserRole role);
    
    List<User> findByActive(boolean active);
    
    List<User> findByRoleAndActive(UserRole role, boolean active);
    
    Long countByRole(UserRole role);
    
    Long countByActive(boolean active);
}