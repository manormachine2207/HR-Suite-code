package io.github.manormachine2207.hrsuite.notification.dto;

/** Result of a test send: success flag + verbatim outcome (never a secret). */
public record TestSendResponse(boolean success, String outcome) {
}
