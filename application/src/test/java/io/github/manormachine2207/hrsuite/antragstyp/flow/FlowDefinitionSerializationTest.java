package io.github.manormachine2207.hrsuite.antragstyp.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FlowDefinitionSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void roundTripFormStep() throws Exception {
        var step = new FormStep("antrag", Map.of("de", "Antrag stellen", "en", "Submit request"));
        var def = new FlowDefinition(List.of(step));
        String json = mapper.writeValueAsString(def);
        assertThat(json).contains("\"kind\":\"FORM\"").contains("antrag");
        FlowDefinition parsed = mapper.readValue(json, FlowDefinition.class);
        assertThat(parsed.steps()).hasSize(1);
        assertThat(parsed.steps().get(0)).isInstanceOf(FormStep.class);
        assertThat(((FormStep) parsed.steps().get(0)).key()).isEqualTo("antrag");
        assertThat(((FormStep) parsed.steps().get(0)).title()).containsEntry("de", "Antrag stellen");
    }

    @Test
    void roundTripApprovalStep() throws Exception {
        var step = new ApprovalStep("review", Map.of("de", "Freigabe"), "hr-reviewer",
                List.of("approve", "reject"));
        FlowDefinition parsed = mapper.readValue(
                mapper.writeValueAsString(new FlowDefinition(List.of(step))), FlowDefinition.class);
        var approval = (ApprovalStep) parsed.steps().get(0);
        assertThat(approval.key()).isEqualTo("review");
        assertThat(approval.assigneeRole()).isEqualTo("hr-reviewer");
        assertThat(approval.outcomes()).containsExactly("approve", "reject");
    }

    @Test
    void roundTripActionStep() throws Exception {
        var step = new ActionStep("provision", Map.of("de", "Konto anlegen"),
                "provision-ad-account", Map.of("upn", "a@b.ch"));
        FlowDefinition parsed = mapper.readValue(
                mapper.writeValueAsString(new FlowDefinition(List.of(step))), FlowDefinition.class);
        var action = (ActionStep) parsed.steps().get(0);
        assertThat(action.ref()).isEqualTo("provision-ad-account");
        assertThat(action.inputMapping()).containsEntry("upn", "a@b.ch");
    }

    @Test
    void roundTripBranchStep() throws Exception {
        var then = new ActionStep("then_action", Map.of("de", "Aktion"), "ref1", Map.of());
        var step = new BranchStep("b1", Map.of("de", "Verzweigung"),
                "review_outcome", "approve", List.of(then), List.of());
        FlowDefinition parsed = mapper.readValue(
                mapper.writeValueAsString(new FlowDefinition(List.of(step))), FlowDefinition.class);
        var branch = (BranchStep) parsed.steps().get(0);
        assertThat(branch.key()).isEqualTo("b1");
        assertThat(branch.conditionVariable()).isEqualTo("review_outcome");
        assertThat(branch.approveValue()).isEqualTo("approve");
        assertThat(branch.thenSteps()).hasSize(1);
        assertThat(branch.thenSteps().get(0)).isInstanceOf(ActionStep.class);
        assertThat(branch.elseSteps()).isEmpty();
    }

    @Test
    void heterogeneousStepsRoundTrip() throws Exception {
        var def = new FlowDefinition(List.of(
                new FormStep("antrag", Map.of("de", "Antrag")),
                new ApprovalStep("review", Map.of("de", "Review"), "hr-reviewer", List.of("approve", "reject")),
                new ActionStep("ad", Map.of("de", "AD"), "provision-ad-account", Map.of())
        ));
        FlowDefinition parsed = mapper.readValue(mapper.writeValueAsString(def), FlowDefinition.class);
        assertThat(parsed.steps()).hasSize(3);
        assertThat(parsed.steps().get(0)).isInstanceOf(FormStep.class);
        assertThat(parsed.steps().get(1)).isInstanceOf(ApprovalStep.class);
        assertThat(parsed.steps().get(2)).isInstanceOf(ActionStep.class);
    }
}
