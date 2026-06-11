package io.github.manormachine2207.hrsuite.antragstyp.form;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BDR-005: DE/FR/IT/EN sind Pflicht. Das Publish-Gate (Review 2026-06-12) erzwingt
 * Vollstaendigkeit fuer alles, was Antragsteller sehen: Antragstyp-Titel, Feld-Labels,
 * Options-Labels. helpText ist optional — aber wenn gesetzt, ebenfalls vollstaendig.
 */
class FormI18nValidatorTest {

    private static final Map<String, String> FULL =
            Map.of("de", "D", "fr", "F", "it", "I", "en", "E");

    private static FormField field(String key, Map<String, String> label) {
        return new FormField(key, FieldType.TEXT, false, label, null, null, List.of(), null);
    }

    @Test
    void completeTitleAndLabelsPass() {
        var def = new FormDefinition(List.of(field("a", FULL)));
        assertThat(FormI18nValidator.issues(FULL, def)).isEmpty();
    }

    @Test
    void missingTitleLocalesAreReported() {
        var issues = FormI18nValidator.issues(Map.of("fr", "Titre"), new FormDefinition(List.of()));
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0)).contains("title").contains("de").contains("it").contains("en");
    }

    @Test
    void blankLocaleCountsAsMissing() {
        var title = Map.of("de", "D", "fr", " ", "it", "I", "en", "E");
        var issues = FormI18nValidator.issues(title, new FormDefinition(List.of()));
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0)).contains("fr");
    }

    @Test
    void fieldWithoutAnyLabelIsReported() {
        var def = new FormDefinition(List.of(field("kommentar", null)));
        var issues = FormI18nValidator.issues(FULL, def);
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0)).contains("kommentar");
    }

    @Test
    void optionLabelsAreChecked() {
        var opt = new Option("yes", Map.of("de", "Ja"));
        var f = new FormField("choice", FieldType.SELECT, false, FULL, null, null, List.of(opt), null);
        var issues = FormI18nValidator.issues(FULL, new FormDefinition(List.of(f)));
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0)).contains("choice").contains("yes");
    }

    @Test
    void helpTextOnlyCheckedWhenPresent() {
        var ok = new FormField("a", FieldType.TEXT, false, FULL, null, null, List.of(), null);
        assertThat(FormI18nValidator.issues(FULL, new FormDefinition(List.of(ok)))).isEmpty();

        var partialHelp = new FormField("a", FieldType.TEXT, false, FULL, Map.of("de", "Hilfe"),
                null, List.of(), null);
        var issues = FormI18nValidator.issues(FULL, new FormDefinition(List.of(partialHelp)));
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0)).contains("helpText");
    }
}
