package io.github.manormachine2207.hrsuite.notification;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.github.manormachine2207.hrsuite.notification.EmailTemplates.ANTRAG_DECIDED;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link EmailTemplates} — no Spring context required (BDR-005 / ADR-017 Stufe 2c).
 */
class EmailTemplatesTest {

    private static final String ANTRAG_ID = UUID.randomUUID().toString();
    private static final String RECIPIENT  = "a@b.ch";

    // ── FR render ─────────────────────────────────────────────────────────────

    @Test
    void fr_approved_subjectIsFrench_bodyContainsApprouveAndAntragId() {
        MailMessage msg = EmailTemplates.render(
                ANTRAG_DECIDED, "fr", RECIPIENT,
                Map.of("status", "APPROVED", "antragId", ANTRAG_ID));

        assertEquals(RECIPIENT, msg.to());
        assertTrue(msg.subject().contains("demande"),
                "FR subject should mention 'demande', got: " + msg.subject());
        assertTrue(msg.bodyText().contains("approuvé"),
                "FR body should contain 'approuvé', got: " + msg.bodyText());
        assertTrue(msg.bodyText().contains(ANTRAG_ID),
                "FR body should contain the antragId");
    }

    // ── Locale fallback ───────────────────────────────────────────────────────

    @Test
    void unknownLocale_fallsBackToGerman() {
        MailMessage msg = EmailTemplates.render(
                ANTRAG_DECIDED, "xx", RECIPIENT,
                Map.of("status", "APPROVED", "antragId", ANTRAG_ID));

        assertTrue(msg.subject().contains("Antrag"),
                "Unknown locale must fall back to DE; subject: " + msg.subject());
    }

    @Test
    void nullLocale_fallsBackToGerman() {
        MailMessage msg = EmailTemplates.render(
                ANTRAG_DECIDED, null, RECIPIENT,
                Map.of("status", "APPROVED", "antragId", ANTRAG_ID));

        assertTrue(msg.subject().contains("Antrag"),
                "null locale must fall back to DE; subject: " + msg.subject());
    }

    // ── All 4 locales produce distinct, non-empty subjects ───────────────────

    @Test
    void fourLocales_produceDistinctNonEmptySubjects() {
        Map<String, Object> params = Map.of("status", "APPROVED", "antragId", ANTRAG_ID);
        Set<String> subjects = new java.util.HashSet<>();

        for (String locale : new String[]{"de", "fr", "it", "en"}) {
            MailMessage msg = EmailTemplates.render(ANTRAG_DECIDED, locale, RECIPIENT, params);
            assertNotNull(msg.subject(), "subject must not be null for locale " + locale);
            assertFalse(msg.subject().isBlank(), "subject must not be blank for locale " + locale);
            subjects.add(msg.subject());
        }

        assertEquals(4, subjects.size(), "All 4 locales must produce distinct subjects: " + subjects);
    }

    // ── Unknown template key ──────────────────────────────────────────────────

    @Test
    void unknownTemplateKey_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                EmailTemplates.render("NO_SUCH_TEMPLATE", "de", RECIPIENT,
                        Map.of("status", "APPROVED", "antragId", ANTRAG_ID)));
    }

    // ── Unknown status → raw string, no crash ────────────────────────────────

    @Test
    void unknownStatus_appearsRawInBody() {
        String weirdStatus = "PENDING_WEIRD_STATE";
        MailMessage msg = EmailTemplates.render(
                ANTRAG_DECIDED, "de", RECIPIENT,
                Map.of("status", weirdStatus, "antragId", ANTRAG_ID));

        assertTrue(msg.bodyText().contains(weirdStatus),
                "Unknown status must appear as-is in body; body: " + msg.bodyText());
    }

    // ── Smoke: all locales × all known statuses ───────────────────────────────

    @Test
    void allLocalesAndStatuses_doNotThrow() {
        String[] locales   = {"de", "fr", "it", "en"};
        String[] statuses  = {"APPROVED", "REJECTED", "COMPLETED"};

        for (String locale : locales) {
            for (String status : statuses) {
                MailMessage msg = EmailTemplates.render(
                        ANTRAG_DECIDED, locale, RECIPIENT,
                        Map.of("status", status, "antragId", ANTRAG_ID));
                assertNotNull(msg);
                assertFalse(msg.bodyText().isBlank(),
                        "Body must not be blank for locale=" + locale + " status=" + status);
            }
        }
    }

    // ── BCP-47 extended tag (e.g. fr-CH) ─────────────────────────────────────

    @Test
    void bcp47Tag_frCH_resolvedToFrench() {
        MailMessage msg = EmailTemplates.render(
                ANTRAG_DECIDED, "fr-CH", RECIPIENT,
                Map.of("status", "APPROVED", "antragId", ANTRAG_ID));

        assertTrue(msg.subject().contains("demande"),
                "fr-CH should resolve to FR; subject: " + msg.subject());
    }

    // ── DE baseline text check ────────────────────────────────────────────────

    @Test
    void de_approved_subjectAndBodyMatchBaseline() {
        MailMessage msg = EmailTemplates.render(
                ANTRAG_DECIDED, "de", RECIPIENT,
                Map.of("status", "APPROVED", "antragId", ANTRAG_ID));

        assertEquals("HR-Suite: Ihr Antrag wurde entschieden", msg.subject());
        assertTrue(msg.bodyText().contains("genehmigt"),
                "DE body should contain 'genehmigt'");
        assertTrue(msg.bodyText().contains(ANTRAG_ID));
        assertTrue(msg.bodyText().contains("Freundliche Grüsse"));
    }
}
