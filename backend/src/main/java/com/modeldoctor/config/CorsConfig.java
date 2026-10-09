package com.modeldoctor.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${modeldoctor.cors.allowed-origins:http://localhost:3000,http://127.0.0.1:3000}")
    private List<String> allowedOrigins;

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            @SuppressWarnings("null")
            public void addCorsMappings(@NonNull CorsRegistry registry) {
                String[] origins = allowedOrigins != null && !allowedOrigins.isEmpty()
                        ? allowedOrigins.toArray(new String[0])
                        : new String[]{"http://localhost:3000", "http://127.0.0.1:3000"};
                registry.addMapping("/**")
                        .allowedOriginPatterns(origins)
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                        .allowedHeaders("*")
                        .allowCredentials(true)
                        .maxAge(3600);
            }
        };
    }
}

