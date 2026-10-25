package com.eexchange.backend.configuration;

import com.eexchange.backend.Entity.Admin;
import com.eexchange.backend.Entity.Enums.AdminRole;
import com.eexchange.backend.Repository.AdminRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminBootstrapTest {

    private AdminRepository adminRepository;
    private PasswordEncoder passwordEncoder;
    private AdminBootstrap bootstrap;
    private ApplicationArguments args;

    @BeforeEach
    void setUp() {
        adminRepository = mock(AdminRepository.class);
        passwordEncoder = new BCryptPasswordEncoder(4);
        bootstrap = new AdminBootstrap(adminRepository, passwordEncoder);
        args = mock(ApplicationArguments.class);

        ReflectionTestUtils.setField(bootstrap, "username", "admin");
        ReflectionTestUtils.setField(bootstrap, "email", "Admin@Example.com");
        ReflectionTestUtils.setField(bootstrap, "password", "secret123");
    }

    @Test
    void createsSuperAdminWhenTableIsEmptyAndVarsAreSet() {
        when(adminRepository.count()).thenReturn(0L);
        when(adminRepository.save(any(Admin.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        bootstrap.run(args);

        ArgumentCaptor<Admin> captor = ArgumentCaptor.forClass(Admin.class);
        verify(adminRepository).save(captor.capture());

        Admin saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("admin");
        assertThat(saved.getEmail()).isEqualTo("admin@example.com");
        assertThat(saved.getRole()).isEqualTo(AdminRole.SUPER_ADMIN);
        assertThat(saved.getIsActive()).isTrue();
        assertThat(passwordEncoder.matches("secret123", saved.getPasswordHash())).isTrue();
    }

    @Test
    void skipsWhenAnAdminAlreadyExists() {
        when(adminRepository.count()).thenReturn(1L);

        bootstrap.run(args);

        verify(adminRepository, never()).save(any());
    }

    @Test
    void skipsWhenBootstrapVarsAreMissing() {
        when(adminRepository.count()).thenReturn(0L);
        ReflectionTestUtils.setField(bootstrap, "password", "");

        bootstrap.run(args);

        verify(adminRepository, never()).save(any());
    }
}
