package io.github.manormachine2207.hrsuite.antragstyp;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.manormachine2207.hrsuite.antragstyp.form.FormDefinition;
import io.github.manormachine2207.hrsuite.antragstyp.version.ChangeClassification;
import io.github.manormachine2207.hrsuite.antragstyp.version.CompatibilityClassifier;
import io.github.manormachine2207.hrsuite.shared.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Anwendungsdienst des Antragstyp-Moduls und Hueter der ADR-009-Invarianten:
 * genau ein {@code published} Major je Antragstyp, Minor-Edits nur wenn
 * rueckwaerts-kompatibel (sonst {@link AntragsTypExceptions.BreakingChange}),
 * Lifecycle draft → published → deprecated → archived mit erzwungenen Uebergaengen.
 *
 * <p>{@code tenant_id} neuer Entitaeten stammt aus dem {@link TenantContext}; die
 * RLS-Policy filtert zusaetzlich auf DB-Ebene (ADR-008).
 */
@Service
@Transactional
public class AntragsTypService {

    private final AntragsTypRepository antragsTypRepository;
    private final AntragsTypVersionRepository versionRepository;
    private final CompatibilityClassifier classifier;

    public AntragsTypService(AntragsTypRepository antragsTypRepository,
                             AntragsTypVersionRepository versionRepository,
                             CompatibilityClassifier classifier) {
        this.antragsTypRepository = antragsTypRepository;
        this.versionRepository = versionRepository;
        this.classifier = classifier;
    }

    public AntragsTyp createDefinition(String key, Map<String, String> title, Map<String, String> description) {
        if (antragsTypRepository.existsByKey(key)) {
            throw new AntragsTypExceptions.Conflict("antragstyp key already exists: " + key);
        }
        AntragsTyp at = new AntragsTyp(UuidCreator.getTimeOrderedEpoch(), currentTenant(), key, title, description);
        return antragsTypRepository.save(at);
    }

    @Transactional(readOnly = true)
    public List<AntragsTyp> listDefinitions() {
        return antragsTypRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public AntragsTyp getDefinition(UUID id) {
        return antragsTypRepository.findById(id)
                .orElseThrow(() -> new AntragsTypExceptions.NotFound("antragstyp not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<AntragsTypVersion> listVersions(UUID antragstypId) {
        return versionRepository.findByAntragstypIdOrderByMajorDesc(antragstypId);
    }

    public AntragsTypVersion createDraftMajor(UUID antragstypId, FormDefinition formDefinition,
                                              String workflowBpmn, Map<String, Object> sfActionBindings) {
        AntragsTyp at = getDefinition(antragstypId);
        int nextMajor = versionRepository.maxMajor(at.getId()) + 1;
        AntragsTypVersion v = new AntragsTypVersion(
                UuidCreator.getTimeOrderedEpoch(), currentTenant(), at.getId(),
                nextMajor, formDefinition, workflowBpmn, sfActionBindings);
        return versionRepository.save(v);
    }

    public AntragsTypVersion editDraft(UUID versionId, FormDefinition formDefinition,
                                       String workflowBpmn, Map<String, Object> sfActionBindings) {
        AntragsTypVersion v = getVersion(versionId);
        if (v.getStatus() != VersionStatus.DRAFT) {
            throw new AntragsTypExceptions.IllegalState("only DRAFT versions can be edited freely: " + versionId);
        }
        v.replaceDraftContent(formDefinition, workflowBpmn, sfActionBindings);
        return v;
    }

    public AntragsTypVersion editInPlaceMinor(UUID versionId, FormDefinition newFormDefinition) {
        AntragsTypVersion v = getVersion(versionId);
        if (v.getStatus() != VersionStatus.PUBLISHED && v.getStatus() != VersionStatus.DEPRECATED) {
            throw new AntragsTypExceptions.IllegalState(
                    "minor in-place edits require a PUBLISHED or DEPRECATED major: " + versionId);
        }
        // Minor edits never touch workflow/SF (those are MAJOR by definition): classify against unchanged workflow/bindings.
        ChangeClassification c = classifier.classify(
                v.getFormDefinition(), newFormDefinition,
                v.getWorkflowBpmn(), v.getWorkflowBpmn(),
                v.getSfActionBindings(), v.getSfActionBindings());
        if (c.isMajor()) {
            throw new AntragsTypExceptions.BreakingChange(
                    "change is breaking and requires a new major (version " + versionId + "): " + c.reasons());
        }
        v.applyMinorEdit(newFormDefinition, Map.of("reasons", c.reasons()));
        return v;
    }

    public AntragsTypVersion publish(UUID versionId, UUID publishedBy) {
        AntragsTypVersion target = getVersion(versionId);
        if (target.getStatus() != VersionStatus.DRAFT) {
            throw new AntragsTypExceptions.IllegalState("only DRAFT versions can be published: " + versionId);
        }
        // Enforce exactly-one-published invariant: demote the current published major (if any)
        // BEFORE promoting the target. This invariant is held at the service layer, not by a DB
        // constraint: a `WHERE status='PUBLISHED'` partial-unique index cannot be DEFERRABLE in
        // PostgreSQL and would break this demote->promote flush ordering. Consequence: two
        // concurrent publish() calls for the same antragstyp at READ COMMITTED could both observe
        // the same prior state and both promote, yielding two PUBLISHED majors. Publishing is a
        // rare single-admin action, so we accept this window for now; fully closing it needs
        // SERIALIZABLE isolation or a per-antragstyp advisory/row lock (tracked for a later cut).
        versionRepository.findByAntragstypIdAndStatus(target.getAntragstypId(), VersionStatus.PUBLISHED)
                .ifPresent(prev -> prev.setStatus(VersionStatus.DEPRECATED));

        target.publish(publishedBy);

        AntragsTyp at = getDefinition(target.getAntragstypId());
        at.markLive(target.getId());
        return target;
    }

    public AntragsTypVersion deprecate(UUID versionId) {
        AntragsTypVersion v = getVersion(versionId);
        if (v.getStatus() != VersionStatus.PUBLISHED) {
            throw new AntragsTypExceptions.IllegalState("only PUBLISHED versions can be deprecated: " + versionId);
        }
        // Version-level lifecycle only: the parent AntragsTyp stays LIVE and keeps pointing at
        // currentVersionId until a new major is published (publish() -> markLive). A deprecated
        // major may still receive backward-compatible minor edits for already-pinned in-flight requests.
        v.setStatus(VersionStatus.DEPRECATED);
        return v;
    }

    public AntragsTypVersion archive(UUID versionId) {
        AntragsTypVersion v = getVersion(versionId);
        if (v.getStatus() != VersionStatus.DEPRECATED) {
            throw new AntragsTypExceptions.IllegalState("only DEPRECATED versions can be archived: " + versionId);
        }
        v.setStatus(VersionStatus.ARCHIVED);
        return v;
    }

    private AntragsTypVersion getVersion(UUID id) {
        return versionRepository.findById(id)
                .orElseThrow(() -> new AntragsTypExceptions.NotFound("version not found: " + id));
    }

    private UUID currentTenant() {
        return TenantContext.get().orElseThrow(
                () -> new IllegalStateException("no tenant in context"));
    }
}
