package com.titanium.maintenance.query.converter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** 以 ISO-8601 字符串无损持久化需要参与幂等摘要的纳秒级时间。 */
@Converter
public class LocalDateTimeStringConverter implements AttributeConverter<LocalDateTime, String> {

    private static final DateTimeFormatter LEGACY_DATABASE_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .optionalEnd()
            .toFormatter();

    @Override
    public String convertToDatabaseColumn(LocalDateTime attribute) {
        return attribute == null ? null : attribute.toString();
    }

    @Override
    public LocalDateTime convertToEntityAttribute(String databaseValue) {
        if (databaseValue == null) {
            return null;
        }
        DateTimeFormatter formatter = databaseValue.indexOf(' ') == 10
                ? LEGACY_DATABASE_FORMATTER
                : DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        return LocalDateTime.parse(databaseValue, formatter);
    }
}
