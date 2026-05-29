package io.github.manormachine2207.hrsuite.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Pins the Spring transaction advisor to the OUTERMOST position (order 0) so the
 * TenantContextAspect (a higher order value = inner) runs INSIDE an already-open
 * transaction. That guarantees {@code set_config('app.tenant_id', …, is_local=true)}
 * targets the same connection the JPA queries use, which is required for
 * PostgreSQL RLS to filter correctly (ADR-008).
 */
@Configuration
@EnableTransactionManagement(order = 0)
public class TransactionConfig {
}
