package org.codeflow.veterineryproject.common.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.codeflow.veterineryproject.common.time.ApiDateTime;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.function.Function;

/** Tüm tarih-saat alanları (created_at, updated_at vb.) JSON'da yalnızca tarih, saat ve dakika ile yazılır. */
@Configuration
public class JacksonDateTimeConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer dateTimeFormatCustomizer() {
        SimpleModule module = new SimpleModule("api-date-time");
        module.addSerializer(OffsetDateTime.class, serializer(ApiDateTime::format, OffsetDateTime.class));
        module.addSerializer(Instant.class, serializer(ApiDateTime::format, Instant.class));
        module.addSerializer(ZonedDateTime.class, serializer(ApiDateTime::format, ZonedDateTime.class));
        module.addSerializer(LocalDateTime.class, serializer(ApiDateTime::format, LocalDateTime.class));
        module.addDeserializer(OffsetDateTime.class, deserializer(ApiDateTime::parseOffset));
        module.addDeserializer(Instant.class, deserializer(ApiDateTime::parseInstant));
        module.addDeserializer(LocalDateTime.class, deserializer(ApiDateTime::parseLocal));
        return builder -> builder.modulesToInstall(module);
    }

    private static <T> JsonSerializer<T> serializer(Function<T, String> format, Class<T> type) {
        return new JsonSerializer<>() {
            @Override
            public void serialize(T value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                gen.writeString(format.apply(value));
            }

            @Override
            public Class<T> handledType() {
                return type;
            }
        };
    }

    private static <T> JsonDeserializer<T> deserializer(Function<String, T> parse) {
        return new JsonDeserializer<>() {
            @Override
            public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                String text = p.getValueAsString();
                if (text == null || text.isBlank()) {
                    return null;
                }
                try {
                    return parse.apply(text);
                } catch (java.time.format.DateTimeParseException ex) {
                    throw ctxt.weirdStringException(text, Object.class, "Tarih formatı yyyy-MM-dd HH:mm olmalıdır.");
                }
            }
        };
    }
}
