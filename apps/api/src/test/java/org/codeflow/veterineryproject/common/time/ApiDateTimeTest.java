package org.codeflow.veterineryproject.common.time;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.codeflow.veterineryproject.common.config.JacksonDateTimeConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiDateTimeTest {

    record Sample(OffsetDateTime createdAt, Instant updatedAt, LocalDateTime localAt, LocalDate birthDate) {
    }

    private final ObjectMapper mapper = mapper();

    private static ObjectMapper mapper() {
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json().featuresToDisable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        new JacksonDateTimeConfig().dateTimeFormatCustomizer().customize(builder);
        return builder.build();
    }

    @Test
    void format_showsOnlyDateHourAndMinuteInApiZone() {
        OffsetDateTime utc = OffsetDateTime.of(2026, 10, 23, 8, 42, 59, 123_000_000, ZoneOffset.UTC);

        assertThat(ApiDateTime.format(utc)).isEqualTo("2026-10-23 11:42");
        assertThat(ApiDateTime.format(utc.toInstant())).isEqualTo("2026-10-23 11:42");
        assertThat(ApiDateTime.format(LocalDateTime.of(2026, 1, 5, 9, 7, 30))).isEqualTo("2026-01-05 09:07");
    }

    @Test
    void parse_acceptsApiFormatAndIso() {
        OffsetDateTime fromApi = ApiDateTime.parseOffset("2026-10-23 11:42");
        OffsetDateTime fromIso = ApiDateTime.parseOffset("2026-10-23T08:42:00Z");

        assertThat(fromApi.toInstant()).isEqualTo(fromIso.toInstant());
        assertThat(ApiDateTime.parseLocal("2026-10-23 11:42")).isEqualTo(LocalDateTime.of(2026, 10, 23, 11, 42));
        assertThatThrownBy(() -> ApiDateTime.parseOffset("23.10.2026")).isInstanceOf(DateTimeParseException.class);
    }

    @Test
    void json_serializesDateTimesWithoutSecondsAndKeepsLocalDate() throws Exception {
        Sample sample = new Sample(OffsetDateTime.of(2026, 10, 23, 8, 42, 59, 0, ZoneOffset.UTC),
                Instant.parse("2026-10-23T08:42:59.999Z"), LocalDateTime.of(2026, 10, 23, 11, 42, 59), LocalDate.of(2020, 3, 4));

        String json = mapper.writeValueAsString(sample);

        assertThat(json).contains("\"createdAt\":\"2026-10-23 11:42\"", "\"updatedAt\":\"2026-10-23 11:42\"",
                "\"localAt\":\"2026-10-23 11:42\"", "\"birthDate\":\"2020-03-04\"");
    }

    @Test
    void json_deserializesBothFormats() throws Exception {
        Sample api = mapper.readValue("{\"createdAt\":\"2026-10-23 11:42\",\"updatedAt\":\"2026-10-23 11:42\"}", Sample.class);
        Sample iso = mapper.readValue("{\"createdAt\":\"2026-10-23T08:42:00Z\",\"updatedAt\":\"2026-10-23T08:42:00Z\"}", Sample.class);

        assertThat(api.createdAt().toInstant()).isEqualTo(iso.createdAt().toInstant());
        assertThat(api.updatedAt()).isEqualTo(iso.updatedAt());
    }

    @Test
    void json_rejectsInvalidDateTime() {
        assertThatThrownBy(() -> mapper.readValue("{\"createdAt\":\"yarin sabah\"}", Sample.class)).isInstanceOf(Exception.class);
    }
}
