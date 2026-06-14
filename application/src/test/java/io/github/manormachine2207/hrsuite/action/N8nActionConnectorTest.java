package io.github.manormachine2207.hrsuite.action;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.manormachine2207.hrsuite.shared.secret.SecretResolver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class N8nActionConnectorTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private HttpServer server;
    private int port;
    private final AtomicInteger statusToReturn = new AtomicInteger(200);
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastSignature = new AtomicReference<>();
    private final AtomicInteger callCount = new AtomicInteger(0);

    private static final String HMAC_REF = "N8N_HMAC_REF";
    private static final String HMAC_SECRET = "topsecret";

    private TenantN8nConfigRepository repo;
    private N8nActionConnector connector;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/webhook/", exchange -> {
            callCount.incrementAndGet();
            byte[] in = exchange.getRequestBody().readAllBytes();
            lastBody.set(new String(in, StandardCharsets.UTF_8));
            lastSignature.set(exchange.getRequestHeaders().getFirst("X-HRSuite-Signature"));
            int code = statusToReturn.get();
            byte[] out = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        port = server.getAddress().getPort();

        repo = mock(TenantN8nConfigRepository.class);
        when(repo.findById(TENANT)).thenReturn(Optional.of(
                new TenantN8nConfig(TENANT, "http://127.0.0.1:" + port, HMAC_REF,
                        List.of("provision-ad-account"))));
        // SDR-004: the config carries the env-var NAME; the resolver supplies the value.
        SecretResolver secretResolver = ref -> HMAC_REF.equals(ref) ? Optional.of(HMAC_SECRET) : Optional.empty();
        connector = new N8nActionConnector(repo, secretResolver);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private ActionRequest req(String ref) {
        return new ActionRequest(TENANT, "pi-1", "antrag-1", "ad", ref, Map.of("upn", "a@b.ch"));
    }

    @Test
    void postsToWebhookWithSignatureOnSuccess() {
        ActionResult r = connector.execute(req("provision-ad-account"));
        assertThat(r.success()).isTrue();
        assertThat(callCount.get()).isEqualTo(1);
        assertThat(lastSignature.get())
                .isEqualTo(HmacSigner.hexSha256(HMAC_SECRET,
                        connector.canonical(req("provision-ad-account"))));
        assertThat(lastBody.get()).contains("provision-ad-account").contains("a@b.ch");
    }

    @Test
    void idempotencyKeyAnchorsOnBusinessKeyWhenPresent() {
        // stable across process instances: a resubmit (new pi) keeps the same key
        assertThat(connector.canonical(req("provision-ad-account")))
                .contains("\"idempotencyKey\":\"antrag-1:ad\"");
    }

    @Test
    void idempotencyKeyFallsBackToProcessInstanceWithoutBusinessKey() {
        var noBk = new ActionRequest(TENANT, "pi-1", null, "ad", "provision-ad-account", Map.of());
        assertThat(connector.canonical(noBk))
                .contains("\"idempotencyKey\":\"pi-1:ad\"");
    }

    @Test
    void rejectsRefNotInAllowlistWithoutCallingServer() {
        ActionResult r = connector.execute(req("delete-everything"));
        assertThat(r.success()).isFalse();
        assertThat(r.retryable()).isFalse();
        assertThat(callCount.get()).isZero();
    }

    @Test
    void serverErrorIsRetryable() {
        statusToReturn.set(500);
        ActionResult r = connector.execute(req("provision-ad-account"));
        assertThat(r.success()).isFalse();
        assertThat(r.retryable()).isTrue();
    }

    @Test
    void clientErrorIsTerminal() {
        statusToReturn.set(400);
        ActionResult r = connector.execute(req("provision-ad-account"));
        assertThat(r.success()).isFalse();
        assertThat(r.retryable()).isFalse();
    }

    @Test
    void missingConfigIsTerminal() {
        when(repo.findById(TENANT)).thenReturn(Optional.empty());
        ActionResult r = connector.execute(req("provision-ad-account"));
        assertThat(r.success()).isFalse();
        assertThat(r.retryable()).isFalse();
    }

    /** SDR-004: an unset HMAC env var is a config gap — terminal, and never calls the server. */
    @Test
    void unconfiguredHmacSecretRefIsTerminalWithoutCallingServer() {
        when(repo.findById(TENANT)).thenReturn(Optional.of(
                new TenantN8nConfig(TENANT, "http://127.0.0.1:" + port, "UNSET_REF",
                        List.of("provision-ad-account"))));   // resolver returns empty for UNSET_REF

        ActionResult r = connector.execute(req("provision-ad-account"));

        assertThat(r.success()).isFalse();
        assertThat(r.retryable()).isFalse();
        assertThat(callCount.get()).isZero();
    }
}
