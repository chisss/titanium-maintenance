package com.titanium.maintenance.valueobject;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

@Getter
@EqualsAndHashCode
@ToString
public class CustomerId {
    private final String id;

    private CustomerId(String id) {
        this.id = id;
    }

    public static CustomerId generate() {
        return new CustomerId(UUID.randomUUID().toString());
    }

    public static CustomerId of(String id) {
        return new CustomerId(id);
    }
}