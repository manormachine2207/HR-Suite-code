package io.github.manormachine2207.hrsuite.antragstyp.flow;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BpmnCompilerTest {

    @Test
    void singleFormStepProducesUserTaskWithStartAndEnd() {
        var def = new FlowDefinition(List.of(
                new FormStep("antrag", Map.of("de", "Antrag stellen"))));
        String bpmn = BpmnCompiler.compile("proc_1", "Test Process", def);
        assertThat(bpmn).contains("<process id=\"proc_1\"");
        assertThat(bpmn).contains("<startEvent id=\"start\"");
        assertThat(bpmn).contains("<userTask id=\"antrag\"");
        assertThat(bpmn).contains("<documentation>FORM</documentation>");
        assertThat(bpmn).contains("<endEvent id=\"end\"");
        // must be a deployable BPMN (contains <process)
        assertThat(bpmn).contains("<process");
    }

    @Test
    void singleActionStepProducesServiceTaskWithDelegateAndRef() {
        var def = new FlowDefinition(List.of(
                new ActionStep("provision", Map.of("de", "AD-Konto"), "provision-ad-account", Map.of())));
        String bpmn = BpmnCompiler.compile("proc_2", "Action Process", def);
        assertThat(bpmn).contains("<serviceTask id=\"provision\"");
        assertThat(bpmn).contains("flowable:delegateExpression=\"${n8nActionDelegate}\"");
        assertThat(bpmn).contains("<flowable:string>provision-ad-account</flowable:string>");
    }

    @Test
    void actionStepWithInputMappingEmbedsJsonField() {
        var def = new FlowDefinition(List.of(
                new ActionStep("provision", Map.of("de", "AD"),
                        "provision-ad-account", Map.of("upn", "john@example.com"))));
        String bpmn = BpmnCompiler.compile("proc_3", "Action with input", def);
        assertThat(bpmn).contains("inputMappingJson");
        assertThat(bpmn).contains("john@example.com");
    }

    @Test
    void multipleStepsAreChainedCorrectly() {
        var def = new FlowDefinition(List.of(
                new FormStep("antrag", Map.of("de", "Antrag")),
                new ActionStep("provision", Map.of("de", "Provision"), "provision-ad-account", Map.of())
        ));
        String bpmn = BpmnCompiler.compile("proc_4", "Multi", def);
        // Both elements present
        assertThat(bpmn).contains("id=\"antrag\"");
        assertThat(bpmn).contains("id=\"provision\"");
        // antrag → provision must have a sequence flow
        assertThat(bpmn).contains("sourceRef=\"antrag\"").contains("targetRef=\"provision\"");
    }

    @Test
    void emptyFlowDefinitionThrows() {
        assertThatThrownBy(() -> BpmnCompiler.compile("k", "n", new FlowDefinition(List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullFlowDefinitionThrows() {
        assertThatThrownBy(() -> BpmnCompiler.compile("k", "n", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void branchStepThrowsUnsupportedOperationWithCutCMessage() {
        var def = new FlowDefinition(List.of(
                new BranchStep("b1", Map.of("de", "Branch"), "outcome", "approve",
                        List.of(), List.of())));
        assertThatThrownBy(() -> BpmnCompiler.compile("k", "n", def))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Cut C");
    }

    @Test
    void specialCharsInNameAreXmlEscaped() {
        var def = new FlowDefinition(List.of(
                new FormStep("antrag", Map.of("de", "Antrag & <Test>"))));
        String bpmn = BpmnCompiler.compile("k", "name", def);
        assertThat(bpmn).doesNotContain("Antrag & <Test>");
        assertThat(bpmn).contains("Antrag &amp; &lt;Test&gt;");
    }

    @Test
    void hyphenatedStepKeyThrows() {
        var def = new FlowDefinition(List.of(
                new FormStep("provision-ad", Map.of("de", "x"))));
        assertThatThrownBy(() -> BpmnCompiler.compile("proc", "n", def))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidProcessKeyThrows() {
        var def = new FlowDefinition(List.of(
                new FormStep("antrag", Map.of("de", "Antrag"))));
        assertThatThrownBy(() -> BpmnCompiler.compile("bad key", "n", def))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
