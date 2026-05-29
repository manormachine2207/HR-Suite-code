package io.github.manormachine2207.hrsuite.antragstyp.version;

import io.github.manormachine2207.hrsuite.antragstyp.form.FieldType;
import io.github.manormachine2207.hrsuite.antragstyp.form.FormDefinition;
import io.github.manormachine2207.hrsuite.antragstyp.form.FormField;
import io.github.manormachine2207.hrsuite.antragstyp.form.Option;
import io.github.manormachine2207.hrsuite.antragstyp.form.Validation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CompatibilityClassifierTest {

    private static final String BPMN = "<bpmn>v1</bpmn>";
    private static final Map<String, Object> BIND = Map.of("task1", "sf.createLeave");

    private final CompatibilityClassifier classifier = new CompatibilityClassifier();

    // ---- helpers ----------------------------------------------------------
    private static FormField field(String key, FieldType type, boolean required,
                                   Map<String, String> label, Validation v,
                                   List<Option> opts, String def) {
        return new FormField(key, type, required, label, Map.of(), v, opts, def);
    }

    private static FormField text(String key, boolean required, Integer maxLen) {
        return field(key, FieldType.TEXT, required,
                Map.of("de", "Label"), maxLen == null ? null : new Validation(maxLen, null, null),
                List.of(), null);
    }

    private static FormDefinition def(FormField... fields) {
        return new FormDefinition(List.of(fields));
    }

    private ChangeClassification classifyForm(FormDefinition oldF, FormDefinition newF) {
        return classifier.classify(oldF, newF, BPMN, BPMN, BIND, BIND);
    }

    // ---- baseline ---------------------------------------------------------
    @Test
    void identicalDefinitionIsMinor() {
        var d = def(text("a", true, 100));
        assertThat(classifyForm(d, d).changeClass()).isEqualTo(ChangeClass.MINOR);
    }

    // ===== MINOR rows ======================================================
    @Test
    void changingI18nLabelIsMinor() {
        var oldD = def(field("a", FieldType.TEXT, true, Map.of("de", "Alt"), null, List.of(), null));
        var newD = def(field("a", FieldType.TEXT, true, Map.of("de", "Neu"), null, List.of(), null));
        assertThat(classifyForm(oldD, newD).changeClass()).isEqualTo(ChangeClass.MINOR);
    }

    @Test
    void changingHelpTextIsMinor() {
        var oldD = def(new FormField("a", FieldType.TEXT, true, Map.of("de", "L"),
                Map.of("de", "alt"), null, List.of(), null));
        var newD = def(new FormField("a", FieldType.TEXT, true, Map.of("de", "L"),
                Map.of("de", "neu"), null, List.of(), null));
        assertThat(classifyForm(oldD, newD).changeClass()).isEqualTo(ChangeClass.MINOR);
    }

    @Test
    void reorderingFieldsIsMinor() {
        var a = text("a", true, 100);
        var b = text("b", true, 100);
        assertThat(classifyForm(def(a, b), def(b, a)).changeClass()).isEqualTo(ChangeClass.MINOR);
    }

    @Test
    void addingOptionalFieldIsMinor() {
        var oldD = def(text("a", true, 100));
        var newD = def(text("a", true, 100), text("b", false, 100));
        assertThat(classifyForm(oldD, newD).changeClass()).isEqualTo(ChangeClass.MINOR);
    }

    @Test
    void looseningMaxLengthIsMinor() {
        assertThat(classifyForm(def(text("a", true, 100)), def(text("a", true, 200)))
                .changeClass()).isEqualTo(ChangeClass.MINOR);
    }

    @Test
    void requiredToOptionalIsMinor() {
        assertThat(classifyForm(def(text("a", true, 100)), def(text("a", false, 100)))
                .changeClass()).isEqualTo(ChangeClass.MINOR);
    }

    @Test
    void wideningNumericRangeIsMinor() {
        var oldD = def(field("n", FieldType.NUMBER, true, Map.of("de", "N"),
                new Validation(null, 10, 20), List.of(), null));
        var newD = def(field("n", FieldType.NUMBER, true, Map.of("de", "N"),
                new Validation(null, 0, 30), List.of(), null));
        assertThat(classifyForm(oldD, newD).changeClass()).isEqualTo(ChangeClass.MINOR);
    }

    @Test
    void changingSelectOptionLabelKeepingValueIsMinor() {
        var oldD = def(field("s", FieldType.SELECT, true, Map.of("de", "S"), null,
                List.of(new Option("v1", Map.of("de", "Alt"))), null));
        var newD = def(field("s", FieldType.SELECT, true, Map.of("de", "S"), null,
                List.of(new Option("v1", Map.of("de", "Neu"))), null));
        assertThat(classifyForm(oldD, newD).changeClass()).isEqualTo(ChangeClass.MINOR);
    }

    @Test
    void changingDefaultValueIsMinor() {
        var oldD = def(field("a", FieldType.TEXT, true, Map.of("de", "L"), null, List.of(), "x"));
        var newD = def(field("a", FieldType.TEXT, true, Map.of("de", "L"), null, List.of(), "y"));
        assertThat(classifyForm(oldD, newD).changeClass()).isEqualTo(ChangeClass.MINOR);
    }

    // ===== MAJOR rows ======================================================
    @Test
    void removingFieldIsMajor() {
        var oldD = def(text("a", true, 100), text("b", true, 100));
        var newD = def(text("a", true, 100));
        var r = classifyForm(oldD, newD);
        assertThat(r.isMajor()).isTrue();
        assertThat(r.reasons()).anyMatch(s -> s.contains("b"));
    }

    @Test
    void renamingFieldKeyIsMajor() {
        // rename = remove old key (+ add new); remove branch dominates -> MAJOR
        var oldD = def(text("alt", false, 100));
        var newD = def(text("neu", false, 100));
        assertThat(classifyForm(oldD, newD).isMajor()).isTrue();
    }

    @Test
    void changingFieldTypeIsMajor() {
        var oldD = def(field("a", FieldType.TEXT, true, Map.of("de", "L"), null, List.of(), null));
        var newD = def(field("a", FieldType.DATE, true, Map.of("de", "L"), null, List.of(), null));
        assertThat(classifyForm(oldD, newD).isMajor()).isTrue();
    }

    @Test
    void addingRequiredFieldIsMajor() {
        var oldD = def(text("a", true, 100));
        var newD = def(text("a", true, 100), text("b", true, 100));
        assertThat(classifyForm(oldD, newD).isMajor()).isTrue();
    }

    @Test
    void tighteningMaxLengthIsMajor() {
        assertThat(classifyForm(def(text("a", true, 200)), def(text("a", true, 100)))
                .isMajor()).isTrue();
    }

    @Test
    void optionalToRequiredIsMajor() {
        assertThat(classifyForm(def(text("a", false, 100)), def(text("a", true, 100)))
                .isMajor()).isTrue();
    }

    @Test
    void narrowingNumericRangeIsMajor() {
        var oldD = def(field("n", FieldType.NUMBER, true, Map.of("de", "N"),
                new Validation(null, 0, 30), List.of(), null));
        var newD = def(field("n", FieldType.NUMBER, true, Map.of("de", "N"),
                new Validation(null, 10, 20), List.of(), null));
        assertThat(classifyForm(oldD, newD).isMajor()).isTrue();
    }

    @Test
    void changingSelectOptionValueIsMajor() {
        var oldD = def(field("s", FieldType.SELECT, true, Map.of("de", "S"), null,
                List.of(new Option("v1", Map.of("de", "L"))), null));
        var newD = def(field("s", FieldType.SELECT, true, Map.of("de", "S"), null,
                List.of(new Option("v2", Map.of("de", "L"))), null));
        assertThat(classifyForm(oldD, newD).isMajor()).isTrue();
    }

    @Test
    void removingSelectOptionValueIsMajor() {
        var oldD = def(field("s", FieldType.SELECT, true, Map.of("de", "S"), null,
                List.of(new Option("v1", Map.of("de", "A")), new Option("v2", Map.of("de", "B"))), null));
        var newD = def(field("s", FieldType.SELECT, true, Map.of("de", "S"), null,
                List.of(new Option("v1", Map.of("de", "A"))), null));
        assertThat(classifyForm(oldD, newD).isMajor()).isTrue();
    }

    @Test
    void changingWorkflowBpmnIsMajor() {
        var d = def(text("a", true, 100));
        var r = classifier.classify(d, d, "<bpmn>v1</bpmn>", "<bpmn>v2</bpmn>", BIND, BIND);
        assertThat(r.isMajor()).isTrue();
        assertThat(r.reasons()).anyMatch(s -> s.contains("BPMN"));
    }

    @Test
    void changingSfActionBindingsIsMajor() {
        var d = def(text("a", true, 100));
        var r = classifier.classify(d, d, BPMN, BPMN,
                Map.of("task1", "sf.createLeave"), Map.of("task1", "sf.createAbsence"));
        assertThat(r.isMajor()).isTrue();
        assertThat(r.reasons()).anyMatch(s -> s.contains("sf_action_bindings"));
    }
}
