package com.ecommint.accounthr.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** E1-05 hassas değer maskeleme birim testleri. */
class LogMaskerTest {

    @Test
    void masksLongValueLeavingOnlySuffix() {
        String token = "eyJhbGciOiJIUzI1NiJ9.payload.signature-1234";
        String masked = LogMasker.mask(token);

        assertThat(masked).doesNotContain("payload");
        assertThat(masked).doesNotContain("signature");
        assertThat(masked).startsWith("****");
        // Yalnızca son 4 karakter görünür.
        assertThat(masked).endsWith("1234");
        assertThat(masked).isEqualTo("****1234");
    }

    @Test
    void fullyMasksShortValue() {
        assertThat(LogMasker.mask("secret")).isEqualTo("****");
        assertThat(LogMasker.mask("")).isEqualTo("****");
    }

    @Test
    void handlesNull() {
        assertThat(LogMasker.mask(null)).isEqualTo("null");
    }

    @Test
    void masksBearerTokenButKeepsScheme() {
        String header = "Bearer eyJhbGciOiJIUzI1NiJ9.verylong.token";
        String masked = LogMasker.maskAuthorizationHeader(header);

        assertThat(masked).isEqualTo("Bearer ****");
        assertThat(masked).doesNotContain("eyJ");
        assertThat(masked).doesNotContain("token");
    }

    @Test
    void masksAuthorizationHeaderWithoutScheme() {
        assertThat(LogMasker.maskAuthorizationHeader("rawtokenvalue")).isEqualTo("****");
        assertThat(LogMasker.maskAuthorizationHeader(null)).isEqualTo("null");
    }
}
