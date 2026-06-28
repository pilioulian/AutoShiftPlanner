package org.betaiotazeta.autoshiftplanner.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata for the generated spec (served at {@code /v3/api-docs}) and Swagger UI
 * ({@code /swagger-ui.html}).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI autoShiftPlannerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AutoShiftPlanner API")
                        .description("Start a shift-scheduling solve from a problem configuration and poll "
                                + "its best solution. Solving runs asynchronously on Timefold Solver.")
                        .version("v1")
                        .license(new License().name("Apache-2.0")));
    }
}
