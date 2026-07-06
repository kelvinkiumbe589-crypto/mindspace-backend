package com.mindspace.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * The backend runs in UTC and stores server-generated timestamps (message/mood/forum
 * createdAt, etc.) as LocalDateTime via @CreationTimestamp. By default Jackson writes
 * these with no timezone, so browsers interpret them as *local* time and display them
 * offset by the client's UTC offset (e.g. 3h behind in Kenya).
 *
 * This customiser serialises every LocalDateTime as an explicit UTC instant with a
 * trailing "Z", so `new Date(value)` in the browser converts it to the user's local
 * time correctly. (Booking scheduledAt is a user-picked wall-clock and is serialised
 * as a plain String elsewhere, so it is unaffected by this.)
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer utcLocalDateTimeCustomizer() {
        return builder -> builder.serializerByType(LocalDateTime.class, new JsonSerializer<LocalDateTime>() {
            @Override
            public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeString(value.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            }
        });
    }
}
