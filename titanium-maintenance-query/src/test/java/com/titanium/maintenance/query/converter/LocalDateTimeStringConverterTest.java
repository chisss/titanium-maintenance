package com.titanium.maintenance.query.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class LocalDateTimeStringConverterTest {

    private final LocalDateTimeStringConverter converter = new LocalDateTimeStringConverter();

    @Test
    void shouldPreserveNanosecondPrecision() {
        LocalDateTime value = LocalDateTime.parse("2026-08-26T17:23:22.171414751");

        String stored = converter.convertToDatabaseColumn(value);

        assertEquals("2026-08-26T17:23:22.171414751", stored);
        assertEquals(value, converter.convertToEntityAttribute(stored));
    }

    @Test
    void shouldReadLegacyDatabaseValueWithSpaceSeparator() {
        LocalDateTime value = converter.convertToEntityAttribute("2026-08-26 17:23:22");

        assertEquals(LocalDateTime.parse("2026-08-26T17:23:22"), value);
    }

    @Test
    void shouldPreserveNullValues() {
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
    }
}
