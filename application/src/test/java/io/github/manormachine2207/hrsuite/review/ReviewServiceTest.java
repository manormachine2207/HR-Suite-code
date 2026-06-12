package io.github.manormachine2207.hrsuite.review;

import io.github.manormachine2207.hrsuite.antrag.Antrag;
import io.github.manormachine2207.hrsuite.antrag.AntragService;
import io.github.manormachine2207.hrsuite.antrag.AntragStatus;
import io.github.manormachine2207.hrsuite.antragstyp.AntragsTyp;
import io.github.manormachine2207.hrsuite.antragstyp.AntragsTypService;
import io.github.manormachine2207.hrsuite.shared.tenant.TenantContext;
import io.github.manormachine2207.hrsuite.workflow.WorkflowEngine;
import io.github.manormachine2207.hrsuite.workflow.WorkflowTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Mock
    private WorkflowEngine workflowEngine;
    @Mock
    private AntragService antragService;
    @Mock
    private AntragsTypService antragsTypService;

    private ReviewService service;

    private Antrag antrag;
    private UUID versionId;
    private WorkflowTask task;

    @BeforeEach
    void setUp() {
        service = new ReviewService(workflowEngine, antragService, antragsTypService);
        TenantContext.set(TENANT);

        UUID antragstypId = UUID.randomUUID();
        versionId = UUID.randomUUID();
        antrag = new Antrag(UUID.randomUUID(), TENANT, antragstypId, "dev-applicant~x", Map.of("grund", "x"));
        antrag.submit(versionId, 0);
        task = new WorkflowTask("t-1", "Freigabe", "freigabe", "pi-1",
                antrag.getId().toString(), Instant.now());

        lenient().when(workflowEngine.findOpenTask(TENANT, "t-1")).thenReturn(Optional.of(task));
        lenient().when(antragService.getForReview(antrag.getId())).thenReturn(antrag);
        lenient().when(antragsTypService.findDeclaredOutcomes(versionId, "freigabe"))
                .thenReturn(List.of("approve", "reject"));
        lenient().when(antragsTypService.getDefinition(antragstypId))
                .thenReturn(new AntragsTyp(antragstypId, TENANT, "k",
                        Map.of("de", "K", "fr", "K", "it", "K", "en", "K"), Map.of()));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void completeWithApproveEndsProcessAndApprovesAntrag() {
        when(workflowEngine.isInstanceRunning(TENANT, "pi-1")).thenReturn(false);

        service.complete("t-1", "approve", null);

        verify(workflowEngine).completeTask("t-1", Map.of("freigabe_outcome", "approve"));
        assertThat(antrag.getStatus()).isEqualTo(AntragStatus.APPROVED);
    }

    @Test
    void completeWithRejectRejectsAntrag() {
        when(workflowEngine.isInstanceRunning(TENANT, "pi-1")).thenReturn(false);

        service.complete("t-1", "reject", "zu kurzfristig");

        assertThat(antrag.getStatus()).isEqualTo(AntragStatus.REJECTED);
    }

    @Test
    void completeWhileProcessKeepsRunningMovesAntragToInReview() {
        when(workflowEngine.isInstanceRunning(TENANT, "pi-1")).thenReturn(true);

        service.complete("t-1", "approve", null);

        assertThat(antrag.getStatus()).isEqualTo(AntragStatus.IN_REVIEW);
    }

    /** Prozessende ohne Outcome (FORM-/Platzhalter-Task) → COMPLETED, nicht fingiertes APPROVED (ADR-013). */
    @Test
    void completeWithoutOutcomeEndsAsCompleted() {
        // ohne Outcome wird die Whitelist bewusst nicht abgefragt
        when(workflowEngine.isInstanceRunning(TENANT, "pi-1")).thenReturn(false);

        service.complete("t-1", null, null);

        verify(workflowEngine).completeTask("t-1", Map.of());
        assertThat(antrag.getStatus()).isEqualTo(AntragStatus.COMPLETED);
    }

    @Test
    void outcomeOutsideDeclaredWhitelistIs422() {
        assertThatThrownBy(() -> service.complete("t-1", "escalate", null))
                .isInstanceOf(ReviewExceptions.Invalid.class)
                .hasMessageContaining("escalate");
        verify(workflowEngine, never()).completeTask(anyString(), any());
        assertThat(antrag.getStatus()).isEqualTo(AntragStatus.SUBMITTED);
    }

    /** Outcome wird Prozessvariable — gleiche Injection-Disziplin wie Compiler-Keys. */
    @Test
    void outcomeViolatingPatternIs422() {
        assertThatThrownBy(() -> service.complete("t-1", "x' || hack", null))
                .isInstanceOf(ReviewExceptions.Invalid.class);
        verify(workflowEngine, never()).completeTask(anyString(), any());
    }

    @Test
    void unknownTaskIs404() {
        when(workflowEngine.findOpenTask(TENANT, "missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.complete("missing", "approve", null))
                .isInstanceOf(ReviewExceptions.NotFound.class);
    }

    @Test
    void listJoinsAntragContextAndDeclaredOutcomes() {
        when(workflowEngine.openTasks(eq(TENANT), any())).thenReturn(List.of(task));

        var tasks = service.listOpenTasks(Set.of("hr-reviewer"));

        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).antragId()).isEqualTo(antrag.getId());
        assertThat(tasks.get(0).allowedOutcomes()).containsExactly("approve", "reject");
        assertThat(tasks.get(0).payload()).isNull();   // list view carries no payload
    }

    /** Tasks ohne Business-Key (Alt-Instanzen/Fremdprozesse) bleiben sichtbar — ohne Antrag-Kontext. */
    @Test
    void taskWithoutBusinessKeyStaysListable() {
        var orphan = new WorkflowTask("t-2", "Review", "review", "pi-2", null, Instant.now());
        when(workflowEngine.openTasks(eq(TENANT), any())).thenReturn(List.of(orphan));

        var tasks = service.listOpenTasks(Set.of("hr-reviewer"));

        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).antragId()).isNull();
    }
}
