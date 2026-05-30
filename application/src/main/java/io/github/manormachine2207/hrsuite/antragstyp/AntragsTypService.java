package io.github.manormachine2207.hrsuite.antragstyp;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.manormachine2207.hrsuite.antragstyp.form.FormDefinition;
import io.github.manormachine2207.hrsuite.antragstyp.version.ChangeClassification;
import io.github.manormachine2207.hrsuite.antragstyp.version.CompatibilityClassifier;
import io.github.manormachine2207.hrsuite.shared.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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

    /**
     * Fixed namespace seed for the per-antragstyp publish advisory lock (see
     * {@link #publish}). Arbitrary but stable; keeps this lock's 64-bit key space from
     * colliding with any other {@code pg_advisory_*} user. ("AT_PUBL1")
     */
    private static final long PUBLISH_LOCK_NAMESPACE = 0x4154_5F50_5542_4C31L;

    private final AntragsTypRepository antragsTypRepository;
    private final AntragsTypVersionRepository versionRepository;
    private final CompatibilityClassifier classifier;

    @PersistenceContext
    private EntityManager entityManager;

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
        UUID antragstypId = versionRepository.findAntragstypIdById(versionId)
                .orElseThrow(() -> new AntragsTypExceptions.NotFound("version not found: " + versionId));

        // ADR-009 "exactly one PUBLISHED major per Antragstyp": serialize all publishes of the
        // same antragstyp on a per-antragstyp advisory lock taken BEFORE we read the current
        // published major. Without it, two concurrent publish() calls at READ COMMITTED could
        // both observe the same prior state (or both observe "none published"), both demote it,
        // and both promote their own target -> two PUBLISHED majors. We use a transaction-scoped
        // advisory lock rather than the alternatives because:
        //   - SERIALIZABLE would force a serialization-failure (40001) retry loop on this path;
        //   - a `WHERE status='PUBLISHED'` partial-unique index cannot be DEFERRABLE in
        //     PostgreSQL and would break the demote->promote flush ordering below.
        // The lock auto-releases on commit/rollback and runs on the same transactional
        // connection, so RLS's app.tenant_id GUC (set by TenantContextAspect, ADR-008) stays in
        // force for the demote/promote statements.
        lockForPublish(antragstypId);

        // Read the target fresh under the lock: a competing publish that committed while we waited
        // for the lock may already have promoted a different major (or this very version).
        AntragsTypVersion target = getVersion(versionId);
        if (target.getStatus() != VersionStatus.DRAFT) {
            throw new AntragsTypExceptions.IllegalState("only DRAFT versions can be published: " + versionId);
        }
        // Demote the current published major (if any) BEFORE promoting the target.
        versionRepository.findByAntragstypIdAndStatus(antragstypId, VersionStatus.PUBLISHED)
                .ifPresent(prev -> prev.setStatus(VersionStatus.DEPRECATED));

        target.publish(publishedBy);

        AntragsTyp at = getDefinition(antragstypId);
        at.markLive(target.getId());
        return target;
    }

    /**
     * Acquires the per-antragstyp publish lock for the remainder of the current transaction
     * (auto-released on commit/rollback), serializing concurrent {@link #publish} calls for the
     * same antragstyp. {@code hashtextextended} maps the antragstyp id into the advisory-lock
     * {@code bigint} key space under {@link #PUBLISH_LOCK_NAMESPACE}. The {@code SELECT 1 FROM
     * (...)} wrapper keeps the {@code void}-returning lock function out of the result mapping.
     * Runs on the transactional connection and touches no RLS table, so it leaves the
     * {@code app.tenant_id} GUC untouched.
     */
    private void lockForPublish(UUID antragstypId) {
        entityManager.createNativeQuery(
                        "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(:id, :seed))) publish_lock")
                .setParameter("id", antragstypId.toString())
                .setParameter("seed", PUBLISH_LOCK_NAMESPACE)
                .getSingleResult();
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
        return TenantContext.require();
    }
}
