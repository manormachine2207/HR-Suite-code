package io.github.manormachine2207.hrsuite.antragstyp.form;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Provisorisches Formularfeld. {@code key} ist der stabile Identifier
 * (Umbenennen/Entfernen = MAJOR). Siehe Decision-Draft DRAFT-form-definition-schema.
 *
 * @param key          stabiler Feld-Identifier
 * @param type         Feldtyp
 * @param required     Pflichtfeld?
 * @param label        i18n-Label (de/fr/it/en)
 * @param helpText     i18n-Hilfetext (optional)
 * @param validation   Schranken (optional)
 * @param options      Optionen fuer SELECT/MULTI_SELECT (sonst leer)
 * @param defaultValue Default-Wert als String (optional)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FormField(
        String key,
        FieldType type,
        boolean required,
        Map<String, String> label,
        Map<String, String> helpText,
        Validation validation,
        List<Option> options,
        String defaultValue) {

    public FormField {
        options = options == null ? List.of() : List.copyOf(options);
    }
}
