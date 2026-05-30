package io.github.manormachine2207.hrsuite.antrag;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.manormachine2207.hrsuite.antragstyp.AntragsTypService;
import io.github.manormachine2207.hrsuite.antragstyp.PublishedMajorRef;
import io.github.manormachine2207.hrsuite.shared.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Anwendungsdienst des Antrag-Moduls. Kapselt den ADR-009-Einreich-/Pin-Pfad: ein
 * Antrag wird als DRAFT angelegt (noch nicht gepinnt) und pinnt bei {@link #submit}
 * den aktuell {@code published} Major des Antragstyps + den {@code submitted_minor}.
 *
 * <p>Mandantenscope via {@link TenantContext} + RLS (ADR-008); die Applicant-Grenze
 * (Antrag.read.own, 04-Authorization-Model) ist der {@code antragstellerSubject}-Match.
 * Der Flowable-Prozessinstanz-Start haengt am workflow-engine-bridge-Cut und ist hier
 * bewusst noch nicht verdrahtet.
 */
@Service
@Transactional
public class AntragService {

    private final AntragRepository antragRepository;
    private final AntragsTypService antragsTypService;

    public AntragService(AntragRepository antragRepository, AntragsTypService antragsTypService) {
        this.antragRepository = antragRepository;
        this.antragsTypService = antragsTypService;
    }

    /** Creates a DRAFT request against a live antragstyp (one that has a published major). */
    public Antrag createDraft(UUID antragstypId, Map<String, Object> payload, String subject) {
        antragsTypService.findPublishedMajor(antragstypId)
                .orElseThrow(() -> new AntragExceptions.NotFound(
                        "antragstyp not found or not published: " + antragstypId));
        Antrag antrag = new Antrag(
                UuidCreator.getTimeOrderedEpoch(), currentTenant(), antragstypId, subject, payload);
        return antragRepository.save(antrag);
    }

    public Antrag editDraft(UUID antragId, Map<String, Object> payload, String subject) {
        Antrag antrag = getOwned(antragId, subject);
        if (antrag.getStatus() != AntragStatus.DRAFT) {
            throw new AntragExceptions.IllegalState("only DRAFT requests can be edited: " + antragId);
        }
        antrag.replaceDraftPayload(payload);
        return antrag;
    }

    /**
     * Pins the antragstyp's currently published major + its minor and submits
     * (ADR-009 §4). Re-reads the published major at submission time, so a major
     * published after the draft was created is the one that gets pinned.
     */
    public Antrag submit(UUID antragId, String subject) {
        Antrag antrag = getOwned(antragId, subject);
        if (antrag.getStatus() != AntragStatus.DRAFT) {
            throw new AntragExceptions.IllegalState("only DRAFT requests can be submitted: " + antragId);
        }
        PublishedMajorRef major = antragsTypService.findPublishedMajor(antrag.getAntragstypId())
                .orElseThrow(() -> new AntragExceptions.IllegalState(
                        "antragstyp has no published major to pin: " + antrag.getAntragstypId()));
        antrag.submit(major.versionId(), major.minor());
        return antrag;
    }

    /** Withdraws a DRAFT or SUBMITTED (pre-decision) request. */
    public Antrag cancel(UUID antragId, String subject) {
        Antrag antrag = getOwned(antragId, subject);
        if (antrag.getStatus() != AntragStatus.DRAFT && antrag.getStatus() != AntragStatus.SUBMITTED) {
            throw new AntragExceptions.IllegalState(
                    "only DRAFT or SUBMITTED requests can be cancelled: " + antragId);
        }
        antrag.cancel();
        return antrag;
    }

    @Transactional(readOnly = true)
    public Antrag getOwn(UUID antragId, String subject) {
        return getOwned(antragId, subject);
    }

    @Transactional(readOnly = true)
    public List<Antrag> listOwn(String subject) {
        return antragRepository.findByAntragstellerSubjectOrderByCreatedAtDesc(subject);
    }

    /** Tenant-wide listing (RLS-scoped); guarded to tenant-admin/hr-reviewer at the API. */
    @Transactional(readOnly = true)
    public List<Antrag> listForTenant() {
        return antragRepository.findAllByOrderByCreatedAtDesc();
    }

    private Antrag getOwned(UUID antragId, String subject) {
        Antrag antrag = antragRepository.findById(antragId)
                .orElseThrow(() -> new AntragExceptions.NotFound("antrag not found: " + antragId));
        // RLS already constrains to the tenant; the applicant boundary is ownership.
        // Report a foreign-but-same-tenant request as NotFound (don't leak existence).
        if (!antrag.getAntragstellerSubject().equals(subject)) {
            throw new AntragExceptions.NotFound("antrag not found: " + antragId);
        }
        return antrag;
    }

    private UUID currentTenant() {
        return TenantContext.require();
    }
}
