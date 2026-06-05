package io.github.manormachine2207.hrsuite.action;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActionRefsControllerTest {

    private final ActionRefsService service = mock(ActionRefsService.class);
    private final ActionRefsController controller = new ActionRefsController(service);

    @Test
    void returnsAllowedRefsForCurrentTenant() {
        when(service.getAllowedRefs()).thenReturn(List.of("provision-ad-account", "sync-payroll"));

        assertThat(controller.refs()).containsExactly("provision-ad-account", "sync-payroll");
    }

    @Test
    void returnsEmptyListWhenNoConfigForTenant() {
        when(service.getAllowedRefs()).thenReturn(List.of());

        assertThat(controller.refs()).isEmpty();
    }
}
