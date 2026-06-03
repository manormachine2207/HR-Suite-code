package io.github.manormachine2207.hrsuite.action;

import io.github.manormachine2207.hrsuite.shared.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActionRefsControllerTest {

    private final TenantN8nConfigRepository repo = mock(TenantN8nConfigRepository.class);
    private final ActionRefsController controller = new ActionRefsController(repo);

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void returnsAllowedRefsForCurrentTenant() {
        UUID tenant = UUID.randomUUID();
        TenantContext.set(tenant);
        when(repo.findById(tenant)).thenReturn(Optional.of(
                new TenantN8nConfig(tenant, "http://n8n:5678", "secret",
                        List.of("provision-ad-account", "sync-payroll"))));

        assertThat(controller.refs()).containsExactly("provision-ad-account", "sync-payroll");
    }

    @Test
    void returnsEmptyListWhenNoConfigForTenant() {
        UUID tenant = UUID.randomUUID();
        TenantContext.set(tenant);
        when(repo.findById(tenant)).thenReturn(Optional.empty());

        assertThat(controller.refs()).isEmpty();
    }
}
