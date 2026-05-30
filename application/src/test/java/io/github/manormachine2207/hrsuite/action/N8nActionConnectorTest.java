package io.github.manormachine2207.hrsuite.action;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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
        when(repo.findById(TENANT)).thenReturn(java.util.Optional.of(
                new TenantN8nConfig(TENANT, "http://127.0.0.1:" + port, "topsecret",
                        List.of("provision-ad-account"))));
        connector = new N8nActionConnector(repo);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private ActionRequest req(String ref) {
        return new ActionRequest(TENANT, "pi-1", "ad", ref, Map.of("upn", "a@b.ch"));
    }

    @Test
    void postsToWebhookWithSignatureOnSuccess() {
        ActionResult r = connector.execute(req("provision-ad-account"));
        assertThat(r.success()).isTrue();
        assertThat(callCount.get()).isEqualTo(1);
        assertThat(lastSignature.get())
                .isEqualTo(HmacSigner.hexSha256("topsecret",
                        connector.canonical(req("provision-ad-account"))));
        assertThat(lastBody.get()).contains("provision-ad-account").contains("a@b.ch");
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
        when(repo.findById(TENANT)).thenReturn(java.util.Optional.empty());
        ActionResult r = connector.execute(req("provision-ad-account"));
        assertThat(r.success()).isFalse();
        assertThat(r.retryable()).isFalse();
    }
}
