package io.github.manormachine2207.hrsuite.notification;

/** Domain exceptions of the notification module; HTTP mapping in ApiExceptionHandler. */
public final class NotificationExceptions {

    private NotificationExceptions() {
    }

    /** Invalid request (e.g. test-send without recipient, relay disabled). HTTP 422. */
    public static class Invalid extends RuntimeException {
        public Invalid(String message) {
            super(message);
        }
    }

    /** Notification not found, or owned by another recipient. HTTP 404. */
    public static class NotFound extends RuntimeException {
        public NotFound(String message) {
            super(message);
        }
    }
}
