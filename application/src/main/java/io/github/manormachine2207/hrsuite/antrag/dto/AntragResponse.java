package io.github.manormachine2207.hrsuite.antrag.dto;

import io.github.manormachine2207.hrsuite.antrag.Antrag;
import io.github.manormachine2207.hrsuite.antrag.AntragStatus;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record AntragResponse(
        UUID id,
        UUID antragstypId,
        UUID antragstypVersionId,
        Integer submittedMinor,
        AntragStatus status,
        Map<String, Object> payload,
        String antragstellerSubject,
        OffsetDateTime submittedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static AntragResponse from(Antrag a) {
        return new AntragResponse(
                a.getId(), a.getAntragstypId(), a.getAntragstypVersionId(), a.getSubmittedMinor(),
                a.getStatus(), a.getPayload(), a.getAntragstellerSubject(),
                a.getSubmittedAt(), a.getCreatedAt(), a.getUpdatedAt());
    }
}
