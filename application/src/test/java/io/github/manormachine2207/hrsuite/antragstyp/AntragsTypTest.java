package io.github.manormachine2207.hrsuite.antragstyp;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AntragsTypTest {

    private static final UUID ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final Map<String, String> TITLE = Map.of("de", "Urlaubsantrag", "en", "Leave Request");
    private static final Map<String, String> DESCRIPTION = Map.of("de", "Antrag auf Urlaub");

    @Test
    void constructorSetsFieldsAndDefaultStatus() {
        var at = new AntragsTyp(ID, TENANT_ID, "urlaub", TITLE, DESCRIPTION);

        assertThat(at.getId()).isEqualTo(ID);
        assertThat(at.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(at.getKey()).isEqualTo("urlaub");
        assertThat(at.getTitle()).isEqualTo(TITLE);
        assertThat(at.getDescription()).isEqualTo(DESCRIPTION);
        assertThat(at.getStatus()).isEqualTo(AntragsTypStatus.DRAFT);
        assertThat(at.getCurrentVersionId()).isNull();
    }

    @Test
    void markLiveSetsStatusLiveAndCurrentVersionId() {
        var at = new AntragsTyp(ID, TENANT_ID, "urlaub", TITLE, DESCRIPTION);
        UUID versionId = UUID.randomUUID();

        at.markLive(versionId);

        assertThat(at.getStatus()).isEqualTo(AntragsTypStatus.LIVE);
        assertThat(at.getCurrentVersionId()).isEqualTo(versionId);
    }

    @Test
    void setStatusMutatesStatus() {
        var at = new AntragsTyp(ID, TENANT_ID, "urlaub", TITLE, DESCRIPTION);

        at.setStatus(AntragsTypStatus.DEPRECATED);

        assertThat(at.getStatus()).isEqualTo(AntragsTypStatus.DEPRECATED);
    }

    @Test
    void setCurrentVersionIdMutatesVersionId() {
        var at = new AntragsTyp(ID, TENANT_ID, "urlaub", TITLE, DESCRIPTION);
        UUID versionId = UUID.randomUUID();

        at.setCurrentVersionId(versionId);

        assertThat(at.getCurrentVersionId()).isEqualTo(versionId);
    }
}
