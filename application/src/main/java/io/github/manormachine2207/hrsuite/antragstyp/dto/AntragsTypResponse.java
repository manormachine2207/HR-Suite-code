package io.github.manormachine2207.hrsuite.antragstyp.dto;

import io.github.manormachine2207.hrsuite.antragstyp.AntragsTyp;
import io.github.manormachine2207.hrsuite.antragstyp.AntragsTypStatus;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record AntragsTypResponse(
        UUID id,
        String key,
        Map<String, String> title,
        Map<String, String> description,
        AntragsTypStatus status,
        UUID currentVersionId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static AntragsTypResponse from(AntragsTyp a) {
        return new AntragsTypResponse(
                a.getId(), a.getKey(), a.getTitle(), a.getDescription(),
                a.getStatus(), a.getCurrentVersionId(), a.getCreatedAt(), a.getUpdatedAt());
    }
}
