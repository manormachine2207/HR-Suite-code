package io.github.manormachine2207.hrsuite.shared.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Pushes the current tenant id onto the transactional DB connection as the
 * {@code app.tenant_id} session GUC, which the PostgreSQL RLS policies read
 * (ADR-008, ADR-009).
 *
 * <p>Runs around public methods of {@code @Service} beans. With
 * {@code @EnableTransactionManagement(order = 0)} the transaction advisor is the
 * outermost advice, so by the time this aspect ({@link Order} default, inner)
 * executes, the transaction — and thus the connection — is already bound. We use
 * {@code set_config(key, value, is_local := true)} so the GUC is scoped to the
 * current transaction and auto-resets on commit/rollback. That is the
 * "clear-after-use" reset ADR-008 requires to live in the aspect and explicitly
 * NOT in the Hikari connection-test-query (which the actuator health probe
 * reuses).
 *
 * <p>When no tenant is in context (e.g. the platform-admin tenant API) the
 * aspect is a no-op; only the non-RLS {@code tenant} system-root table is then
 * reachable.
 */
@Aspect
@Component
@Order(50)
public class TenantContextAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Around("@within(org.springframework.stereotype.Service) && execution(public * *(..))")
    public Object applyTenantGuc(ProceedingJoinPoint pjp) throws Throwable {
        UUID tenantId = TenantContext.get().orElse(null);
        if (tenantId != null) {
            entityManager
                    .createNativeQuery("SELECT set_config('app.tenant_id', :tid, true)")
                    .setParameter("tid", tenantId.toString())
                    .getSingleResult();
        }
        return pjp.proceed();
    }
}
