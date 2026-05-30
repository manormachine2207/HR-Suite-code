package io.github.manormachine2207.hrsuite.antragstyp;

import io.github.manormachine2207.hrsuite.antragstyp.form.FieldType;
import io.github.manormachine2207.hrsuite.antragstyp.form.FormDefinition;
import io.github.manormachine2207.hrsuite.antragstyp.form.FormField;
import io.github.manormachine2207.hrsuite.antragstyp.form.Validation;
import io.github.manormachine2207.hrsuite.antragstyp.version.CompatibilityClassifier;
import io.github.manormachine2207.hrsuite.shared.tenant.TenantContext;
import io.github.manormachine2207.hrsuite.workflow.DeployedProcess;
import io.github.manormachine2207.hrsuite.workflow.WorkflowEngine;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AntragsTypServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    @Mock
    private AntragsTypRepository antragsTypRepository;
    @Mock
    private AntragsTypVersionRepository versionRepository;
    @Mock
    private EntityManager entityManager;
    @Mock
    private Query advisoryLockQuery;
    @Mock
    private WorkflowEngine workflowEngine;

    private AntragsTypService service;

    @BeforeEach
    void setUp() {
        service = new AntragsTypService(antragsTypRepository, versionRepository, new CompatibilityClassifier(), workflowEngine);
        lenient().when(workflowEngine.deploy(any(), anyString(), any(), any()))
                .thenReturn(new DeployedProcess("dep-1", "proc-key", 1));
        // @PersistenceContext field injection has no Spring container here; wire the
        // EntityManager publish() uses for its pg_advisory_xact_lock query by hand.
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        lenient().when(entityManager.createNativeQuery(anyString())).thenReturn(advisoryLockQuery);
        lenient().when(advisoryLockQuery.setParameter(anyString(), any())).thenReturn(advisoryLockQuery);
        lenient().when(advisoryLockQuery.getSingleResult()).thenReturn(1);
        TenantContext.set(TENANT);
        lenient().when(antragsTypRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(versionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ---- helpers ----------------------------------------------------------
    private static FormDefinition form(FormField... f) {
        return new FormDefinition(List.of(f));
    }

    private static FormField text(String key, boolean required, Integer maxLen) {
        return new FormField(key, FieldType.TEXT, required, Map.of("de", "L"), Map.of(),
                maxLen == null ? null : new Validation(maxLen, null, null), List.of(), null);
    }

    private AntragsTypVersion publishedVersion(UUID antragstypId, FormDefinition def) {
        var v = new AntragsTypVersion(UUID.randomUUID(), TENANT, antragstypId, 1, def, "<bpmn/>", Map.of());
        v.setStatus(VersionStatus.PUBLISHED);
        return v;
    }

    // ---- createDefinition -------------------------------------------------
    @Test
    void createDefinitionSavesDraftWhenKeyFree() {
        when(antragsTypRepository.existsByKey("sonderurlaub")).thenReturn(false);

        AntragsTyp at = service.createDefinition("sonderurlaub", Map.of("de", "Sonderurlaub"), Map.of());

        assertThat(at.getKey()).isEqualTo("sonderurlaub");
        assertThat(at.getTenantId()).isEqualTo(TENANT);
        assertThat(at.getStatus()).isEqualTo(AntragsTypStatus.DRAFT);
    }

    @Test
    void createDefinitionThrowsConflictWhenKeyExists() {
        when(antragsTypRepository.existsByKey("dup")).thenReturn(true);

        assertThatThrownBy(() -> service.createDefinition("dup", Map.of("de", "x"), Map.of()))
                .isInstanceOf(AntragsTypExceptions.Conflict.class);
    }

    // ---- createDraftMajor -------------------------------------------------
    @Test
    void createDraftMajorAssignsNextMajor() {
        UUID atId = UUID.randomUUID();
        when(antragsTypRepository.findById(atId))
                .thenReturn(Optional.of(new AntragsTyp(atId, TENANT, "k", Map.of("de", "K"), Map.of())));
        when(versionRepository.maxMajor(atId)).thenReturn(2);

        AntragsTypVersion v = service.createDraftMajor(atId, form(text("a", true, 100)), "<bpmn/>", Map.of());

        assertThat(v.getMajor()).isEqualTo(3);
        assertThat(v.getMinor()).isZero();
        assertThat(v.getStatus()).isEqualTo(VersionStatus.DRAFT);
        assertThat(v.getTenantId()).isEqualTo(TENANT);
    }

    @Test
    void createDraftMajorThrowsNotFoundWhenAntragstypMissing() {
        UUID missing = UUID.randomUUID();
        when(antragsTypRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createDraftMajor(missing, form(text("a", true, 100)), "<bpmn/>", Map.of()))
                .isInstanceOf(AntragsTypExceptions.NotFound.class);
    }

    // ---- publish ----------------------------------------------------------
    @Test
    void publishFirstVersionMarksAntragsTypLive() {
        UUID atId = UUID.randomUUID();
        var at = new AntragsTyp(atId, TENANT, "k", Map.of("de", "K"), Map.of());
        var v = new AntragsTypVersion(UUID.randomUUID(), TENANT, atId, 1, form(text("a", true, 100)), "<bpmn/>", Map.of());
        when(versionRepository.findAntragstypIdById(v.getId())).thenReturn(Optional.of(atId));
        when(versionRepository.findById(v.getId())).thenReturn(Optional.of(v));
        when(versionRepository.findByAntragstypIdAndStatus(atId, VersionStatus.PUBLISHED)).thenReturn(Optional.empty());
        when(antragsTypRepository.findById(atId)).thenReturn(Optional.of(at));

        service.publish(v.getId(), USER);

        assertThat(v.getStatus()).isEqualTo(VersionStatus.PUBLISHED);
        assertThat(v.getPublishedBy()).isEqualTo(USER);
        assertThat(at.getStatus()).isEqualTo(AntragsTypStatus.LIVE);
        assertThat(at.getCurrentVersionId()).isEqualTo(v.getId());
    }

    @Test
    void publishDemotesPreviousPublishedMajor() {
        UUID atId = UUID.randomUUID();
        var at = new AntragsTyp(atId, TENANT, "k", Map.of("de", "K"), Map.of());
        var prev = publishedVersion(atId, form(text("a", true, 100)));
        var next = new AntragsTypVersion(UUID.randomUUID(), TENANT, atId, 2, form(text("a", true, 100)), "<bpmn/>", Map.of());
        when(versionRepository.findAntragstypIdById(next.getId())).thenReturn(Optional.of(atId));
        when(versionRepository.findById(next.getId())).thenReturn(Optional.of(next));
        when(versionRepository.findByAntragstypIdAndStatus(atId, VersionStatus.PUBLISHED)).thenReturn(Optional.of(prev));
        when(antragsTypRepository.findById(atId)).thenReturn(Optional.of(at));

        service.publish(next.getId(), USER);

        assertThat(prev.getStatus()).isEqualTo(VersionStatus.DEPRECATED);
        assertThat(next.getStatus()).isEqualTo(VersionStatus.PUBLISHED);
    }

    @Test
    void publishThrowsIllegalStateWhenNotDraft() {
        var v = publishedVersion(UUID.randomUUID(), form(text("a", true, 100)));
        when(versionRepository.findAntragstypIdById(v.getId())).thenReturn(Optional.of(v.getAntragstypId()));
        when(versionRepository.findById(v.getId())).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> service.publish(v.getId(), USER))
                .isInstanceOf(AntragsTypExceptions.IllegalState.class);
    }

    // ---- editInPlaceMinor -------------------------------------------------
    @Test
    void editInPlaceMinorAppliesWhenBackwardCompatible() {
        var v = publishedVersion(UUID.randomUUID(),
                form(new FormField("a", FieldType.TEXT, true, Map.of("de", "Alt"), Map.of(), null, List.of(), null)));
        when(versionRepository.findById(v.getId())).thenReturn(Optional.of(v));

        var edited = service.editInPlaceMinor(v.getId(),
                form(new FormField("a", FieldType.TEXT, true, Map.of("de", "Neu"), Map.of(), null, List.of(), null)));

        assertThat(edited.getMinor()).isEqualTo(1);
        assertThat(edited.getStatus()).isEqualTo(VersionStatus.PUBLISHED);
    }

    @Test
    void editInPlaceMinorThrowsBreakingChangeWhenMajor() {
        var v = publishedVersion(UUID.randomUUID(), form(text("a", true, 100), text("b", true, 100)));
        when(versionRepository.findById(v.getId())).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> service.editInPlaceMinor(v.getId(), form(text("a", true, 100)))) // removed b
                .isInstanceOf(AntragsTypExceptions.BreakingChange.class);
        assertThat(v.getMinor()).isZero(); // unchanged
    }

    @Test
    void editInPlaceMinorThrowsIllegalStateWhenDraft() {
        var v = new AntragsTypVersion(UUID.randomUUID(), TENANT, UUID.randomUUID(), 1, form(text("a", true, 100)), "<bpmn/>", Map.of());
        when(versionRepository.findById(v.getId())).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> service.editInPlaceMinor(v.getId(), form(text("a", true, 200))))
                .isInstanceOf(AntragsTypExceptions.IllegalState.class);
    }

    @Test
    void editInPlaceMinorAppliesOnDeprecatedVersion() {
        var v = publishedVersion(UUID.randomUUID(),
                form(new FormField("a", FieldType.TEXT, true, Map.of("de", "Alt"), Map.of(), null, List.of(), null)));
        v.setStatus(VersionStatus.DEPRECATED);
        when(versionRepository.findById(v.getId())).thenReturn(Optional.of(v));

        var edited = service.editInPlaceMinor(v.getId(),
                form(new FormField("a", FieldType.TEXT, true, Map.of("de", "Neu"), Map.of(), null, List.of(), null)));

        assertThat(edited.getMinor()).isEqualTo(1);
        assertThat(edited.getStatus()).isEqualTo(VersionStatus.DEPRECATED);
    }

    // ---- editDraft --------------------------------------------------------
    @Test
    void editDraftReplacesContentWhenDraft() {
        var v = new AntragsTypVersion(UUID.randomUUID(), TENANT, UUID.randomUUID(), 1, form(text("a", true, 100)), "<bpmn/>", Map.of());
        when(versionRepository.findById(v.getId())).thenReturn(Optional.of(v));

        var edited = service.editDraft(v.getId(), form(text("a", true, 100), text("b", false, 50)), "<bpmn>v2</bpmn>", Map.of());

        assertThat(edited.getFormDefinition().fields()).hasSize(2);
        assertThat(edited.getWorkflowBpmn()).isEqualTo("<bpmn>v2</bpmn>");
        assertThat(edited.getMinor()).isZero();
        assertThat(edited.getStatus()).isEqualTo(VersionStatus.DRAFT);
    }

    // ---- deprecate / archive ---------------------------------------------
    @Test
    void deprecateThenArchiveFollowsLifecycle() {
        var v = publishedVersion(UUID.randomUUID(), form(text("a", true, 100)));
        when(versionRepository.findById(v.getId())).thenReturn(Optional.of(v));

        service.deprecate(v.getId());
        assertThat(v.getStatus()).isEqualTo(VersionStatus.DEPRECATED);

        service.archive(v.getId());
        assertThat(v.getStatus()).isEqualTo(VersionStatus.ARCHIVED);
    }

    @Test
    void archiveThrowsIllegalStateWhenNotDeprecated() {
        var v = publishedVersion(UUID.randomUUID(), form(text("a", true, 100)));
        when(versionRepository.findById(v.getId())).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> service.archive(v.getId()))
                .isInstanceOf(AntragsTypExceptions.IllegalState.class);
    }

    // ---- not found --------------------------------------------------------
    @Test
    void getDefinitionThrowsNotFound() {
        UUID missing = UUID.randomUUID();
        when(antragsTypRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDefinition(missing))
                .isInstanceOf(AntragsTypExceptions.NotFound.class);
    }
}
