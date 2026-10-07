package com.modeldoctor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Model Doctor Backend Core Service.
 * Central orchestrator for ML diagnostic pipelines, artifact registry, and forensic reporting.
 */
@SpringBootApplication
public class ModelDoctorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModelDoctorApplication.class, args);
    }
}
