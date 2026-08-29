package com.titanium.maintenance.valueobject;

import com.titanium.common.util.SnowflakeIdGenerator;

/**
 * 保单标识值对象。
 *
 * @param id 保单唯一标识
 */
public record PolicyId(String id) {

    public static PolicyId generate() {
        return new PolicyId(SnowflakeIdGenerator.generate());
    }

    public static PolicyId of(String id) {
        return new PolicyId(id);
    }
}
