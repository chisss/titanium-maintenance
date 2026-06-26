package com.titanium.maintenance.valueobject;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

@Getter
@EqualsAndHashCode
@ToString
public class MaintenanceId {
    private final String id;

    private MaintenanceId(String id) {
        this.id = id;
    }

    public static MaintenanceId generate() {
        return new MaintenanceId(UUID.randomUUID().toString());
    }

    public static MaintenanceId of(String id) {
        return new MaintenanceId(id);
    }
}