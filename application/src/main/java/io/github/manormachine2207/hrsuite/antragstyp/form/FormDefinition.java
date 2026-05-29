package io.github.manormachine2207.hrsuite.antragstyp.form;

import java.util.List;

/**
 * Provisorisches Wurzel-Objekt der Formular-Definition: eine geordnete Feldliste.
 *
 * <p><strong>Decision-Draft:</strong> Die konkrete JSON-Struktur ist in ADR-009
 * bewusst NICHT entschieden. Dieses Modell ist minimal und provisorisch; ein
 * spaeterer FormDefinition-Cut (evtl. eigene ADR) ersetzt oder bestaetigt es.
 * Siehe {@code Entscheidungen/_drafts/DRAFT-form-definition-schema.md}.
 */
public record FormDefinition(
        List<FormField> fields) {

    public FormDefinition {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
