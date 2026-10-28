package com.eexchange.backend.Configuration;

import com.eexchange.backend.Configuration.Filters.AuthRateLimitFilter;
import com.eexchange.backend.Configuration.Filters.JWTfilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterRegistrationConfig {

    @Bean
    public FilterRegistrationBean<JWTfilter> jwtFilterRegistration(JWTfilter filter) {
        FilterRegistrationBean<JWTfilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<AuthRateLimitFilter> authRateLimitFilterRegistration(
            AuthRateLimitFilter filter) {
        FilterRegistrationBean<AuthRateLimitFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}