package io.github.manormachine2207.hrsuite.antragstyp;

/**
 * Domaenen-Exceptions des Antragstyp-Moduls. Mapping auf HTTP-Status erfolgt im
 * {@code ApiExceptionHandler} (Task 8): Conflict→409, NotFound→404,
 * BreakingChange→422, IllegalState→409.
 */
public final class AntragsTypExceptions {

    private AntragsTypExceptions() {
    }

    /** Eindeutigkeits-/Konflikt-Verletzung (z. B. doppelter key). HTTP 409. */
    public static class Conflict extends RuntimeException {
        public Conflict(String message) {
            super(message);
        }
    }

    /** Ressource nicht gefunden. HTTP 404. */
    public static class NotFound extends RuntimeException {
        public NotFound(String message) {
            super(message);
        }
    }

    /** In-place-Minor-Edit ist nicht rueckwaerts-kompatibel (ADR-009). HTTP 422. */
    public static class BreakingChange extends RuntimeException {
        public BreakingChange(String message) {
            super(message);
        }
    }

    /** Unzulaessiger Lifecycle-Uebergang. HTTP 409. */
    public static class IllegalState extends RuntimeException {
        public IllegalState(String message) {
            super(message);
        }
    }
}
