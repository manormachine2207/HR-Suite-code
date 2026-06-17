package io.github.manormachine2207.hrsuite.notification;

import java.util.Map;

/**
 * Pure, stateless multilingual e-mail template renderer (BDR-005 / ADR-017 Stufe 2c).
 *
 * <p>Supports 4 locales: de (lead language / fallback), fr, it, en.
 * The only template key currently defined is {@link #ANTRAG_DECIDED}.
 *
 * <p>No Spring dependency – unit-testable without context.
 */
public final class EmailTemplates {

    public static final String ANTRAG_DECIDED = "ANTRAG_DECIDED";

    private EmailTemplates() {}

    // ── Status localisation ────────────────────────────────────────────────────

    private static final Map<String, Map<String, String>> STATUS_WORDS = Map.of(
            "de", Map.of(
                    "APPROVED",  "genehmigt",
                    "REJECTED",  "abgelehnt",
                    "COMPLETED", "abgeschlossen"),
            "fr", Map.of(
                    "APPROVED",  "approuvé",
                    "REJECTED",  "refusé",
                    "COMPLETED", "terminé"),
            "it", Map.of(
                    "APPROVED",  "approvato",
                    "REJECTED",  "respinto",
                    "COMPLETED", "completato"),
            "en", Map.of(
                    "APPROVED",  "approved",
                    "REJECTED",  "rejected",
                    "COMPLETED", "completed")
    );

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Renders the requested template in the given locale.
     *
     * @param templateKey one of the {@code EmailTemplates.*} constants
     * @param locale      BCP-47 language tag or simple code; unknown/null → "de"
     * @param recipientEmail  the To: address
     * @param params      template parameters (see per-template javadoc)
     * @return a ready-to-send {@link MailMessage}
     * @throws IllegalArgumentException for unknown templateKey
     */
    public static MailMessage render(String templateKey,
                                     String locale,
                                     String recipientEmail,
                                     Map<String, Object> params) {
        String lang = normaliseLocale(locale);

        if (ANTRAG_DECIDED.equals(templateKey)) {
            return renderAntragDecided(lang, recipientEmail, params);
        }

        throw new IllegalArgumentException("Unknown email template key: " + templateKey);
    }

    // ── Template: ANTRAG_DECIDED ───────────────────────────────────────────────

    /**
     * Params: {@code status} (APPROVED/REJECTED/COMPLETED or any string),
     *         {@code antragId} (UUID string).
     */
    private static MailMessage renderAntragDecided(String lang,
                                                    String recipientEmail,
                                                    Map<String, Object> params) {
        String rawStatus = String.valueOf(params.getOrDefault("status", ""));
        String antragId  = String.valueOf(params.getOrDefault("antragId", ""));
        String statusWord = localiseStatus(lang, rawStatus);

        String subject;
        String body;

        switch (lang) {
            case "fr" -> {
                subject = "HR-Suite : Votre demande a été traitée";
                body = "Bonjour\n\n"
                        + "Votre demande a été " + statusWord + ".\n\n"
                        + "Vous trouverez les détails dans HR-Suite sous « Mes demandes » "
                        + "(référence de la demande : " + antragId + ").\n\n"
                        + "Cordialement\nHR-Suite";
            }
            case "it" -> {
                subject = "HR-Suite: La sua richiesta è stata decisa";
                body = "Gentile utente\n\n"
                        + "La sua richiesta è stata " + statusWord + ".\n\n"
                        + "I dettagli sono disponibili in HR-Suite alla voce «Le mie richieste» "
                        + "(riferimento richiesta: " + antragId + ").\n\n"
                        + "Cordiali saluti\nHR-Suite";
            }
            case "en" -> {
                subject = "HR-Suite: Your request has been decided";
                body = "Dear user\n\n"
                        + "Your request has been " + statusWord + ".\n\n"
                        + "You can find the details in HR-Suite under \"My Requests\" "
                        + "(request reference: " + antragId + ").\n\n"
                        + "Kind regards\nHR-Suite";
            }
            default -> {  // "de" (lead language / fallback)
                subject = "HR-Suite: Ihr Antrag wurde entschieden";
                body = "Guten Tag\n\n"
                        + "Ihr Antrag wurde " + statusWord + ".\n\n"
                        + "Details finden Sie in der HR-Suite unter \"Meine Anträge\" "
                        + "(Antrag-Referenz " + antragId + ").\n\n"
                        + "Freundliche Grüsse\nHR-Suite";
            }
        }

        return MailMessage.text(recipientEmail, subject, body);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Normalises any locale string to one of {@code de|fr|it|en}; unknown/null → {@code de}.
     * Handles BCP-47 tags like "fr-CH" by extracting the primary subtag.
     */
    static String normaliseLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            return "de";
        }
        // Extract primary language subtag (before '-' or '_')
        String primary = locale.trim().toLowerCase()
                .split("[-_]")[0];
        return switch (primary) {
            case "fr" -> "fr";
            case "it" -> "it";
            case "en" -> "en";
            default  -> "de";
        };
    }

    private static String localiseStatus(String lang, String rawStatus) {
        return STATUS_WORDS.getOrDefault(lang, Map.of())
                .getOrDefault(rawStatus, rawStatus);
    }
}
