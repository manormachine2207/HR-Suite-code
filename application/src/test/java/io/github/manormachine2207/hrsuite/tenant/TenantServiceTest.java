package io.github.manormachine2207.hrsuite.tenant;

import io.github.manormachine2207.hrsuite.tenant.TenantExceptions.TenantConflictException;
import io.github.manormachine2207.hrsuite.tenant.TenantExceptions.TenantNotFoundException;
import io.github.manormachine2207.hrsuite.tenant.dto.CreateTenantRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    TenantRepository repository;

    @InjectMocks
    TenantService service;

    private CreateTenantRequest request() {
        return new CreateTenantRequest("BIT", Map.of("de", "Bundesamt für Informatik"),
                "bit", null, null);
    }

    @Test
    void createAssignsV7IdAndDefaults() {
        when(repository.existsByCode("BIT")).thenReturn(false);
        when(repository.existsBySubdomain("bit")).thenReturn(false);
        when(repository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        Tenant saved = service.create(request());

        ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        Tenant persisted = captor.getValue();
        assertThat(persisted.getId()).isNotNull();
        assertThat(persisted.getId().version()).isEqualTo(7);   // UUID v7
        assertThat(persisted.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(persisted.getDefaultLocale()).isEqualTo("de");
        assertThat(saved.getCode()).isEqualTo("BIT");
    }

    @Test
    void createRejectsDuplicateCode() {
        when(repository.existsByCode("BIT")).thenReturn(true);
        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(TenantConflictException.class)
                .hasMessageContaining("code");
    }

    @Test
    void findByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(TenantNotFoundException.class);
    }
}
