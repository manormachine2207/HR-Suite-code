package io.github.manormachine2207.hrsuite.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.manormachine2207.hrsuite.shared.secret.SecretResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * {@link ActionConnector} over n8n webhooks (ADR-010 L2). Reads the per-tenant
 * {@link TenantN8nConfig} (RLS-scoped), refuses any {@code ref} not on the tenant's
 * allowlist, HMAC-signs a canonical body and POSTs to {@code {baseUrl}/webhook/{ref}}.
 * 2xx => ok; 4xx => terminal; 5xx / IO / timeout => retryable.
 *
 * <p>Connect and read timeouts are configurable via:
 * <ul>
 *   <li>{@code hrsuite.action.connect-timeout-ms} (default 3000 ms)</li>
 *   <li>{@code hrsuite.action.read-timeout-ms} (default 10000 ms)</li>
 * </ul>
 * A timeout/IO failure surfaces as {@code transientFailure} (retryable) through the
 * existing catch block.
 */
@Component
public class N8nActionConnector implements ActionConnector {

    private final TenantN8nConfigRepository configRepo;
    private final SecretResolver secretResolver;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient;

    @Autowired
    public N8nActionConnector(
            TenantN8nConfigRepository configRepo,
            SecretResolver secretResolver,
            @Value("${hrsuite.action.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${hrsuite.action.read-timeout-ms:10000}") int readTimeoutMs) {
        this.configRepo = configRepo;
        this.secretResolver = secretResolver;
        var factorySettings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .withReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = RestClient.builder()
                .requestFactory(ClientHttpRequestFactoryBuilder.simple().build(factorySettings))
                .build();
    }

    /** Test-only constructor — uses sane default timeouts (3 s connect, 10 s read). */
    N8nActionConnector(TenantN8nConfigRepository configRepo, SecretResolver secretResolver) {
        this(configRepo, secretResolver, 3000, 10000);
    }

    @Override
    public ActionResult execute(ActionRequest request) {
        Optional<TenantN8nConfig> maybe = configRepo.findById(request.tenantId());
        if (maybe.isEmpty()) {
            return ActionResult.terminal(0, "no tenant_n8n_config for tenant " + request.tenantId());
        }
        TenantN8nConfig cfg = maybe.get();
        if (!cfg.getAllowedRefs().contains(request.ref())) {
            return ActionResult.terminal(0, "ref not allowlisted: " + request.ref());
        }

        // SDR-004: the HMAC secret is resolved from the env var NAMED by the config —
        // never stored in the DB. A missing/unset ref means we cannot sign; fail
        // terminally (retrying won't fix a config gap) without leaking the value.
        Optional<String> secret = secretResolver.resolve(cfg.getHmacSecretRef());
        if (secret.isEmpty()) {
            return ActionResult.terminal(0, "hmac secret not configured (env var '"
                    + cfg.getHmacSecretRef() + "') for tenant " + request.tenantId());
        }

        String body = canonical(request);
        String signature = HmacSigner.hexSha256(secret.get(), body);
        String url = trimTrailingSlash(cfg.getBaseUrl()) + "/webhook/" + request.ref();

        try {
            var response = restClient.post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .header("X-HRSuite-Signature", signature)
                    .body(body)
                    .retrieve()
                    .onStatus(s -> true, (req, res) -> { /* never throw; inspect status below */ })
                    .toEntity(String.class);

            int code = response.getStatusCode().value();
            if (code >= 200 && code < 300) {
                return ActionResult.ok(code, parse(response.getBody()));
            }
            if (code >= 400 && code < 500) {
                return ActionResult.terminal(code, "n8n returned " + code);
            }
            return ActionResult.transientFailure(code, "n8n returned " + code);
        } catch (Exception e) {
            return ActionResult.transientFailure(0, "n8n call failed: " + e.getMessage());
        }
    }

    /** Canonical signed body: stable field order so the signature is reproducible. */
    public String canonical(ActionRequest request) {
        Map<String, Object> ordered = new LinkedHashMap<>();
        // Anchor the key on the business key (= antrag id) when present: stays stable when a
        // resubmit starts a new process instance, so n8n can actually deduplicate.
        String anchor = request.businessKey() != null ? request.businessKey() : request.processInstanceId();
        ordered.put("idempotencyKey", anchor + ":" + request.stepKey());
        ordered.put("ref", request.ref());
        ordered.put("input", request.input());
        try {
            return objectMapper.writeValueAsString(ordered);
        } catch (Exception e) {
            throw new IllegalStateException("cannot serialize action body", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String body) {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(body, Map.class);
        } catch (Exception e) {
            return Map.of("raw", body);
        }
    }

    private static String trimTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
