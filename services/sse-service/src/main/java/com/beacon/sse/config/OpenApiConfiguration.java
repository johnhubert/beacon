package com.beacon.sse.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Defines OpenAPI metadata for the server-sent events service.
 */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Beacon Event Stream API",
        version = "v1",
        description = "Server-sent event stream broadcasting Beacon updates.",
        license = @License(name = "Apache-2.0")))
public class OpenApiConfiguration {

    @Bean
    public OpenAPI sseOpenApi() {
        return new OpenAPI()
                .info(new io.swagger.v3.oas.models.info.Info()
                        .title("Beacon Event Stream API")
                        .version("v1")
                        .description("Subscribe to Beacon server-sent events for live updates.")
                        .contact(new Contact()
                                .name("Beacon Platform Team")
                                .email("support@beacon.dev")));
    }
}
