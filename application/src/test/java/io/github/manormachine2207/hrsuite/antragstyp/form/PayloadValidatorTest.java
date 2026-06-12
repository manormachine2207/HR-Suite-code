package io.github.manormachine2207.hrsuite.antragstyp.form;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Serverseitige Payload-Validierung gegen die gepinnte FormDefinition (Review
 * 2026-06-12; "validieren an Systemgrenzen", ADR-009 §4 — der Pin-Moment ist die
 * Grenze). Bisher akzeptierte submit() jeden Payload unbesehen.
 */
class PayloadValidatorTest {

    private static final Map<String, String> L = Map.of("de", "D", "fr", "F", "it", "I", "en", "E");

    private static FormField text(String key, boolean required, Integer maxLen) {
        return new FormField(key, FieldType.TEXT, required,
                L, null, maxLen == null ? null : new Validation(maxLen, null, null), List.of(), null);
    }

    private static FormDefinition def(FormField... f) {
        return new FormDefinition(List.of(f));
    }

    @Test
    void validPayloadPasses() {
        var errors = PayloadValidator.errors(def(text("grund", true, 50)), Map.of("grund", "Umzug"));
        assertThat(errors).isEmpty();
    }

    @Test
    void missingRequiredFieldIsReported() {
        var errors = PayloadValidator.errors(def(text("grund", true, null)), Map.of());
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("grund").contains("required");
    }

    @Test
    void blankRequiredStringIsReported() {
        var errors = PayloadValidator.errors(def(text("grund", true, null)), Map.of("grund", "  "));
        assertThat(errors).hasSize(1);
    }

    @Test
    void unknownKeyIsReported() {
        var errors = PayloadValidator.errors(def(text("grund", false, null)), Map.of("hack", "x"));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("hack").contains("unknown");
    }

    @Test
    void maxLengthIsEnforced() {
        var errors = PayloadValidator.errors(def(text("grund", false, 3)), Map.of("grund", "zu lang"));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("maxLength");
    }

    @Test
    void numberRangeIsEnforced() {
        var f = new FormField("tage", FieldType.NUMBER, false, L, null,
                new Validation(null, 1, 10), List.of(), null);
        assertThat(PayloadValidator.errors(def(f), Map.of("tage", 5))).isEmpty();
        assertThat(PayloadValidator.errors(def(f), Map.of("tage", 11))).hasSize(1);
        assertThat(PayloadValidator.errors(def(f), Map.of("tage", "keine zahl"))).hasSize(1);
    }

    @Test
    void dateMustBeIsoParseable() {
        var f = new FormField("ab", FieldType.DATE, false, L, null, null, List.of(), null);
        assertThat(PayloadValidator.errors(def(f), Map.of("ab", "2026-07-01"))).isEmpty();
        assertThat(PayloadValidator.errors(def(f), Map.of("ab", "morgen"))).hasSize(1);
    }

    @Test
    void selectValueMustBeAnOption() {
        var f = new FormField("art", FieldType.SELECT, false, L, null, null,
                List.of(new Option("a", L), new Option("b", L)), null);
        assertThat(PayloadValidator.errors(def(f), Map.of("art", "a"))).isEmpty();
        assertThat(PayloadValidator.errors(def(f), Map.of("art", "z"))).hasSize(1);
    }

    @Test
    void multiSelectValuesMustAllBeOptions() {
        var f = new FormField("tags", FieldType.MULTI_SELECT, false, L, null, null,
                List.of(new Option("a", L), new Option("b", L)), null);
        assertThat(PayloadValidator.errors(def(f), Map.of("tags", List.of("a", "b")))).isEmpty();
        assertThat(PayloadValidator.errors(def(f), Map.of("tags", List.of("a", "z")))).hasSize(1);
    }

    @Test
    void booleanMustBeBoolean() {
        var f = new FormField("ok", FieldType.BOOLEAN, false, L, null, null, List.of(), null);
        assertThat(PayloadValidator.errors(def(f), Map.of("ok", true))).isEmpty();
        assertThat(PayloadValidator.errors(def(f), Map.of("ok", "ja"))).hasSize(1);
    }

    @Test
    void optionalAbsentFieldIsFine() {
        assertThat(PayloadValidator.errors(def(text("grund", false, 5)), Map.of())).isEmpty();
    }
}
