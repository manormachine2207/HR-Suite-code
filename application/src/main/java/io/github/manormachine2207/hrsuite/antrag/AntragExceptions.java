package io.github.manormachine2207.hrsuite.antrag;

/**
 * Domain exceptions of the antrag module. Mapped to HTTP status in
 * {@code ApiExceptionHandler}: NotFound → 404, IllegalState → 409.
 */
public final class AntragExceptions {

    private AntragExceptions() {
    }

    /** Resource not found (or not visible in the current tenant / to the caller). HTTP 404. */
    public static class NotFound extends RuntimeException {
        public NotFound(String message) {
            super(message);
        }
    }

    /** Illegal lifecycle transition (e.g. submitting a non-DRAFT request). HTTP 409. */
    public static class IllegalState extends RuntimeException {
        public IllegalState(String message) {
            super(message);
        }
    }
}
