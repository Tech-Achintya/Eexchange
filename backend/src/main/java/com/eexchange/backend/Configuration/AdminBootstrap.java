package com.eexchange.backend.Configuration;

import com.eexchange.backend.Entity.Admin;
import com.eexchange.backend.Entity.Enums.AdminRole;
import com.eexchange.backend.Repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.bootstrap.username:}")
    private String username;

    @Value("${app.admin.bootstrap.email:}")
    private String email;

    @Value("${app.admin.bootstrap.password:}")
    private String password;

    @Override
    public void run(ApplicationArguments args) {
        if (adminRepository.count() > 0) {
            return;
        }

        if (isBlank(username) || isBlank(email) || isBlank(password)) {
            log.warn("No admin exists and APP_ADMIN_USERNAME/APP_ADMIN_EMAIL/APP_ADMIN_PASSWORD "
                    + "are not all set. Skipping admin bootstrap.");
            return;
        }

        Admin admin = Admin.builder()
                .username(username.trim())
                .email(email.trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(password))
                .role(AdminRole.SUPER_ADMIN)
                .isActive(true)
                .build();

        adminRepository.save(admin);
        log.info("Bootstrapped initial SUPER_ADMIN account '{}'.", admin.getUsername());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}