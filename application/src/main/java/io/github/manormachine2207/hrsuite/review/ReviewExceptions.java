package io.github.manormachine2207.hrsuite.review;

/** Domaenen-Exceptions des Review-Moduls; HTTP-Mapping im ApiExceptionHandler. */
public final class ReviewExceptions {

    private ReviewExceptions() {
    }

    /** Task existiert nicht, ist nicht offen oder gehoert einem anderen Tenant. HTTP 404. */
    public static class NotFound extends RuntimeException {
        public NotFound(String message) {
            super(message);
        }
    }

    /** Outcome verletzt Pattern oder deklarierte Whitelist (ADR-013). HTTP 422. */
    public static class Invalid extends RuntimeException {
        public Invalid(String message) {
            super(message);
        }
    }
}
