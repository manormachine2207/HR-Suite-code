package io.github.manormachine2207.hrsuite.antragstyp;

import io.github.manormachine2207.hrsuite.antragstyp.form.FormDefinition;
import io.github.manormachine2207.hrsuite.antragstyp.form.FormField;
import io.github.manormachine2207.hrsuite.antragstyp.form.FieldType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AntragsTypVersionTest {

    private static final UUID ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ANTRAGSTYP_ID = UUID.randomUUID();

    private static FormDefinition minimalFormDefinition() {
        return new FormDefinition(List.of());
    }

    private static FormDefinition formDefinitionWithField(String key) {
        var field = new FormField(
                key,
                FieldType.TEXT,
                false,
                Map.of("de", "Feld"),
                Map.of(),
                null,
                List.of(),
                null);
        return new FormDefinition(List.of(field));
    }

    @Test
    void constructorSetsFieldsAndDefaults() {
        var formDef = minimalFormDefinition();
        var sfBindings = Map.<String, Object>of("action", "submit");

        var version = new AntragsTypVersion(ID, TENANT_ID, ANTRAGSTYP_ID, 1, formDef, "<bpmn/>", sfBindings);

        assertThat(version.getId()).isEqualTo(ID);
        assertThat(version.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(version.getAntragstypId()).isEqualTo(ANTRAGSTYP_ID);
        assertThat(version.getMajor()).isEqualTo(1);
        assertThat(version.getMinor()).isEqualTo(0);
        assertThat(version.getStatus()).isEqualTo(VersionStatus.DRAFT);
        assertThat(version.getFormDefinition()).isEqualTo(formDef);
        assertThat(version.getWorkflowBpmn()).isEqualTo("<bpmn/>");
        assertThat(version.getSfActionBindings()).isEqualTo(sfBindings);
    }

    @Test
    void applyMinorEditIncrementsMinorAndSwapsDefinition() {
        var version = new AntragsTypVersion(ID, TENANT_ID, ANTRAGSTYP_ID, 1, minimalFormDefinition(), null, null);
        var newDef = formDefinitionWithField("grund");
        var changelog = Map.<String, Object>of("added", List.of("grund"));

        version.applyMinorEdit(newDef, changelog);

        assertThat(version.getMinor()).isEqualTo(1);
        assertThat(version.getFormDefinition()).isEqualTo(newDef);
        assertThat(version.getMinorChangelog()).isEqualTo(changelog);
    }

    @Test
    void applyMinorEditIncrementsCounterCumulatively() {
        var version = new AntragsTypVersion(ID, TENANT_ID, ANTRAGSTYP_ID, 1, minimalFormDefinition(), null, null);

        version.applyMinorEdit(formDefinitionWithField("a"), Map.of());
        version.applyMinorEdit(formDefinitionWithField("b"), Map.of());

        assertThat(version.getMinor()).isEqualTo(2);
    }

    @Test
    void replaceDraftContentSwapsAllThreeFields() {
        var version = new AntragsTypVersion(ID, TENANT_ID, ANTRAGSTYP_ID, 1, minimalFormDefinition(), "<old/>", Map.of("old", "binding"));
        var newDef = formDefinitionWithField("neues-feld");
        var newSf = Map.<String, Object>of("new", "binding");

        version.replaceDraftContent(newDef, "<new/>", newSf);

        assertThat(version.getFormDefinition()).isEqualTo(newDef);
        assertThat(version.getWorkflowBpmn()).isEqualTo("<new/>");
        assertThat(version.getSfActionBindings()).isEqualTo(newSf);
    }

    @Test
    void publishSetsStatusPublishedAndPublishedAtAndPublishedBy() {
        var version = new AntragsTypVersion(ID, TENANT_ID, ANTRAGSTYP_ID, 1, minimalFormDefinition(), null, null);
        var publisherId = UUID.randomUUID();

        version.publish(publisherId);

        assertThat(version.getStatus()).isEqualTo(VersionStatus.PUBLISHED);
        assertThat(version.getPublishedAt()).isNotNull();
        assertThat(version.getPublishedBy()).isEqualTo(publisherId);
    }
}
