package com.beacon.authservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the OpenAPI specification metadata for the authentication service.
 */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Beacon Auth Service API",
        version = "v1",
        description = "Token issuance endpoints for Beacon clients.",
        license = @License(name = "Apache-2.0")))
public class OpenApiConfiguration {

    @Bean
    public OpenAPI authOpenApi() {
        return new OpenAPI()
                .info(new io.swagger.v3.oas.models.info.Info()
                        .title("Beacon Auth Service API")
                        .version("v1")
                        .description("Authenticate users and obtain Beacon JWT access tokens.")
                        .contact(new Contact()
                                .name("Beacon Platform Team")
                                .email("support@beacon.dev")));
    }
}
