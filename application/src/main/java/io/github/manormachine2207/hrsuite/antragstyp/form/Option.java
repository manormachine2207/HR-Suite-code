package io.github.manormachine2207.hrsuite.antragstyp.form;

import java.util.Map;

/**
 * Auswahloption fuer SELECT/MULTI_SELECT. {@code value} ist stabil (Aenderung = MAJOR),
 * {@code label} ist i18n und in-place aenderbar (MINOR). Siehe ADR-009.
 */
public record Option(
        String value,
        Map<String, String> label) {
}
