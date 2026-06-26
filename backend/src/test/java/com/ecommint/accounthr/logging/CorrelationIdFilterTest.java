package com.ecommint.accounthr.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * {@link CorrelationIdFilter} X-Request-Id doğrulama testleri (log injection savunması).
 *
 * <p>Geçerli (non-blank, ≤64, [A-Za-z0-9-]) bir değer aynen yansıtılır; eksik/uzun/geçersiz
 * karakterli değerler reddedilip yerine taze bir UUID üretilir.
 */
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    private String reflectedId(String incoming) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (incoming != null) {
            request.addHeader(CorrelationIdFilter.HEADER, incoming);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getHeader(CorrelationIdFilter.HEADER);
    }

    @Test
    void validIdIsReflectedVerbatim() throws Exception {
        String id = "abc-123-DEF";
        assertThat(reflectedId(id)).isEqualTo(id);
    }

    @Test
    void missingHeaderGeneratesUuid() throws Exception {
        String reflected = reflectedId(null);
        assertThat(reflected).isNotBlank();
        // Üretilen UUID güvenli karakter setindedir.
        assertThat(reflected).matches("[A-Za-z0-9-]+");
    }

    @Test
    void blankHeaderGeneratesUuid() throws Exception {
        String reflected = reflectedId("   ");
        assertThat(reflected).matches("[0-9a-fA-F-]{36}");
    }

    @Test
    void crlfInjectionIsRejected() throws Exception {
        String malicious = "evil\r\nSet-Cookie: x=1";
        String reflected = reflectedId(malicious);
        assertThat(reflected).isNotEqualTo(malicious);
        assertThat(reflected).matches("[0-9a-fA-F-]{36}");
    }

    @Test
    void tooLongHeaderIsRejected() throws Exception {
        String tooLong = "a".repeat(CorrelationIdFilter.MAX_LENGTH + 1);
        String reflected = reflectedId(tooLong);
        assertThat(reflected).isNotEqualTo(tooLong);
        assertThat(reflected).matches("[0-9a-fA-F-]{36}");
    }

    @Test
    void maxLengthBoundaryIsAccepted() throws Exception {
        String atLimit = "a".repeat(CorrelationIdFilter.MAX_LENGTH);
        assertThat(reflectedId(atLimit)).isEqualTo(atLimit);
    }

    @Test
    void disallowedCharactersAreRejected() throws Exception {
        String reflected = reflectedId("has spaces and/slashes");
        assertThat(reflected).matches("[0-9a-fA-F-]{36}");
    }
}
