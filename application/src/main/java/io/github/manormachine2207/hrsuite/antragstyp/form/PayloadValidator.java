package io.github.manormachine2207.hrsuite.antragstyp.form;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validiert einen Antrags-Payload gegen die (gepinnte) FormDefinition — die
 * Systemgrenze des Einreich-Pfads (ADR-009 §4, Review 2026-06-12). Pure Funktion.
 *
 * <p>Geprueft werden: unbekannte Keys, required, maxLength (TEXT), min/max (NUMBER),
 * ISO-Datum (DATE), Boolean (BOOLEAN), Optionswerte (SELECT/MULTI_SELECT).
 */
public final class PayloadValidator {

    private PayloadValidator() {
    }

    /** Returns human-readable errors; empty = payload acceptable for submission. */
    public static List<String> errors(FormDefinition definition, Map<String, Object> payload) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> values = payload == null ? Map.of() : payload;
        Set<String> knownKeys = definition.fields().stream().map(FormField::key).collect(Collectors.toSet());

        for (String key : values.keySet()) {
            if (!knownKeys.contains(key)) {
                errors.add("'" + key + "': unknown field");
            }
        }

        for (FormField field : definition.fields()) {
            Object value = values.get(field.key());
            boolean blank = value == null || (value instanceof String s && s.isBlank());
            if (blank) {
                if (field.required()) {
                    errors.add("'" + field.key() + "': required");
                }
                continue;
            }
            switch (field.type()) {
                case TEXT -> validateText(field, value, errors);
                case NUMBER -> validateNumber(field, value, errors);
                case DATE -> validateDate(field, value, errors);
                case BOOLEAN -> {
                    if (!(value instanceof Boolean)) {
                        errors.add("'" + field.key() + "': must be a boolean");
                    }
                }
                case SELECT -> {
                    if (!optionValues(field).contains(String.valueOf(value))) {
                        errors.add("'" + field.key() + "': value not in options");
                    }
                }
                case MULTI_SELECT -> validateMultiSelect(field, value, errors);
            }
        }
        return errors;
    }

    private static void validateText(FormField field, Object value, List<String> errors) {
        String s = String.valueOf(value);
        Integer maxLength = field.validation() == null ? null : field.validation().maxLength();
        if (maxLength != null && s.length() > maxLength) {
            errors.add("'" + field.key() + "': exceeds maxLength " + maxLength);
        }
    }

    private static void validateNumber(FormField field, Object value, List<String> errors) {
        if (!(value instanceof Number n)) {
            errors.add("'" + field.key() + "': must be a number");
            return;
        }
        Validation v = field.validation();
        if (v != null) {
            if (v.min() != null && n.doubleValue() < v.min()) {
                errors.add("'" + field.key() + "': below min " + v.min());
            }
            if (v.max() != null && n.doubleValue() > v.max()) {
                errors.add("'" + field.key() + "': above max " + v.max());
            }
        }
    }

    private static void validateDate(FormField field, Object value, List<String> errors) {
        try {
            LocalDate.parse(String.valueOf(value));
        } catch (DateTimeParseException e) {
            errors.add("'" + field.key() + "': must be an ISO date (yyyy-MM-dd)");
        }
    }

    private static void validateMultiSelect(FormField field, Object value, List<String> errors) {
        if (!(value instanceof Collection<?> values)) {
            errors.add("'" + field.key() + "': must be a list of option values");
            return;
        }
        Set<String> allowed = optionValues(field);
        boolean allKnown = values.stream().map(String::valueOf).allMatch(allowed::contains);
        if (!allKnown) {
            errors.add("'" + field.key() + "': contains values not in options");
        }
    }

    private static Set<String> optionValues(FormField field) {
        return field.options().stream().map(Option::value).filter(Objects::nonNull).collect(Collectors.toSet());
    }
}
