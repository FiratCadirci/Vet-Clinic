package org.codeflow.veterineryproject.auth.model;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformAdminAccessLogMappingTest {

    @Test
    void idShouldBeLongIdentityAndOperationString() throws Exception {
        var id = PlatformAdminAccessLog.class.getDeclaredField("id");
        assertThat(id.getType()).isEqualTo(Long.class);
        assertThat(id.getAnnotation(GeneratedValue.class).strategy()).isEqualTo(GenerationType.IDENTITY);
        assertThat(PlatformAdminAccessLog.class.getDeclaredField("operation").getType()).isEqualTo(String.class);
    }
}
