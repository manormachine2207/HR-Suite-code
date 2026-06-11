package io.github.manormachine2207.hrsuite.action;

import io.github.manormachine2207.hrsuite.shared.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActionExecutionServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Mock
    private ActionConnector connector;
    @Mock
    private ActionExecutionRepository repo;
    @Mock
    private DeadLetterWriter deadLetterWriter;

    private ActionExecutionService service;

    @BeforeEach
    void setUp() {
        service = new ActionExecutionService(connector, repo, deadLetterWriter, 3);
        TenantContext.set(TENANT);
        lenient().when(repo.findByBusinessKeyAndStepKey(any(), any())).thenReturn(Optional.empty());
        lenient().when(repo.findByProcessInstanceIdAndStepKey(any(), any())).thenReturn(Optional.empty());
        lenient().when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        // Terminal states (FAILED/DEAD) are persisted out-of-band via the writer.
        lenient().when(deadLetterWriter.persistTerminal(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void successMarksSucceededAndCallsConnectorOnce() {
        when(connector.execute(any())).thenReturn(ActionResult.ok(200, Map.of()));

        ActionExecution e = service.run("pi-1", "antrag-1", "ad", "provision-ad-account", Map.of());

        assertThat(e.getStatus()).isEqualTo(ActionStatus.SUCCEEDED);
        verify(connector, times(1)).execute(any());
    }

    @Test
    void transientFailureRetriesUpToMaxThenDead() {
        when(connector.execute(any())).thenReturn(ActionResult.transientFailure(500, "boom"));

        ActionExecution e = service.run("pi-1", "antrag-1", "ad", "provision-ad-account", Map.of());

        assertThat(e.getStatus()).isEqualTo(ActionStatus.DEAD);
        assertThat(e.getAttempts()).isEqualTo(3);
        verify(connector, times(3)).execute(any());
    }

    @Test
    void terminalFailureDoesNotRetry() {
        when(connector.execute(any())).thenReturn(ActionResult.terminal(400, "bad"));

        ActionExecution e = service.run("pi-1", "antrag-1", "ad", "provision-ad-account", Map.of());

        assertThat(e.getStatus()).isEqualTo(ActionStatus.FAILED);
        verify(connector, times(1)).execute(any());
    }

    @Test
    void alreadySucceededIsIdempotentAndSkipsConnector() {
        ActionExecution existing = new ActionExecution(TENANT, "pi-1", "antrag-1", "ad", "provision-ad-account");
        existing.markSucceeded();
        when(repo.findByBusinessKeyAndStepKey("antrag-1", "ad")).thenReturn(Optional.of(existing));

        ActionExecution e = service.run("pi-1", "antrag-1", "ad", "provision-ad-account", Map.of());

        assertThat(e.getStatus()).isEqualTo(ActionStatus.SUCCEEDED);
        verify(connector, times(0)).execute(any());
    }

    /**
     * Resubmit after a rollback starts a NEW process instance — the business key
     * (= antrag id) is the stable dedupe anchor across instances (Review 2026-06-12):
     * a SUCCEEDED action for the same antrag+step must not re-fire its side effect.
     */
    @Test
    void successShortCircuitsAcrossProcessInstancesViaBusinessKey() {
        ActionExecution existing = new ActionExecution(TENANT, "pi-OLD", "antrag-1", "ad", "provision-ad-account");
        existing.markSucceeded();
        when(repo.findByBusinessKeyAndStepKey("antrag-1", "ad")).thenReturn(Optional.of(existing));

        ActionExecution e = service.run("pi-NEW", "antrag-1", "ad", "provision-ad-account", Map.of());

        assertThat(e.getStatus()).isEqualTo(ActionStatus.SUCCEEDED);
        verify(connector, times(0)).execute(any());
    }

    /** A dead-lettered action must stay dead on BPMN re-entry — no self-reviving DLQ rows. */
    @Test
    void deadRowDoesNotRefireOnReentry() {
        ActionExecution existing = new ActionExecution(TENANT, "pi-1", "antrag-1", "ad", "provision-ad-account");
        existing.markDead("gave up");
        when(repo.findByBusinessKeyAndStepKey("antrag-1", "ad")).thenReturn(Optional.of(existing));

        ActionExecution e = service.run("pi-1", "antrag-1", "ad", "provision-ad-account", Map.of());

        assertThat(e.getStatus()).isEqualTo(ActionStatus.DEAD);
        verify(connector, times(0)).execute(any());
    }

    @Test
    void failedRowDoesNotRefireOnReentry() {
        ActionExecution existing = new ActionExecution(TENANT, "pi-1", "antrag-1", "ad", "provision-ad-account");
        existing.markFailed("4xx");
        when(repo.findByBusinessKeyAndStepKey("antrag-1", "ad")).thenReturn(Optional.of(existing));

        ActionExecution e = service.run("pi-1", "antrag-1", "ad", "provision-ad-account", Map.of());

        assertThat(e.getStatus()).isEqualTo(ActionStatus.FAILED);
        verify(connector, times(0)).execute(any());
    }

    /** Instances without a business key (non-antrag processes) fall back to the old lookup. */
    @Test
    void fallsBackToProcessInstanceLookupWithoutBusinessKey() {
        when(connector.execute(any())).thenReturn(ActionResult.ok(200, Map.of()));

        ActionExecution e = service.run("pi-1", null, "ad", "provision-ad-account", Map.of());

        assertThat(e.getStatus()).isEqualTo(ActionStatus.SUCCEEDED);
        verify(repo).findByProcessInstanceIdAndStepKey("pi-1", "ad");
        verify(connector, times(1)).execute(any());
    }
}
