package com.tapride.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Allows the frontend (running on a different port - a different "origin" as
 * far as the browser is concerned) to call this API. Without this, every
 * fetch() call from the browser fails with a generic "Failed to fetch" error,
 * even though the request never actually reaches this service at all - the
 * browser blocks it before sending, per CORS rules.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:8085") // the frontend's origin
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*");
    }
}