package io.github.manormachine2207.hrsuite.antragstyp;

import io.github.manormachine2207.hrsuite.antragstyp.dto.AntragsTypResponse;
import io.github.manormachine2207.hrsuite.antragstyp.dto.AntragsTypVersionResponse;
import io.github.manormachine2207.hrsuite.antragstyp.dto.CreateAntragsTypRequest;
import io.github.manormachine2207.hrsuite.antragstyp.dto.CreateVersionRequest;
import io.github.manormachine2207.hrsuite.antragstyp.dto.MinorEditRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * REST-API des Antragstyp-Moduls. Methoden-Autorisierung nach 04-Authorization-Model:
 * read = jede fachliche Rolle; write.draft = nur hr-designer; publish/lifecycle =
 * tenant-admin oder hr-designer. {@code SecurityConfig} setzt die Route auf
 * {@code authenticated()}; RLS (ADR-008) filtert mandantenscharf.
 */
@RestController
@RequestMapping("/api/v1/antragstyp")
public class AntragsTypController {

    private static final String READ = "hasAnyRole('tenant-admin','hr-designer','hr-reviewer','applicant')";
    private static final String WRITE_DRAFT = "hasRole('hr-designer')";
    private static final String PUBLISH = "hasAnyRole('tenant-admin','hr-designer')";

    private final AntragsTypService service;

    public AntragsTypController(AntragsTypService service) {
        this.service = service;
    }

    // ---- definitions ------------------------------------------------------
    @PostMapping
    @PreAuthorize(WRITE_DRAFT)
    public ResponseEntity<AntragsTypResponse> create(@Valid @RequestBody CreateAntragsTypRequest req,
                                                     UriComponentsBuilder uri) {
        AntragsTyp created = service.createDefinition(req.key(), req.title(), req.description());
        URI location = uri.path("/api/v1/antragstyp/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(AntragsTypResponse.from(created));
    }

    @GetMapping
    @PreAuthorize(READ)
    public List<AntragsTypResponse> list() {
        return service.listDefinitions().stream().map(AntragsTypResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    public AntragsTypResponse get(@PathVariable("id") UUID id) {
        return AntragsTypResponse.from(service.getDefinition(id));
    }

    // ---- versions ---------------------------------------------------------
    @PostMapping("/{id}/versions")
    @PreAuthorize(WRITE_DRAFT)
    public ResponseEntity<AntragsTypVersionResponse> createVersion(@PathVariable("id") UUID id,
                                                                   @Valid @RequestBody CreateVersionRequest req,
                                                                   UriComponentsBuilder uri) {
        AntragsTypVersion v = service.createDraftMajor(id, req.formDefinition(), req.workflowBpmn(), req.sfActionBindings());
        URI location = uri.path("/api/v1/antragstyp/versions/{vid}").buildAndExpand(v.getId()).toUri();
        return ResponseEntity.created(location).body(AntragsTypVersionResponse.from(v));
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize(READ)
    public List<AntragsTypVersionResponse> listVersions(@PathVariable("id") UUID id) {
        return service.listVersions(id).stream().map(AntragsTypVersionResponse::from).toList();
    }

    @PutMapping("/versions/{vid}/draft")
    @PreAuthorize(WRITE_DRAFT)
    public AntragsTypVersionResponse editDraft(@PathVariable("vid") UUID vid, @Valid @RequestBody CreateVersionRequest req) {
        return AntragsTypVersionResponse.from(
                service.editDraft(vid, req.formDefinition(), req.workflowBpmn(), req.sfActionBindings()));
    }

    @PutMapping("/versions/{vid}/minor")
    @PreAuthorize(WRITE_DRAFT)
    public AntragsTypVersionResponse editMinor(@PathVariable("vid") UUID vid, @Valid @RequestBody MinorEditRequest req) {
        return AntragsTypVersionResponse.from(service.editInPlaceMinor(vid, req.formDefinition()));
    }

    // ---- lifecycle --------------------------------------------------------
    @PostMapping("/versions/{vid}/publish")
    @PreAuthorize(PUBLISH)
    public AntragsTypVersionResponse publish(@PathVariable("vid") UUID vid) {
        // publishedBy stays null until the identity module maps the JWT subject to a User id
        // (published_by is a nullable FK). Wiring lands with the identity-sp cut.
        return AntragsTypVersionResponse.from(service.publish(vid, null));
    }

    @PostMapping("/versions/{vid}/deprecate")
    @PreAuthorize(PUBLISH)
    public AntragsTypVersionResponse deprecate(@PathVariable("vid") UUID vid) {
        return AntragsTypVersionResponse.from(service.deprecate(vid));
    }

    @PostMapping("/versions/{vid}/archive")
    @PreAuthorize(PUBLISH)
    public AntragsTypVersionResponse archive(@PathVariable("vid") UUID vid) {
        return AntragsTypVersionResponse.from(service.archive(vid));
    }
}
