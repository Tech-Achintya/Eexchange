package com.eexchange.backend.configuration;

import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Entity.UserRole;
import com.eexchange.backend.Repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

@Configuration
public class DataInitializer {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Bean
    public CommandLineRunner initData(UserRepository userRepository) {
        return args -> {
            // Create Admin 1
            createAdminIfNotFound(userRepository, "admin@cuchd.in", "System Admin", "@control2602");
            
            // Create Admin 2
            createAdminIfNotFound(userRepository, "control@cuchd.in", "Control Admin", "@control2602");
        };
    }

    private void createAdminIfNotFound(UserRepository userRepository, String email, String name, String password) {
        if (userRepository.findByEmail(email).isEmpty()) {
            User admin = new User();
            admin.setName(name);
            admin.setEmail(email);
            admin.setPassword(passwordEncoder.encode(password));
            admin.setRole(UserRole.ROLE_ADMIN);
            admin.setBlocked(false);
            admin.setCreatedAt(LocalDateTime.now());
            userRepository.save(admin);
            System.out.println("Admin account created: " + email);
        } else {
            System.out.println("Admin account already exists: " + email);
        }
    }
}
