package org.codeflow.veterineryproject.appointment.dto;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.codeflow.veterineryproject.common.time.ApiDateTime;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public class AppointmentDateTimeSerializer extends JsonSerializer<OffsetDateTime> {

    @Override
    public void serialize(OffsetDateTime value, JsonGenerator generator, SerializerProvider serializers) throws IOException {
        generator.writeString(value.atZoneSameInstant(ApiDateTime.ZONE).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }
}
