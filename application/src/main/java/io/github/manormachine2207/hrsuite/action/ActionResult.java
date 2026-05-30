package io.github.manormachine2207.hrsuite.action;

import java.util.Map;

/**
 * Outcome of one connector attempt. {@code retryable} true means a transient failure
 * (5xx / timeout / IO) that the orchestrator may retry; false with success false is a
 * terminal failure (e.g. 4xx / allowlist reject).
 */
public record ActionResult(
        boolean success,
        boolean retryable,
        int statusCode,
        Map<String, Object> output,
        String error) {

    public static ActionResult ok(int statusCode, Map<String, Object> output) {
        return new ActionResult(true, false, statusCode, output, null);
    }

    public static ActionResult terminal(int statusCode, String error) {
        return new ActionResult(false, false, statusCode, Map.of(), error);
    }

    public static ActionResult transientFailure(int statusCode, String error) {
        return new ActionResult(false, true, statusCode, Map.of(), error);
    }
}
