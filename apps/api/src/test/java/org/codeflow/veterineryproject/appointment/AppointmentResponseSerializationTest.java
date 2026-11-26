package org.codeflow.veterineryproject.appointment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.codeflow.veterineryproject.appointment.dto.AppointmentResponse;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AppointmentResponseSerializationTest {

    @Test
    void appointmentTimesAreSerializedAsIso8601WithIstanbulOffset() throws Exception {
        OffsetDateTime appointmentTime = OffsetDateTime.parse("2032-05-03T06:30:00Z");
        AppointmentResponse response = new AppointmentResponse(
                UUID.randomUUID(), "A-000123", UUID.randomUUID(), UUID.randomUUID(), "Ayşe Yılmaz",
                UUID.randomUUID(), "Pamuk", "P-000123", null, null, "EXAM", "BOOKED",
                appointmentTime, appointmentTime.plusMinutes(30), null, null, "STAFF",
                null, null, appointmentTime, appointmentTime);

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        JsonNode json = mapper.readTree(mapper.writeValueAsString(response));

        assertThat(json.path("startsAt").asText()).isEqualTo("2032-05-03T09:30:00+03:00");
        assertThat(json.path("endsAt").asText()).isEqualTo("2032-05-03T10:00:00+03:00");
        assertThat(json.path("createdAt").asText()).isEqualTo("2032-05-03T09:30:00+03:00");
    }
}
