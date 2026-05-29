package io.github.manormachine2207.hrsuite.shared.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
}
