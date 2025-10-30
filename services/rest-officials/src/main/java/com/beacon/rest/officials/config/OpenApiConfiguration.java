package com.beacon.rest.officials.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the generated OpenAPI contract for the officials REST API.
 */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Beacon Officials API",
        version = "v1",
        description = "Public official roster endpoints used by the Beacon applications.",
        license = @License(name = "Apache-2.0")))
public class OpenApiConfiguration {

    @Bean
    public OpenAPI officialsOpenApi() {
        return new OpenAPI()
                .info(new io.swagger.v3.oas.models.info.Info()
                        .title("Beacon Officials API")
                        .version("v1")
                        .description("Explore roster and profile endpoints for Beacon officials.")
                        .contact(new Contact()
                                .name("Beacon Platform Team")
                                .email("support@beacon.dev")));
    }
}
