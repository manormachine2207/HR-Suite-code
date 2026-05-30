package io.github.manormachine2207.hrsuite.antrag;

import io.github.manormachine2207.hrsuite.antragstyp.AntragsTypService;
import io.github.manormachine2207.hrsuite.antragstyp.PublishedMajorRef;
import io.github.manormachine2207.hrsuite.shared.tenant.TenantContext;
import io.github.manormachine2207.hrsuite.workflow.WorkflowEngine;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AntragServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final String SUBJECT = "dev-applicant~00000000-0000-0000-0000-0000000000aa";
    private static final String OTHER_SUBJECT = "dev-applicant~00000000-0000-0000-0000-0000000000bb";

    @Mock
    private AntragRepository antragRepository;
    @Mock
    private AntragsTypService antragsTypService;
    @Mock
    private WorkflowEngine workflowEngine;

    private AntragService service;

    @BeforeEach
    void setUp() {
        service = new AntragService(antragRepository, antragsTypService, workflowEngine);
        TenantContext.set(TENANT);
        lenient().when(antragRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Antrag draft(UUID antragstypId, String subject) {
        return new Antrag(UUID.randomUUID(), TENANT, antragstypId, subject, Map.of("a", "1"));
    }

    // ---- createDraft ------------------------------------------------------
    @Test
    void createDraftCreatesUnpinnedDraftAgainstPublishedAntragstyp() {
        UUID atId = UUID.randomUUID();
        when(antragsTypService.findPublishedMajor(atId))
                .thenReturn(Optional.of(new PublishedMajorRef(UUID.randomUUID(), 0, "proc-key")));

        Antrag a = service.createDraft(atId, Map.of("grund", "x"), SUBJECT);

        assertThat(a.getStatus()).isEqualTo(AntragStatus.DRAFT);
        assertThat(a.getAntragstypId()).isEqualTo(atId);
        assertThat(a.getTenantId()).isEqualTo(TENANT);
        assertThat(a.getAntragstellerSubject()).isEqualTo(SUBJECT);
        assertThat(a.getAntragstypVersionId()).isNull();   // not pinned until submit (ADR-009 §1)
        assertThat(a.getSubmittedMinor()).isNull();
    }

    @Test
    void createDraftThrowsNotFoundWhenAntragstypNotPublished() {
        UUID atId = UUID.randomUUID();
        when(antragsTypService.findPublishedMajor(atId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createDraft(atId, Map.of(), SUBJECT))
                .isInstanceOf(AntragExceptions.NotFound.class);
    }

    // ---- submit (pin) -----------------------------------------------------
    @Test
    void submitPinsCurrentlyPublishedMajorAndMinor() {
        UUID atId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        Antrag a = draft(atId, SUBJECT);
        when(antragRepository.findById(a.getId())).thenReturn(Optional.of(a));
        when(antragsTypService.findPublishedMajor(atId)).thenReturn(Optional.of(new PublishedMajorRef(versionId, 2, "proc-key")));
        when(workflowEngine.startInstance(any(), eq("proc-key"), any(), any())).thenReturn("pi-1");

        service.submit(a.getId(), SUBJECT);

        assertThat(a.getStatus()).isEqualTo(AntragStatus.SUBMITTED);
        assertThat(a.getAntragstypVersionId()).isEqualTo(versionId);
        assertThat(a.getSubmittedMinor()).isEqualTo(2);
        assertThat(a.getSubmittedAt()).isNotNull();
        assertThat(a.getWorkflowProcessId()).isEqualTo("pi-1");   // process instance started (ADR-009 §5)
    }

    @Test
    void submitThrowsIllegalStateWhenNotDraft() {
        UUID atId = UUID.randomUUID();
        Antrag a = draft(atId, SUBJECT);
        a.submit(UUID.randomUUID(), 0); // now SUBMITTED
        when(antragRepository.findById(a.getId())).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.submit(a.getId(), SUBJECT))
                .isInstanceOf(AntragExceptions.IllegalState.class);
    }

    @Test
    void submitThrowsIllegalStateWhenNoPublishedMajor() {
        UUID atId = UUID.randomUUID();
        Antrag a = draft(atId, SUBJECT);
        when(antragRepository.findById(a.getId())).thenReturn(Optional.of(a));
        when(antragsTypService.findPublishedMajor(atId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(a.getId(), SUBJECT))
                .isInstanceOf(AntragExceptions.IllegalState.class);
    }

    @Test
    void submitOnForeignRequestThrowsNotFound() {
        UUID atId = UUID.randomUUID();
        Antrag a = draft(atId, OTHER_SUBJECT);   // owned by someone else
        when(antragRepository.findById(a.getId())).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.submit(a.getId(), SUBJECT))
                .isInstanceOf(AntragExceptions.NotFound.class);
    }

    // ---- cancel -----------------------------------------------------------
    @Test
    void cancelWithdrawsSubmittedRequest() {
        UUID atId = UUID.randomUUID();
        Antrag a = draft(atId, SUBJECT);
        a.submit(UUID.randomUUID(), 0);
        when(antragRepository.findById(a.getId())).thenReturn(Optional.of(a));

        service.cancel(a.getId(), SUBJECT);

        assertThat(a.getStatus()).isEqualTo(AntragStatus.CANCELLED);
    }

    @Test
    void cancelThrowsWhenAlreadyCancelled() {
        UUID atId = UUID.randomUUID();
        Antrag a = draft(atId, SUBJECT);
        a.cancel();
        when(antragRepository.findById(a.getId())).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.cancel(a.getId(), SUBJECT))
                .isInstanceOf(AntragExceptions.IllegalState.class);
    }

    @Test
    void editDraftReplacesPayloadWhenDraft() {
        UUID atId = UUID.randomUUID();
        Antrag a = draft(atId, SUBJECT);
        when(antragRepository.findById(a.getId())).thenReturn(Optional.of(a));

        Antrag edited = service.editDraft(a.getId(), Map.of("grund", "neu"), SUBJECT);

        assertThat(edited.getPayload()).containsEntry("grund", "neu");
    }
}
