package com.titanium.maintenance.valueobject;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

@Getter
@EqualsAndHashCode
@ToString
public class PolicyId {
    private final String id;

    private PolicyId(String id) {
        this.id = id;
    }

    public static PolicyId generate() {
        return new PolicyId(UUID.randomUUID().toString());
    }

    public static PolicyId of(String id) {
        return new PolicyId(id);
    }
}