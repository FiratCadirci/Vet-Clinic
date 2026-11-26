package org.codeflow.veterineryproject.search.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalSearchServiceTest {

    @Test
    void normalizesPhone() {
        assertThat(GlobalSearchService.normalizePhone("+90 (532) 123-45.67"))
                .isEqualTo("+905321234567");
    }

    @Test
    void rejectsNonPhone() {
        assertThat(GlobalSearchService.normalizePhone("Pamuk")).isNull();
    }
}
