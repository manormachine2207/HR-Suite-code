package io.github.manormachine2207.hrsuite.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/** Enables @PreAuthorize on controller/service methods (04-Authorization-Model: API pre-filter). */
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {
}
