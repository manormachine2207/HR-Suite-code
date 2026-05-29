package io.github.manormachine2207.hrsuite.shared.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void emptyByDefault() {
        assertThat(TenantContext.get()).isEmpty();
    }

    @Test
    void holdsAndClearsTenantId() {
        UUID id = UUID.randomUUID();
        TenantContext.set(id);
        assertThat(TenantContext.get()).contains(id);

        TenantContext.clear();
        assertThat(TenantContext.get()).isEmpty();
    }

    @Test
    void requireReturnsTheCurrentTenant() {
        UUID id = UUID.randomUUID();
        TenantContext.set(id);
        assertThat(TenantContext.require()).isEqualTo(id);
    }

    @Test
    void requireThrowsMissingTenantContextWhenUnset() {
        // No TenantContext.set(...) — e.g. a tenant-scoped role with no tenant_id claim.
        assertThatThrownBy(TenantContext::require)
                .isInstanceOf(MissingTenantContextException.class)
                .hasMessage("no tenant in context");
    }
}
