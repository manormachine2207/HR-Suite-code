package io.github.manormachine2207.hrsuite.antragstyp.flow;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * One step in a low-code Antragstyp flow (DRAFT-ADR-010 L1). Sealed so the compiler
 * handles every type exhaustively. Jackson polymorphism on the {@code kind} field.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = FormStep.class, name = "FORM"),
        @JsonSubTypes.Type(value = ApprovalStep.class, name = "APPROVAL"),
        @JsonSubTypes.Type(value = ActionStep.class, name = "ACTION"),
        @JsonSubTypes.Type(value = BranchStep.class, name = "BRANCH")
})
public sealed interface FlowStep permits FormStep, ApprovalStep, ActionStep, BranchStep {
    String key();
}
