package com.compliancevault.vaultapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Global CORS configuration.
 *
 * <p>Allows the Swagger UI (served from the same origin as the API) and an
 * optional external development frontend to call every vault-api endpoint.
 * Origins, methods, and headers are kept as narrow as possible; wildcard
 * origins are not used.
 *
 * <p>The primary allowed origin is configured via the
 * {@code app.cors.allowed-origins} property so it can be overridden per
 * environment without code changes.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * Comma-separated list of allowed origins, e.g.
     * {@code http://localhost:8080,http://localhost:3000}.
     * Defaults to {@code http://localhost:8080} (Swagger UI origin).
     */
    private final String[] allowedOrigins;

    public CorsConfig(
            @Value("${app.cors.allowed-origins:http://localhost:8080,http://localhost:3000}")
            String rawOrigins) {
        this.allowedOrigins = rawOrigins.split(",");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization", "Accept")
                .allowCredentials(false)
                .maxAge(3600);

        // Also expose the actuator health probes to the same origins so that
        // browser-based monitoring dashboards can reach them.
        registry.addMapping("/actuator/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders("Content-Type", "Accept")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
