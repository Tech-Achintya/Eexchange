package com.eexchange.backend.configuration;

import com.eexchange.backend.configuration.Interceptors.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final CurrentUserArgumentResolver currentUserArgumentResolver;
    private final RateLimitInterceptor rateLimitInterceptor;

    public WebMvcConfig(CurrentUserArgumentResolver currentUserArgumentResolver,
                        RateLimitInterceptor rateLimitInterceptor) {
        this.currentUserArgumentResolver = currentUserArgumentResolver;
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor);
    }
}
