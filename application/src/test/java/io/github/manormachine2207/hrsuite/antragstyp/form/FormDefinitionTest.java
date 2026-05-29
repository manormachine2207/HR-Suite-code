package io.github.manormachine2207.hrsuite.antragstyp.form;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FormDefinitionTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializesAndDeserializesRoundTrip() throws Exception {
        var field = new FormField(
                "grund",
                FieldType.TEXT,
                true,
                Map.of("de", "Grund", "fr", "Motif", "it", "Motivo", "en", "Reason"),
                Map.of("de", "Bitte begruenden"),
                new Validation(500, null, null),
                List.of(),
                null);
        var def = new FormDefinition(List.of(field));

        String json = mapper.writeValueAsString(def);
        FormDefinition back = mapper.readValue(json, FormDefinition.class);

        assertThat(back).isEqualTo(def);
        assertThat(back.fields()).hasSize(1);
        assertThat(back.fields().get(0).key()).isEqualTo("grund");
        assertThat(back.fields().get(0).type()).isEqualTo(FieldType.TEXT);
        assertThat(back.fields().get(0).validation().maxLength()).isEqualTo(500);
    }

    @Test
    void selectFieldCarriesOptionsWithI18nLabels() throws Exception {
        var opt = new Option("ja", Map.of("de", "Ja", "fr", "Oui", "it", "Si", "en", "Yes"));
        var field = new FormField(
                "zustimmung",
                FieldType.SELECT,
                true,
                Map.of("de", "Zustimmung"),
                Map.of(),
                null,
                List.of(opt),
                "ja");
        var def = new FormDefinition(List.of(field));

        String json = mapper.writeValueAsString(def);
        FormDefinition back = mapper.readValue(json, FormDefinition.class);

        assertThat(back.fields().get(0).options()).containsExactly(opt);
        assertThat(back.fields().get(0).defaultValue()).isEqualTo("ja");
    }
}
