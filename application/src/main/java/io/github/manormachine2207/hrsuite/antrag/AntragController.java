package io.github.manormachine2207.hrsuite.antrag;

import io.github.manormachine2207.hrsuite.antrag.dto.AntragResponse;
import io.github.manormachine2207.hrsuite.antrag.dto.CreateAntragRequest;
import io.github.manormachine2207.hrsuite.antrag.dto.EditDraftRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * REST-API des Antrag-Moduls (04-Authorization-Model). Applicant-Pfad
 * (create/edit-draft/submit/cancel/read-own) ist fuer jeden authentifizierten Nutzer
 * (= mindestens {@code applicant}); die mandantenweite Liste ist {@code tenant-admin}
 * /{@code hr-reviewer} vorbehalten. RLS (ADR-008) filtert zusaetzlich mandantenscharf;
 * der {@code antragsteller}-Subject kommt aus dem JWT.
 */
@RestController
@RequestMapping("/api/v1/antrag")
public class AntragController {

    private static final String APPLICANT = "isAuthenticated()";
    private static final String READ_TENANT = "hasAnyRole('tenant-admin','hr-reviewer')";

    private final AntragService service;

    public AntragController(AntragService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize(APPLICANT)
    public ResponseEntity<AntragResponse> create(@Valid @RequestBody CreateAntragRequest req,
                                                 @AuthenticationPrincipal Jwt jwt,
                                                 UriComponentsBuilder uri) {
        Antrag created = service.createDraft(req.antragstypId(), req.payload(), jwt.getSubject());
        URI location = uri.path("/api/v1/antrag/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(AntragResponse.from(created));
    }

    @PutMapping("/{id}/draft")
    @PreAuthorize(APPLICANT)
    public AntragResponse editDraft(@PathVariable("id") UUID id,
                                    @RequestBody EditDraftRequest req,
                                    @AuthenticationPrincipal Jwt jwt) {
        return AntragResponse.from(service.editDraft(id, req.payload(), jwt.getSubject()));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize(APPLICANT)
    public AntragResponse submit(@PathVariable("id") UUID id, @AuthenticationPrincipal Jwt jwt) {
        return AntragResponse.from(service.submit(id, jwt.getSubject()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(APPLICANT)
    public AntragResponse cancel(@PathVariable("id") UUID id, @AuthenticationPrincipal Jwt jwt) {
        return AntragResponse.from(service.cancel(id, jwt.getSubject()));
    }

    @GetMapping("/{id}")
    @PreAuthorize(APPLICANT)
    public AntragResponse get(@PathVariable("id") UUID id, @AuthenticationPrincipal Jwt jwt) {
        return AntragResponse.from(service.getOwn(id, jwt.getSubject()));
    }

    @GetMapping
    @PreAuthorize(APPLICANT)
    public List<AntragResponse> listOwn(@AuthenticationPrincipal Jwt jwt) {
        return service.listOwn(jwt.getSubject()).stream().map(AntragResponse::from).toList();
    }

    @GetMapping("/tenant")
    @PreAuthorize(READ_TENANT)
    public List<AntragResponse> listForTenant() {
        return service.listForTenant().stream().map(AntragResponse::from).toList();
    }
}
