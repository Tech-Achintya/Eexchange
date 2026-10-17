package com.eexchange.backend.Service;

import com.eexchange.backend.Entity.Enums.UserStatus;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        boolean isAccountNonLocked = true;
        boolean isEnabled = true;

        if (user.getStatus() == UserStatus.PERM_BLOCKED) {
            isAccountNonLocked = false;
            isEnabled = false;
        } else if (user.getStatus() == UserStatus.TEMP_BLOCKED) {
            if (user.getBlockedUntil() != null && user.getBlockedUntil().isAfter(Instant.now())) {
                isAccountNonLocked = false;
            }
        }

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")))
                .accountLocked(!isAccountNonLocked)
                .disabled(!isEnabled)
                .build();
    }
}
