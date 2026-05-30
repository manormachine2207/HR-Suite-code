# Cut B — Flow-Definition + BPMN-Compiler Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A typed `FlowDefinition` model (FORM/APPROVAL/ACTION/BRANCH steps) stored on `antragstyp_version`; a pure `BpmnCompiler` that translates it to deployable BPMN; `publish()` compiles if `flowDefinition` is present; the n8n delegate reads an `inputMappingJson` field compiled into ACTION service-tasks.

**Architecture:** New `antragstyp/flow/` package holds the sealed `FlowStep` interface (Jackson polymorphism, 4 implementations) + `FlowDefinition` wrapper + pure `BpmnCompiler`. Migration 008 adds a nullable `flow_definition jsonb` column to `antragstyp_version`. At `publish()` in `AntragsTypService`, if `flowDefinition` is non-null the compiler runs and the result overwrites `workflowBpmn` before Flowable deployment; existing manual-BPMN versions are backward-compatible. `N8nActionDelegate` gains an optional `inputMappingJson` Flowable field so compiled ACTION steps can pass static input to the n8n connector without touching `actionInput` process variables.

**Tech Stack:** Java 21 sealed interfaces, Jackson 2 (`@JsonTypeInfo`/`@JsonSubTypes`), Spring Boot 3.4.13, Flowable 7.1.0, Liquibase, PostgreSQL, Testcontainers (roundtrip IT), Docker Maven runner (no local JDK).

**Branch:** `feat/cut-b-flow-definition-compiler` (already created from `feat/antragstyp-publish-lock-and-phase3`).

**Maven runner (from repo root `/Users/david.berier/Desktop/Git Repos/HR-Suite-code`):**
```bash
docker run --rm -v "$PWD":/work -w /work -v hrsuite-m2:/root/.m2 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -e TESTCONTAINERS_RYUK_DISABLED=true \
  maven:3.9-eclipse-temurin-21 mvn -ntp -pl application -am <goals>
```

**Commit footer (every commit):** `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>`

**Do NOT commit:** `application/src/main/java/io/github/manormachine2207/hrsuite/config/RuntimeDbRoleCheck.java`, `application/src/test/java/io/github/manormachine2207/hrsuite/config/`

---

## File Structure

**New files:**
- `application/src/main/java/io/github/manormachine2207/hrsuite/antragstyp/flow/FlowStep.java` — sealed interface + Jackson type info
- `.../flow/FormStep.java`, `.../flow/ApprovalStep.java`, `.../flow/ActionStep.java`, `.../flow/BranchStep.java` — 4 step implementations
- `.../flow/FlowDefinition.java` — wrapper record `List<FlowStep>`
- `.../flow/BpmnCompiler.java` — pure compiler (no Spring)
- `application/src/main/resources/db/changelog/changes/008-add-flow-definition.sql`
- `application/src/test/java/.../antragstyp/flow/FlowDefinitionSerializationTest.java`
- `application/src/test/java/.../antragstyp/flow/BpmnCompilerTest.java`
- `application/src/test/java/.../antragstyp/flow/BpmnCompilerRoundtripIT.java`

**Modified files:**
- `application/src/main/resources/db/changelog/db.changelog-master.yaml` — append 008 include
- `.../antragstyp/AntragsTypVersion.java` — add `flowDefinition` field + getter + `setFlowDefinition()`
- `.../antragstyp/AntragsTypService.java` — compile in `publish()`
- `.../antragstyp/dto/CreateVersionRequest.java` — add optional `flowDefinition`
- `.../antragstyp/dto/AntragsTypVersionResponse.java` — expose `flowDefinition`
- `.../action/N8nActionDelegate.java` — add optional `inputMappingJson` Expression field

---

## Task 1: FlowStep model — sealed interface + 4 step types + FlowDefinition

**Files:**
- Create: `application/src/main/java/io/github/manormachine2207/hrsuite/antragstyp/flow/FlowStep.java`
- Create: `.../flow/FormStep.java`, `.../flow/ApprovalStep.java`, `.../flow/ActionStep.java`, `.../flow/BranchStep.java`
- Create: `.../flow/FlowDefinition.java`
- Test: `application/src/test/java/io/github/manormachine2207/hrsuite/antragstyp/flow/FlowDefinitionSerializationTest.java`

- [ ] **Step 1: Write the failing serialization test**

`application/src/test/java/io/github/manormachine2207/hrsuite/antragstyp/flow/FlowDefinitionSerializationTest.java`:
```java
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
        assertThat(branch.thenSteps()).hasSize(1);
        assertThat(branch.thenSteps().get(0)).isInstanceOf(ActionStep.class);
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
```

- [ ] **Step 2: Run it to verify it fails**

Run: `... mvn -ntp -pl application -am test -Dtest=FlowDefinitionSerializationTest`
Expected: FAIL — `FormStep` does not exist.

- [ ] **Step 3: Create FlowStep sealed interface**

`application/src/main/java/io/github/manormachine2207/hrsuite/antragstyp/flow/FlowStep.java`:
```java
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
```

- [ ] **Step 4: Create the 4 step records**

`FormStep.java`:
```java
package io.github.manormachine2207.hrsuite.antragstyp.flow;

import java.util.Map;

/** A form-fill step: the applicant sees and fills in the associated {@code formDefinition}. */
public record FormStep(String key, Map<String, String> title) implements FlowStep {
}
```

`ApprovalStep.java`:
```java
package io.github.manormachine2207.hrsuite.antragstyp.flow;

import java.util.List;
import java.util.Map;

/**
 * A human-approval step. {@code assigneeRole} is a Flowable candidate group.
 * {@code outcomes} lists decision values in order; the FIRST outcome is the
 * "continue" path (typically "approve"); remaining outcomes route to terminal end events.
 * Completers set the process variable {@code {key}_outcome} to one of these values.
 */
public record ApprovalStep(
        String key,
        Map<String, String> title,
        String assigneeRole,
        List<String> outcomes) implements FlowStep {

    public ApprovalStep {
        outcomes = (outcomes == null || outcomes.isEmpty())
                ? List.of("approve", "reject") : List.copyOf(outcomes);
    }
}
```

`ActionStep.java`:
```java
package io.github.manormachine2207.hrsuite.antragstyp.flow;

import java.util.Map;

/**
 * An automated action step: calls an n8n workflow via the {@code n8nActionDelegate}
 * Flowable service task. {@code ref} is the n8n webhook ref. {@code inputMapping} is
 * a static key→value map compiled into the BPMN as {@code inputMappingJson} field.
 */
public record ActionStep(
        String key,
        Map<String, String> title,
        String ref,
        Map<String, String> inputMapping) implements FlowStep {

    public ActionStep {
        inputMapping = inputMapping == null ? Map.of() : Map.copyOf(inputMapping);
    }
}
```

`BranchStep.java`:
```java
package io.github.manormachine2207.hrsuite.antragstyp.flow;

import java.util.List;
import java.util.Map;

/**
 * A conditional branch (exclusive gateway). The process variable
 * {@code conditionVariable} is compared to {@code approveValue}:
 * matching → {@code thenSteps}, otherwise → {@code elseSteps}.
 * Both paths join at a merge gateway before continuing.
 *
 * <p><strong>Note (Cut C):</strong> BRANCH compilation in {@link BpmnCompiler}
 * is not yet implemented and throws {@link UnsupportedOperationException}.
 * The model is complete so definitions can be stored and round-tripped.
 */
public record BranchStep(
        String key,
        Map<String, String> title,
        String conditionVariable,
        String approveValue,
        List<FlowStep> thenSteps,
        List<FlowStep> elseSteps) implements FlowStep {

    public BranchStep {
        thenSteps = thenSteps == null ? List.of() : List.copyOf(thenSteps);
        elseSteps = elseSteps == null ? List.of() : List.copyOf(elseSteps);
    }
}
```

`FlowDefinition.java`:
```java
package io.github.manormachine2207.hrsuite.antragstyp.flow;

import java.util.List;

/**
 * Root of a low-code Antragstyp flow definition (DRAFT-ADR-010). An ordered list
 * of {@link FlowStep} that the {@link BpmnCompiler} translates to BPMN at publish time.
 * Stored as {@code flow_definition jsonb} on {@code antragstyp_version} (migration 008).
 */
public record FlowDefinition(List<FlowStep> steps) {
    public FlowDefinition {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
```

- [ ] **Step 5: Run serialization tests to verify they pass**

Run: `... mvn -ntp -pl application -am test -Dtest=FlowDefinitionSerializationTest`
Expected: `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 6: Commit**

```bash
cd "/Users/david.berier/Desktop/Git Repos/HR-Suite-code"
git add application/src/main/java/io/github/manormachine2207/hrsuite/antragstyp/flow/ \
        application/src/test/java/io/github/manormachine2207/hrsuite/antragstyp/flow/FlowDefinitionSerializationTest.java
git commit -m "$(cat <<'EOF'
feat(flow): FlowStep model — sealed interface + 4 step types + FlowDefinition (DRAFT-ADR-010)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Migration 008 + AntragsTypVersion field

**Files:**
- Create: `application/src/main/resources/db/changelog/changes/008-add-flow-definition.sql`
- Modify: `application/src/main/resources/db/changelog/db.changelog-master.yaml`
- Modify: `application/src/main/java/io/github/manormachine2207/hrsuite/antragstyp/AntragsTypVersion.java`

- [ ] **Step 1: Create the migration**

`application/src/main/resources/db/changelog/changes/008-add-flow-definition.sql`:
```sql
--liquibase formatted sql

--changeset hr-suite:008-add-flow-definition
--comment: low-code FlowDefinition on antragstyp_version (DRAFT-ADR-010 Cut B).
-- Nullable: existing versions continue to work (workflowBpmn path unchanged).
-- At publish(), AntragsTypService compiles flow_definition to BPMN if non-null.
ALTER TABLE antragstyp_version
    ADD COLUMN IF NOT EXISTS flow_definition jsonb;
--rollback ALTER TABLE antragstyp_version DROP COLUMN IF EXISTS flow_definition;
```

- [ ] **Step 2: Register in master changelog**

In `application/src/main/resources/db/changelog/db.changelog-master.yaml`, append after the `007-create-action-execution.sql` include:
```yaml
  - include:
      file: db/changelog/changes/008-add-flow-definition.sql
      relativeToChangelogFile: false
```

- [ ] **Step 3: Add `flowDefinition` field to `AntragsTypVersion`**

Read `application/src/main/java/io/github/manormachine2207/hrsuite/antragstyp/AntragsTypVersion.java` first to find the exact location of the `sfActionBindings` field (around line 60–65). Add the new field immediately after `sfActionBindings`:

```java
// Add this import at the top of the file:
import io.github.manormachine2207.hrsuite.antragstyp.flow.FlowDefinition;

// Add this field after sfActionBindings:
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "flow_definition", columnDefinition = "jsonb")
private FlowDefinition flowDefinition;
```

Add the getter and a setter at the end of the existing getters section (after `getSfActionBindings()`):
```java
public FlowDefinition getFlowDefinition() {
    return flowDefinition;
}

public void setFlowDefinition(FlowDefinition flowDefinition) {
    this.flowDefinition = flowDefinition;
}
```

- [ ] **Step 4: Verify it compiles**

Run: `... mvn -ntp -pl application -am test-compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add application/src/main/resources/db/changelog/changes/008-add-flow-definition.sql \
        application/src/main/resources/db/changelog/db.changelog-master.yaml \
        application/src/main/java/io/github/manormachine2207/hrsuite/antragstyp/AntragsTypVersion.java
git commit -m "$(cat <<'EOF'
feat(flow): add flow_definition column to antragstyp_version (migration 008)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: BpmnCompiler — FORM and ACTION steps (TDD)

**Files:**
- Create: `application/src/main/java/io/github/manormachine2207/hrsuite/antragstyp/flow/BpmnCompiler.java`
- Test: `application/src/test/java/io/github/manormachine2207/hrsuite/antragstyp/flow/BpmnCompilerTest.java` (add tests incrementally)

- [ ] **Step 1: Write failing tests for FORM and ACTION compilation**

`application/src/test/java/io/github/manormachine2207/hrsuite/antragstyp/flow/BpmnCompilerTest.java`:
```java
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
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `... mvn -ntp -pl application -am test -Dtest=BpmnCompilerTest`
Expected: FAIL — `BpmnCompiler` does not exist.

- [ ] **Step 3: Implement `BpmnCompiler`**

`application/src/main/java/io/github/manormachine2207/hrsuite/antragstyp/flow/BpmnCompiler.java`:
```java
package io.github.manormachine2207.hrsuite.antragstyp.flow;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Translates a {@link FlowDefinition} to deployable Flowable BPMN 2.0 XML.
 * Pure function — no Spring context, safe to unit-test directly.
 *
 * <p>Supported step types: FORM (userTask), APPROVAL (userTask + exclusive gateway),
 * ACTION (serviceTask → n8nActionDelegate with optional inputMappingJson field).
 * BRANCH is modelled but throws {@link UnsupportedOperationException} (Cut C).
 */
public final class BpmnCompiler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BpmnCompiler() {
    }

    /**
     * Compiles {@code flow} into a deployable BPMN process with id {@code processKey}.
     *
     * @throws IllegalArgumentException      if flow is null or has no steps
     * @throws UnsupportedOperationException if any step is a {@link BranchStep} (Cut C)
     */
    public static String compile(String processKey, String processName, FlowDefinition flow) {
        if (flow == null || flow.steps().isEmpty()) {
            throw new IllegalArgumentException(
                    "FlowDefinition must have at least one step to compile; processKey=" + processKey);
        }
        var elements = new ArrayList<String>();
        var seqFlows = new ArrayList<String>();

        elements.add("<startEvent id=\"start\"/>");

        List<FlowStep> steps = flow.steps();
        for (int i = 0; i < steps.size(); i++) {
            FlowStep step = steps.get(i);
            String from = (i == 0) ? "start" : exitId(steps.get(i - 1));
            String to = (i < steps.size() - 1) ? entryId(steps.get(i + 1)) : "end";
            compileStep(step, from, to, elements, seqFlows);
        }

        elements.add("<endEvent id=\"end\"/>");
        return buildBpmn(processKey, safeName(processName, processKey), elements, seqFlows);
    }

    // --- entry / exit IDs ---

    private static String entryId(FlowStep step) {
        return step.key(); // first BPMN element always uses the step key as ID
    }

    private static String exitId(FlowStep step) {
        // APPROVAL exits via its exclusive gateway; all others exit via the step element itself
        return (step instanceof ApprovalStep) ? "gw_" + step.key() : step.key();
    }

    // --- step compilers ---

    private static void compileStep(FlowStep step, String from, String to,
                                     List<String> elements, List<String> seqFlows) {
        switch (step) {
            case FormStep s     -> compileForm(s, from, to, elements, seqFlows);
            case ApprovalStep s -> compileApproval(s, from, to, elements, seqFlows);
            case ActionStep s   -> compileAction(s, from, to, elements, seqFlows);
            case BranchStep s   -> throw new UnsupportedOperationException(
                    "BRANCH step compilation is not yet supported (scheduled for Cut C): key=" + s.key());
        }
    }

    private static void compileForm(FormStep s, String from, String to,
                                     List<String> elements, List<String> seqFlows) {
        elements.add("""
                <userTask id="%s" name="%s">
                  <documentation>FORM</documentation>
                </userTask>""".formatted(s.key(), lbl(s.title())));
        seqFlows.add(sf("sf_" + from + "_" + s.key(), from, s.key(), null));
        seqFlows.add(sf("sf_" + s.key() + "_" + to, s.key(), to, null));
    }

    private static void compileApproval(ApprovalStep s, String from, String to,
                                         List<String> elements, List<String> seqFlows) {
        String gwId = "gw_" + s.key();
        String role = (s.assigneeRole() == null || s.assigneeRole().isBlank())
                ? "hr-reviewer" : s.assigneeRole();

        // The reviewer's userTask
        elements.add("<userTask id=\"%s\" name=\"%s\" flowable:candidateGroups=\"%s\"/>"
                .formatted(s.key(), lbl(s.title()), esc(role)));
        seqFlows.add(sf("sf_" + from + "_" + s.key(), from, s.key(), null));

        // Exclusive gateway — no 'default' attr; all outgoing flows have conditionExpressions
        elements.add("<exclusiveGateway id=\"%s\" name=\"%s_decision\"/>"
                .formatted(gwId, s.key()));
        seqFlows.add(sf("sf_" + s.key() + "_" + gwId, s.key(), gwId, null));

        // First outcome → continue to next step
        String mainOutcome = s.outcomes().get(0);
        seqFlows.add(sf("sf_" + gwId + "_continue", gwId, to,
                "${%s_outcome == '%s'}".formatted(s.key(), mainOutcome)));

        // Remaining outcomes → dedicated end events (terminal, e.g. reject)
        for (int i = 1; i < s.outcomes().size(); i++) {
            String outcome = s.outcomes().get(i);
            String endId = "end_" + s.key() + "_" + outcome;
            seqFlows.add(sf("sf_" + gwId + "_" + outcome, gwId, endId,
                    "${%s_outcome == '%s'}".formatted(s.key(), outcome)));
            elements.add("<endEvent id=\"" + endId + "\"/>");
        }
    }

    private static void compileAction(ActionStep s, String from, String to,
                                       List<String> elements, List<String> seqFlows) {
        var ext = new StringBuilder();
        ext.append("""
                  <extensionElements>
                    <flowable:field name="ref">
                      <flowable:string>%s</flowable:string>
                    </flowable:field>""".formatted(esc(s.ref())));

        if (!s.inputMapping().isEmpty()) {
            try {
                String json = MAPPER.writeValueAsString(s.inputMapping());
                ext.append("""
                
                    <flowable:field name="inputMappingJson">
                      <flowable:string>%s</flowable:string>
                    </flowable:field>""".formatted(esc(json)));
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Cannot serialize inputMapping for ACTION step: " + s.key(), e);
            }
        }
        ext.append("\n                  </extensionElements>");

        elements.add("""
                <serviceTask id="%s" name="%s"
                             flowable:delegateExpression="${n8nActionDelegate}">
                %s
                </serviceTask>""".formatted(s.key(), lbl(s.title()), ext));
        seqFlows.add(sf("sf_" + from + "_" + s.key(), from, s.key(), null));
        seqFlows.add(sf("sf_" + s.key() + "_" + to, s.key(), to, null));
    }

    // --- BPMN helpers ---

    private static String sf(String id, String from, String to, String condition) {
        if (condition == null) {
            return "<sequenceFlow id=\"%s\" sourceRef=\"%s\" targetRef=\"%s\"/>"
                    .formatted(id, from, to);
        }
        return """
                <sequenceFlow id="%s" sourceRef="%s" targetRef="%s">
                  <conditionExpression xsi:type="tFormalExpression">%s</conditionExpression>
                </sequenceFlow>""".formatted(id, from, to, condition);
    }

    private static String lbl(Map<String, String> title) {
        if (title == null || title.isEmpty()) return "";
        String v = title.getOrDefault("de",
                title.values().stream().findFirst().orElse(""));
        return esc(v);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String safeName(String name, String fallback) {
        return (name == null || name.isBlank()) ? fallback : esc(name);
    }

    private static String buildBpmn(String processKey, String processName,
                                     List<String> elements, List<String> seqFlows) {
        var sb = new StringBuilder();
        sb.append("""
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://hr-suite/processes">
                  <process id="%s" name="%s" isExecutable="true">
                """.formatted(processKey, processName));
        for (String el : elements) {
            sb.append("    ").append(el.strip()).append("\n");
        }
        for (String f : seqFlows) {
            sb.append("    ").append(f.strip()).append("\n");
        }
        sb.append("  </process>\n</definitions>\n");
        return sb.toString();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `... mvn -ntp -pl application -am test -Dtest=BpmnCompilerTest`
Expected: `Tests run: 8, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add application/src/main/java/io/github/manormachine2207/hrsuite/antragstyp/flow/BpmnCompiler.java \
        application/src/test/java/io/github/manormachine2207/hrsuite/antragstyp/flow/BpmnCompilerTest.java
git commit -m "$(cat <<'EOF'
feat(flow): BpmnCompiler — FORM + ACTION + APPROVAL steps + BRANCH stub (Cut C)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Add APPROVAL tests to BpmnCompilerTest (TDD)

The `BpmnCompilerTest` from Task 3 does not yet cover APPROVAL. Add these tests to the existing file, run them (they should pass already if Task 3 was implemented correctly).

**Files:**
- Modify: `application/src/test/java/io/github/manormachine2207/hrsuite/antragstyp/flow/BpmnCompilerTest.java`

- [ ] **Step 1: Add APPROVAL tests to the existing test class**

Add these test methods to `BpmnCompilerTest.java` (inside the class, after the existing tests):
```java
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
    // reject condition on the terminal flow
    assertThat(bpmn).contains("review_outcome == 'reject'");
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
```

- [ ] **Step 2: Run all BpmnCompilerTest tests**

Run: `... mvn -ntp -pl application -am test -Dtest=BpmnCompilerTest`
Expected: `Tests run: 10, Failures: 0` (the 8 from Task 3 plus the 2 new ones).

- [ ] **Step 3: Commit**

```bash
git add application/src/test/java/io/github/manormachine2207/hrsuite/antragstyp/flow/BpmnCompilerTest.java
git commit -m "$(cat <<'EOF'
test(flow): APPROVAL step + full FORM→APPROVAL→ACTION compiler tests

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Integration — `publish()` compiles FlowDefinition; update DTOs; update N8nActionDelegate

**Files:**
- Modify: `application/src/main/java/io/github/manormachine2207/hrsuite/antragstyp/AntragsTypService.java`
- Modify: `application/src/main/java/io/github/manormachine2207/hrsuite/antragstyp/dto/CreateVersionRequest.java`
- Modify: `application/src/main/java/io/github/manormachine2207/hrsuite/antragstyp/dto/AntragsTypVersionResponse.java`
- Modify: `application/src/main/java/io/github/manormachine2207/hrsuite/action/N8nActionDelegate.java`

- [ ] **Step 1: Update `AntragsTypService.publish()` to compile if flowDefinition is set**

Read `AntragsTypService.java` (lines ~133–175) to find the exact `publish()` method. Locate this block (around line 165–172):
```java
DeployedProcess deployed = workflowEngine.deploy(
    at.getTenantId(), processKey, at.getKey(), target.getWorkflowBpmn());
target.recordDeployment(deployed.deploymentId(), deployed.processDefinitionKey(),
        deployed.processDefinitionVersion());
```

Replace it with:
```java
// If a FlowDefinition is present, compile it to BPMN and store the result on the version
// for traceability; otherwise fall back to the stored workflowBpmn (backward compat).
if (target.getFlowDefinition() != null) {
    String compiled = io.github.manormachine2207.hrsuite.antragstyp.flow.BpmnCompiler.compile(
            processKey, at.getKey(), target.getFlowDefinition());
    target.setWorkflowBpmn(compiled);
}
DeployedProcess deployed = workflowEngine.deploy(
        at.getTenantId(), processKey, at.getKey(), target.getWorkflowBpmn());
target.recordDeployment(deployed.deploymentId(), deployed.processDefinitionKey(),
        deployed.processDefinitionVersion());
```

- [ ] **Step 2: Update `CreateVersionRequest` to include optional `flowDefinition`**

Read `application/src/main/java/io/github/manormachine2207/hrsuite/antragstyp/dto/CreateVersionRequest.java`. Replace the entire file with:
```java
package io.github.manormachine2207.hrsuite.antragstyp.dto;

import io.github.manormachine2207.hrsuite.antragstyp.flow.FlowDefinition;
import io.github.manormachine2207.hrsuite.antragstyp.form.FormDefinition;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record CreateVersionRequest(
        @NotNull FormDefinition formDefinition,
        String workflowBpmn,
        Map<String, Object> sfActionBindings,
        FlowDefinition flowDefinition     // optional; if present, compiled to BPMN at publish()
) {
}
```

- [ ] **Step 3: Update `AntragsTypVersionResponse` to include `flowDefinition`**

Read `application/src/main/java/io/github/manormachine2207/hrsuite/antragstyp/dto/AntragsTypVersionResponse.java`. Replace the entire file with:
```java
package io.github.manormachine2207.hrsuite.antragstyp.dto;

import io.github.manormachine2207.hrsuite.antragstyp.AntragsTypVersion;
import io.github.manormachine2207.hrsuite.antragstyp.VersionStatus;
import io.github.manormachine2207.hrsuite.antragstyp.flow.FlowDefinition;
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
        FlowDefinition flowDefinition,
        OffsetDateTime publishedAt,
        UUID publishedBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static AntragsTypVersionResponse from(AntragsTypVersion v) {
        return new AntragsTypVersionResponse(
                v.getId(), v.getAntragstypId(), v.getMajor(), v.getMinor(), v.getStatus(),
                v.getFormDefinition(), v.getWorkflowBpmn(), v.getSfActionBindings(),
                v.getFlowDefinition(),
                v.getPublishedAt(), v.getPublishedBy(), v.getCreatedAt(), v.getUpdatedAt());
    }
}
```

- [ ] **Step 4: Update `AntragsTypService.createDraftMajor()` and `editDraft()` to pass flowDefinition through**

Read `AntragsTypService.java` around lines 94–110. The existing signatures:
```java
public AntragsTypVersion createDraftMajor(UUID antragstypId, FormDefinition formDefinition,
                                          String workflowBpmn, Map<String, Object> sfActionBindings)
public AntragsTypVersion editDraft(UUID versionId, FormDefinition formDefinition,
                                   String workflowBpmn, Map<String, Object> sfActionBindings)
```

Update both to accept an additional optional `FlowDefinition flowDefinition` parameter and call `version.setFlowDefinition(flowDefinition)` after creating/updating the version. The controller calls need updating too — add a `null` default to avoid breaking existing callers if needed (or update the controller to pass `req.flowDefinition()`).

Specifically, find `createDraftMajor` and add the parameter:
```java
public AntragsTypVersion createDraftMajor(UUID antragstypId, FormDefinition formDefinition,
                                          String workflowBpmn, Map<String, Object> sfActionBindings,
                                          FlowDefinition flowDefinition) {
    // ... existing code to create the AntragsTypVersion v ...
    v.setFlowDefinition(flowDefinition);  // add this line after v is created
    return versionRepository.save(v);
}
```

For `editDraft`:
```java
public AntragsTypVersion editDraft(UUID versionId, FormDefinition formDefinition,
                                   String workflowBpmn, Map<String, Object> sfActionBindings,
                                   FlowDefinition flowDefinition) {
    // ... existing code ...
    v.replaceDraftContent(formDefinition, workflowBpmn, sfActionBindings);
    v.setFlowDefinition(flowDefinition);  // add this line
    return v;
}
```

Then update the controller `AntragsTypController` to pass `req.flowDefinition()` in the calls to `createDraftMajor` and `editDraft`. Read `AntragsTypController.java` to find the exact lines.

- [ ] **Step 5: Update `N8nActionDelegate` to support `inputMappingJson` field**

Read `application/src/main/java/io/github/manormachine2207/hrsuite/action/N8nActionDelegate.java`. Add an optional `Expression inputMappingJson` field and a setter (after the existing `ref` field and setter), and update `execute()` to use it as fallback for `actionInput`:

```java
// Add field after "private Expression ref;":
private Expression inputMappingJson;  // optional; set by compiled BPMN ACTION steps

// Add setter after "public void setRef(Expression ref)":
public void setInputMappingJson(Expression inputMappingJson) {
    this.inputMappingJson = inputMappingJson;
}
```

In `execute()`, replace:
```java
Object raw = execution.getVariable("actionInput");
Map<String, Object> input = raw instanceof Map ? (Map<String, Object>) raw : Map.of();
```
with:
```java
Object raw = execution.getVariable("actionInput");
// Fall back to the compiled inputMappingJson field when actionInput is not set as a process variable
if (raw == null && inputMappingJson != null) {
    String json = (String) inputMappingJson.getValue(execution);
    if (json != null && !json.isBlank()) {
        try {
            raw = new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, java.util.Map.class);
        } catch (Exception e) {
            // log warning, proceed with empty map; malformed JSON in BPMN is a deploy-time issue
            org.slf4j.LoggerFactory.getLogger(N8nActionDelegate.class)
                    .warn("Could not parse inputMappingJson for step {}: {}", stepKey, e.getMessage());
        }
    }
}
@SuppressWarnings("unchecked")
Map<String, Object> input = raw instanceof Map ? (Map<String, Object>) raw : Map.of();
```

(Remove the `@SuppressWarnings("unchecked")` annotation that was previously on the method and place it here on the local variable declaration instead.)

Also update `N8nActionDelegateTest.java`: add a test for the inputMappingJson field path:
```java
@Test
void inputMappingJsonFieldIsUsedWhenActionInputVariableAbsent() {
    stubExecution();
    when(execution.getVariable("actionInput")).thenReturn(null);   // no process variable
    Expression mappingExpr = mock(Expression.class);
    when(mappingExpr.getValue(execution)).thenReturn("{\"upn\":\"a@b.ch\"}");

    ActionExecution succeeded = new ActionExecution(TENANT, "pi-1", "ad", "provision-ad-account");
    succeeded.markSucceeded();
    when(service.run(eq("pi-1"), eq("ad"), eq("provision-ad-account"), any())).thenReturn(succeeded);

    N8nActionDelegate d = delegate();
    d.setInputMappingJson(mappingExpr);
    d.execute(execution);

    verify(execution).setVariable("actionStatus", "SUCCEEDED");
}
```

- [ ] **Step 6: Verify the full unit suite compiles and passes**

Run: `... mvn -ntp -pl application -am test -Dtest='BpmnCompilerTest,FlowDefinitionSerializationTest,AntragsTypServiceTest,N8nActionDelegateTest'`
Expected: all green. If `AntragsTypServiceTest` fails (because the service constructor changed), update it to pass `null` for the new `flowDefinition` parameter.

- [ ] **Step 7: Commit**

```bash
git add application/src/main/java/io/github/manormachine2207/hrsuite/antragstyp/AntragsTypService.java \
        application/src/main/java/io/github/manormachine2207/hrsuite/antragstyp/dto/CreateVersionRequest.java \
        application/src/main/java/io/github/manormachine2207/hrsuite/antragstyp/dto/AntragsTypVersionResponse.java \
        application/src/main/java/io/github/manormachine2207/hrsuite/action/N8nActionDelegate.java \
        application/src/test/java/io/github/manormachine2207/hrsuite/action/N8nActionDelegateTest.java
git commit -m "$(cat <<'EOF'
feat(flow): compile FlowDefinition at publish; expose via API; N8nActionDelegate inputMappingJson

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Roundtrip IT — compiled BPMN deploys to Flowable

Proves: a `FlowDefinition` with FORM→APPROVAL→ACTION compiles to valid BPMN that Flowable accepts (deploy + start), the process reaches the reviewer userTask, and an ACTION service-task actually calls the n8n stub. Runs as `hrsuite_app` (RLS scharf).

**Files:**
- Create: `application/src/test/java/io/github/manormachine2207/hrsuite/antragstyp/flow/BpmnCompilerRoundtripIT.java`

- [ ] **Step 1: Write the roundtrip IT**

`application/src/test/java/io/github/manormachine2207/hrsuite/antragstyp/flow/BpmnCompilerRoundtripIT.java`:
```java
package io.github.manormachine2207.hrsuite.antragstyp.flow;

import com.sun.net.httpserver.HttpServer;
import io.github.manormachine2207.hrsuite.HrSuiteApplication;
import io.github.manormachine2207.hrsuite.antragstyp.AntragsTypService;
import io.github.manormachine2207.hrsuite.antragstyp.VersionStatus;
import io.github.manormachine2207.hrsuite.antragstyp.form.FormDefinition;
import io.github.manormachine2207.hrsuite.antragstyp.form.FormField;
import io.github.manormachine2207.hrsuite.antragstyp.form.FieldType;
import io.github.manormachine2207.hrsuite.shared.tenant.TenantContext;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that a FlowDefinition (FORM→APPROVAL→ACTION) compiles to deployable BPMN:
 * the compiled process starts in Flowable, the first userTask (reviewer) is created,
 * and when the reviewer completes with outcome "approve" the ACTION service-task
 * fires the n8n stub.
 */
@SpringBootTest(classes = HrSuiteApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@Testcontainers
class BpmnCompilerRoundtripIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withInitScript("db/rls-it-init.sql");

    static HttpServer n8nStub;
    static int stubPort;
    static final AtomicInteger stubCalls = new AtomicInteger(0);

    @BeforeAll
    static void startStub() throws IOException {
        n8nStub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        n8nStub.createContext("/webhook/", exchange -> {
            stubCalls.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            byte[] out = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        n8nStub.start();
        stubPort = n8nStub.getAddress().getPort();
    }

    @AfterAll
    static void stopStub() {
        n8nStub.stop(0);
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", () -> "hrsuite_app");
        r.add("spring.datasource.password", () -> "dev");
    }

    @Autowired TestRestTemplate rest;
    @Autowired RuntimeService runtimeService;
    @Autowired TaskService taskService;

    private HttpHeaders designerToken(String tenantId) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth("dev-hr-designer~" + tenantId);
        return h;
    }

    @Test
    void compiledFlowDeploysAndRunsFormApprovalAction() throws Exception {
        // 1. Create tenant
        String tenantId = rest.exchange("/api/v1/tenant", HttpMethod.POST,
                new HttpEntity<>("{\"code\":\"BPMNIT\",\"subdomain\":\"bpmnit\","
                        + "\"displayName\":{\"de\":\"BPMN IT\"}}", adminToken()), String.class)
                .getBody();
        tenantId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(tenantId).get("id").asText();

        // 2. Seed n8n config (so ACTION step can call the stub)
        final String tid = tenantId;
        seedN8nConfig(tid, "http://127.0.0.1:" + stubPort);

        // 3. Create antragstyp
        HttpHeaders h = designerToken(tenantId);
        String atId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                rest.exchange("/api/v1/antragstyp", HttpMethod.POST,
                        new HttpEntity<>("{\"key\":\"urlaubsantrag\",\"title\":{\"de\":\"Urlaub\"}}", h),
                        String.class).getBody()).get("id").asText();

        // 4. Create version WITH flowDefinition (FORM→APPROVAL→ACTION)
        String versionBody = """
                {
                  "formDefinition": {"fields": [{"key":"grund","type":"TEXT","required":true,
                    "label":{"de":"Grund"}}]},
                  "workflowBpmn": "<bpmn/>",
                  "sfActionBindings": {},
                  "flowDefinition": {
                    "steps": [
                      {"kind":"FORM","key":"antrag","title":{"de":"Antrag stellen"}},
                      {"kind":"APPROVAL","key":"review","title":{"de":"Freigabe"},
                       "assigneeRole":"hr-reviewer","outcomes":["approve","reject"]},
                      {"kind":"ACTION","key":"provision","title":{"de":"Konto anlegen"},
                       "ref":"provision-ad-account","inputMapping":{"upn":"test@example.com"}}
                    ]
                  }
                }
                """;
        String versionId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                rest.exchange("/api/v1/antragstyp/" + atId + "/versions", HttpMethod.POST,
                        new HttpEntity<>(versionBody, h), String.class).getBody()).get("id").asText();

        // 5. Publish → should compile FlowDefinition to BPMN and deploy to Flowable
        var publishResp = rest.exchange("/api/v1/antragstyp/versions/" + versionId + "/publish",
                HttpMethod.POST, new HttpEntity<>(h), String.class);
        assertThat(publishResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify the stored workflowBpmn is now the compiled BPMN (not the placeholder)
        String versJson = rest.exchange("/api/v1/antragstyp/" + atId + "/versions",
                HttpMethod.GET, new HttpEntity<>(h), String.class).getBody();
        String storedBpmn = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(versJson).get(0).get("workflowBpmn").asText();
        assertThat(storedBpmn).contains("<process").contains("antrag").contains("review").contains("provision");

        // 6. Start a process instance in tenant context
        TenantContext.set(UUID.fromString(tenantId));
        String procKey = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                rest.exchange("/api/v1/antragstyp/" + atId + "/versions", HttpMethod.GET,
                        new HttpEntity<>(h), String.class).getBody())
                .get(0).get("processDefinitionKey").asText();

        int callsBefore = stubCalls.get();
        runtimeService.createProcessInstanceBuilder()
                .processDefinitionKey(procKey)
                .tenantId(tenantId)
                .variable("antrag_grund", "Urlaub")
                .start();

        // 7. First task should be the reviewer userTask ("review")
        var tasks = taskService.createTaskQuery()
                .processDefinitionKey(procKey)
                .taskDefinitionKey("review")
                .list();
        assertThat(tasks).hasSize(1);

        // 8. Complete the reviewer task with approve → triggers ACTION service-task
        stubCalls.set(0);
        taskService.setVariable(tasks.get(0).getId(), "review_outcome", "approve");
        taskService.complete(tasks.get(0).getId());

        // 9. ACTION service-task should have called the n8n stub exactly once
        assertThat(stubCalls.get()).isEqualTo(1);

        TenantContext.clear();
    }

    private HttpHeaders adminToken() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth("dev-platform-admin");
        return h;
    }

    private void seedN8nConfig(String tenantId, String baseUrl) {
        // Insert via psql using the admin (superuser) connection to bypass RLS for seeding
        // This mirrors how scripts/dev-seed.sh seeds it; in the IT we use JDBC directly.
        // We borrow the existing ActionItHarness pattern if available, or just use the
        // @Autowired TenantN8nConfigRepository via the test helper.
        // NOTE for the executor: the cleanest approach is to @Autowired
        // io.github.manormachine2207.hrsuite.action.TenantN8nConfigRepository
        // and call configRepo.save(new TenantN8nConfig(uuid, baseUrl, "test-secret",
        //   List.of("provision-ad-account"))) inside inTenant(tenantId, ...).
        // Mirror ActionItHarness.seedConfig() from N8nActionConnectorIT exactly.
        // This note is deliberately left for the executor to fill in based on what they see
        // in ActionItHarness rather than duplicating code that may drift.
        throw new UnsupportedOperationException(
                "Executor: implement seedN8nConfig by injecting TenantN8nConfigRepository "
                + "and calling save inside a tenant-scoped transaction (mirror ActionItHarness). "
                + "Remove this throw once implemented.");
    }
}
```

> **Note for the executor:** `seedN8nConfig` is the only placeholder. Replace it by `@Autowired`-ing the `TenantN8nConfigRepository` and `ActionItHarness` (or its `seedConfig` method) that already exist in `N8nActionConnectorIT` from Cut A. The rest of the IT is complete. Also update the `AntragsTypVersionResponse` API (check step `/api/v1/antragstyp/{id}/versions` returns the `processDefinitionKey` field — if that field is not yet in the DTO, add it or use `workflowBpmn` to confirm compilation).

- [ ] **Step 2: Run the IT red first (expected: fails on seedN8nConfig placeholder)**

Run:
```
... mvn -ntp -pl application -am verify -Dit.test=BpmnCompilerRoundtripIT -Dfailsafe.failIfNoSpecifiedTests=false
```
Expected: FAIL with `UnsupportedOperationException: Executor: implement seedN8nConfig...`

- [ ] **Step 3: Implement `seedN8nConfig` using the existing Cut-A infrastructure**

Look at `N8nActionConnectorIT.java` for how `ActionItHarness.seedConfig(tenantId, baseUrl, ...)` works. Inject `ActionItHarness` via `@Autowired` (it's a test-only `@Service` in the test sources). Call `harness.seedConfig(UUID.fromString(tenantId), baseUrl, "test-secret", List.of("provision-ad-account"))` inside a `inTenant(tenantId, ...)` call (or however `N8nActionConnectorIT` does it). Remove the throw.

- [ ] **Step 4: Run the IT green**

Run: `... mvn -ntp -pl application -am verify -Dit.test=BpmnCompilerRoundtripIT -Dfailsafe.failIfNoSpecifiedTests=false`
Expected: `Tests run: 1, Failures: 0` — the compiled BPMN deploys, the process starts, the review task is created, approval drives the ACTION step, and the n8n stub is called exactly once.

- [ ] **Step 5: Commit**

```bash
git add application/src/test/java/io/github/manormachine2207/hrsuite/antragstyp/flow/BpmnCompilerRoundtripIT.java
git commit -m "$(cat <<'EOF'
test(flow): roundtrip IT — compiled BPMN deploys and runs FORM→APPROVAL→ACTION in Flowable

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Full verify gate

**Files:** none new — gate.

- [ ] **Step 1: Run full verify**

Run:
```
docker run --rm -v "$PWD":/work -w /work -v hrsuite-m2:/root/.m2 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -e TESTCONTAINERS_RYUK_DISABLED=true \
  maven:3.9-eclipse-temurin-21 mvn -ntp -pl application -am verify
```
Expected: BUILD SUCCESS — all unit tests + all ITs green including `BpmnCompilerRoundtripIT`, `N8nActionConnectorIT`, `AntragsTypRlsIT`, `AntragRlsIT`, `AntragsTypPublishConcurrencyIT`, `WorkflowEngineIT`, `ModularityTests`.

- [ ] **Step 2: Confirm no module-boundary violations**

`ModularityTests` should pass cleanly. The `flow` package is part of the `antragstyp` module (same Modulith module, no cross-boundary issue). `BpmnCompiler` references `FlowStep` (same package) and Jackson (external library, always allowed). `AntragsTypService` references `BpmnCompiler` (same module). All fine.

- [ ] **Step 3: Commit if any fixups were needed**

```bash
git add -A application/
git commit -m "$(cat <<'EOF'
test(flow): full verify green for Cut B (flow-definition + BPMN compiler)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Self-Review (completed by plan author)

**Spec coverage:**
- FlowDefinition model (FORM/APPROVAL/ACTION/BRANCH sealed) ✓ Task 1
- Migration 008 `flow_definition` column ✓ Task 2
- `AntragsTypVersion.flowDefinition` field ✓ Task 2
- `BpmnCompiler.compile()` FORM+ACTION+APPROVAL ✓ Task 3+4; BRANCH stub ✓ Task 3
- `publish()` compiles if flowDefinition set ✓ Task 5
- `CreateVersionRequest` + `AntragsTypVersionResponse` include flowDefinition ✓ Task 5
- `N8nActionDelegate.inputMappingJson` field ✓ Task 5
- Roundtrip IT ✓ Task 6
- Full verify gate ✓ Task 7

**Placeholder scan:** Task 6 `seedN8nConfig` is explicitly flagged as requiring executor implementation (reuse from Cut A's `ActionItHarness`). This is the only one; it's a known, bounded gap (not a silent TBD).

**Type consistency:**
- `FlowStep.key()` is the element ID throughout compiler — consistent.
- `exitId(ApprovalStep)` = `"gw_" + key` — used consistently in `compileApproval` and `exitId()`.
- `"sf_gw_{key}_continue"` is the approve path flow ID (fixed string, not dynamic) — consistent.
- `ActionStep.ref()` and `ActionStep.inputMapping()` match in compiler and delegate field names.
- `AntragsTypVersionResponse.from()` call updated to include `v.getFlowDefinition()` ✓.
