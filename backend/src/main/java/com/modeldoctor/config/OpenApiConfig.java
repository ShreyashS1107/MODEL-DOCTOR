package com.modeldoctor.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI modelDoctorOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Model Doctor // Diagnostic Lab Core API")
                        .description("REST API and telemetry contracts for the Model Doctor forensic diagnostics platform.")
                        .version("0.1.0-SNAPSHOT")
                        .contact(new Contact()
                                .name("Model Doctor Engineering")
                                .email("diagnostics@modeldoctor.ai"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
