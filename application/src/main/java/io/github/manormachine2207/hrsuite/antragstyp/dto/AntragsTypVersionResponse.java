package io.github.manormachine2207.hrsuite.antragstyp.dto;

import io.github.manormachine2207.hrsuite.antragstyp.AntragsTypVersion;
import io.github.manormachine2207.hrsuite.antragstyp.VersionStatus;
import io.github.manormachine2207.hrsuite.antragstyp.form.FormDefinition;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record AntragsTypVersionResponse(
        UUID id,
        UUID antragstypId,
        int major,
        int minor,
        VersionStatus status,
        FormDefinition formDefinition,
        String workflowBpmn,
        Map<String, Object> sfActionBindings,
        OffsetDateTime publishedAt,
        UUID publishedBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static AntragsTypVersionResponse from(AntragsTypVersion v) {
        return new AntragsTypVersionResponse(
                v.getId(), v.getAntragstypId(), v.getMajor(), v.getMinor(), v.getStatus(),
                v.getFormDefinition(), v.getWorkflowBpmn(), v.getSfActionBindings(),
                v.getPublishedAt(), v.getPublishedBy(), v.getCreatedAt(), v.getUpdatedAt());
    }
}
