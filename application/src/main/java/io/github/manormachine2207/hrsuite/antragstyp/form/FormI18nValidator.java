package io.github.manormachine2207.hrsuite.antragstyp.form;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Publish-Gate fuer BDR-005 (DE/FR/IT/EN Pflicht), Review 2026-06-12: alles, was
 * Antragsteller sehen, muss beim Publish in allen vier Sprachen vorliegen —
 * Antragstyp-Titel, Feld-Labels, Options-Labels. {@code helpText} ist optional,
 * aber wenn (teilweise) gesetzt, ebenfalls vollstaendig. Pure Funktion, kein Spring.
 */
public final class FormI18nValidator {

    private static final List<String> REQUIRED_LOCALES = List.of("de", "fr", "it", "en");

    private FormI18nValidator() {
    }

    /** Returns human-readable issues; empty = publishable. */
    public static List<String> issues(Map<String, String> title, FormDefinition definition) {
        List<String> issues = new ArrayList<>();
        addMissing(issues, "title", title, true);
        if (definition != null) {
            for (FormField field : definition.fields()) {
                addMissing(issues, "field '" + field.key() + "' label", field.label(), true);
                addMissing(issues, "field '" + field.key() + "' helpText", field.helpText(), false);
                for (Option option : field.options()) {
                    addMissing(issues, "field '" + field.key() + "' option '" + option.value() + "' label",
                            option.label(), true);
                }
            }
        }
        return issues;
    }

    private static void addMissing(List<String> issues, String what, Map<String, String> texts, boolean mandatory) {
        boolean empty = texts == null || texts.values().stream().allMatch(v -> v == null || v.isBlank());
        if (empty) {
            if (mandatory) {
                issues.add(what + ": missing " + String.join(",", REQUIRED_LOCALES));
            }
            return; // optional + completely empty = fine
        }
        List<String> missing = REQUIRED_LOCALES.stream()
                .filter(l -> texts.get(l) == null || texts.get(l).isBlank())
                .toList();
        if (!missing.isEmpty()) {
            issues.add(what + ": missing " + String.join(",", missing));
        }
    }
}
