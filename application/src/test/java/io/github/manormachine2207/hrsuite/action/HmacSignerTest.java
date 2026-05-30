package io.github.manormachine2207.hrsuite.action;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HmacSignerTest {

    @Test
    void producesStableLowercaseHexHmacSha256() {
        // Known vector: key "key", data "The quick brown fox jumps over the lazy dog"
        String mac = HmacSigner.hexSha256("key", "The quick brown fox jumps over the lazy dog");
        assertThat(mac).isEqualTo("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");
    }

    @Test
    void differentSecretChangesSignature() {
        String a = HmacSigner.hexSha256("s1", "payload");
        String b = HmacSigner.hexSha256("s2", "payload");
        assertThat(a).isNotEqualTo(b);
    }
}
