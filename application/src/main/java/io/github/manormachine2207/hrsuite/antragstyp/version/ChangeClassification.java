package io.github.manormachine2207.hrsuite.antragstyp.version;

import java.util.List;

/**
 * Ergebnis der Kompatibilitaets-Klassifizierung eines Definition-Diffs (ADR-009).
 *
 * @param changeClass MINOR (in-place erlaubt) oder MAJOR (neuer Major erzwungen)
 * @param reasons     menschenlesbare Begruendungen (bei MAJOR: die brechenden Aenderungen)
 */
public record ChangeClassification(
        ChangeClass changeClass,
        List<String> reasons) {

    public boolean isMajor() {
        return changeClass == ChangeClass.MAJOR;
    }
}
