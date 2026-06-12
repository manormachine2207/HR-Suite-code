package io.github.manormachine2207.hrsuite.workflow;

import java.time.Instant;

/**
 * Ein Schritt (userTask) einer Prozessinstanz fuer die Fortschritts-Anzeige
 * (Genehmigungsketten-Stepper, Cut D): durchlaufene Schritte sind {@code completed},
 * der aktuell offene nicht. Quelle ist die Flowable-Historie, daher auch nach
 * Prozessende verfuegbar.
 */
public record WorkflowStepInfo(
        String stepKey,
        String name,
        boolean completed,
        Instant startedAt,
        Instant completedAt) {
}
