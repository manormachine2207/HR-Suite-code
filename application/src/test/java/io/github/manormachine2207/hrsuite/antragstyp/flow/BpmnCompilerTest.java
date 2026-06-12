package io.github.manormachine2207.hrsuite.antragstyp.flow;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BpmnCompilerTest {

    // ---- security: outcome values are interpolated into JUEL + XML ids ----

    @Test
    void approvalOutcomeWithJuelInjectionThrows() {
        var def = new FlowDefinition(List.of(
                new ApprovalStep("review", Map.of("de", "Review"), "hr-reviewer",
                        List.of("approve", "x' || evilBean.run() || '"))));
        assertThatThrownBy(() -> BpmnCompiler.compile("proc_inj", "Inj", def))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outcome");
    }

    @Test
    void approvalOutcomeWithXmlSpecialCharsThrows() {
        var def = new FlowDefinition(List.of(
                new ApprovalStep("review", Map.of("de", "Review"), "hr-reviewer",
                        List.of("approve", "a<b"))));
        assertThatThrownBy(() -> BpmnCompiler.compile("proc_xml", "Xml", def))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outcome");
    }

    @Test
    void actionStepRequiresRef() {
        assertThatThrownBy(() -> new ActionStep("act", Map.of("de", "A"), null, Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ref");
    }

    // ---- gateway default flow: unknown outcome must not approve or wedge ----

    /**
     * The LAST outcome's flow becomes the gateway's default flow (deny-by-default:
     * an outcome value outside the declared list routes to the most conservative,
     * terminal path instead of throwing "no outgoing sequence flow" at complete time
     * or — worse — silently continuing like an approval).
     */
    @Test
    void lastApprovalOutcomeBecomesGatewayDefaultFlow() {
        var def = new FlowDefinition(List.of(
                new ApprovalStep("review", Map.of("de", "Review"), "hr-reviewer",
                        List.of("approve", "reject"))));
        String bpmn = BpmnCompiler.compile("proc_def", "Default", def);

        // gateway declares the reject flow as its default
        assertThat(bpmn).contains("default=\"sf_gw_review_reject\"");
        // the default flow itself is unconditional (self-closing element)
        assertThat(bpmn).contains(
                "<sequenceFlow id=\"sf_gw_review_reject\" sourceRef=\"gw_review\" targetRef=\"end_review_reject\"/>");
        // the continue flow keeps its condition
        assertThat(bpmn).contains("review_outcome == 'approve'");
    }

    @Test
    void singleOutcomeApprovalMakesContinueFlowTheDefault() {
        var def = new FlowDefinition(List.of(
                new ApprovalStep("review", Map.of("de", "Review"), "hr-reviewer",
                        List.of("done"))));
        String bpmn = BpmnCompiler.compile("proc_single", "Single", def);

        assertThat(bpmn).contains("default=\"sf_gw_review_continue\"");
        assertThat(bpmn).contains(
                "<sequenceFlow id=\"sf_gw_review_continue\" sourceRef=\"gw_review\" targetRef=\"end\"/>");
    }

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

    @Test
    void approvalStepProducesUserTaskAndExclusiveGatewayWithConditions() {
        var def = new FlowDefinition(List.of(
                new ApprovalStep("review", Map.of("de", "Freigabe"), "hr-reviewer",
                        List.of("approve", "reject"))));
        String bpmn = BpmnCompiler.compile("proc_appr", "Approval", def);
        // userTask for the reviewer
        assertThat(bpmn).contains("<userTask id=\"review\"");
        assertThat(bpmn).contains("flowable:candidateGroups=\"hr-reviewer\"");
        // exclusive gateway
        assertThat(bpmn).contains("<exclusiveGateway id=\"gw_review\"");
        // reject → terminal end event
        assertThat(bpmn).contains("id=\"end_review_reject\"");
        // approve condition on the continue flow
        assertThat(bpmn).contains("review_outcome == 'approve'");
        // reject is the gateway's default flow (unconditional, deny-by-default)
        assertThat(bpmn).contains("default=\"sf_gw_review_reject\"");
    }

    /**
     * Regression for the defect introduced in commit 43ca371:
     * After an APPROVAL step the exclusive gateway already emits a conditional "continue"
     * flow to the following step.  The following step must NOT also emit an unconditional
     * incoming flow — that would leave the gateway with two outgoing flows to the same
     * target, one of which is unconditional (BPMN semantic defect /
     * exclusive-gateway-seq-flow-without-conditions Flowable warning).
     */
    @Test
    void approvalToActionHasNoUnconditionalGatewayOutflow() {
        var def = new FlowDefinition(List.of(
                new FormStep("antrag", Map.of("de", "Antrag")),
                new ApprovalStep("review", Map.of("de", "Review"), "hr-reviewer",
                        List.of("approve", "reject")),
                new ActionStep("provision", Map.of("de", "Provision"), "provision-ad-account", Map.of())
        ));
        String bpmn = BpmnCompiler.compile("proc_regression", "Regression", def);

        // There must be exactly ONE flow from gw_review to provision (the conditional approve flow).
        int count = 0;
        int idx = 0;
        String needle = "sourceRef=\"gw_review\" targetRef=\"provision\"";
        while ((idx = bpmn.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        assertThat(count)
                .as("Expected exactly one sequenceFlow from gw_review to provision, found %d", count)
                .isEqualTo(1);

        // The single flow from gw_review to provision must carry a conditionExpression —
        // i.e. it must NOT be a self-closing unconditional element.
        // The compiler's sf() method produces for an unconditional flow:
        //   <sequenceFlow id="sf_gw_review_continue" sourceRef="gw_review" targetRef="provision"/>
        // and for a conditional flow the element is NOT self-closing (it contains a child
        // <conditionExpression> element). We assert the self-closing form does not appear.
        assertThat(bpmn)
                .as("No unconditional (self-closing) sequenceFlow from gw_review to provision must exist")
                .doesNotContain("sourceRef=\"gw_review\" targetRef=\"provision\"/>");
    }

    @Test
    void fullFlowFormApprovalActionProducesCorrectBpmn() {
        var def = new FlowDefinition(List.of(
                new FormStep("antrag", Map.of("de", "Antrag")),
                new ApprovalStep("review", Map.of("de", "Review"), "hr-reviewer",
                        List.of("approve", "reject")),
                new ActionStep("ad", Map.of("de", "Konto"), "provision-ad-account",
                        Map.of("upn", "test@example.com"))
        ));
        String bpmn = BpmnCompiler.compile("full_proc", "Full Process", def);
        // All elements present
        assertThat(bpmn).contains("id=\"antrag\"").contains("id=\"review\"").contains("id=\"gw_review\"")
                .contains("id=\"ad\"").contains("id=\"end\"").contains("id=\"end_review_reject\"");
        // Chain: antrag → review
        assertThat(bpmn).contains("sourceRef=\"antrag\"").contains("targetRef=\"review\"");
        // review → gw_review
        assertThat(bpmn).contains("sourceRef=\"review\"").contains("targetRef=\"gw_review\"");
        // gw_review approve → ad (the continue/action step)
        assertThat(bpmn).contains("sourceRef=\"gw_review\"").contains("targetRef=\"ad\"");
        // gw_review reject → end_review_reject
        assertThat(bpmn).contains("targetRef=\"end_review_reject\"");
        // ad → end
        assertThat(bpmn).contains("sourceRef=\"ad\"").contains("targetRef=\"end\"");
    }
}
