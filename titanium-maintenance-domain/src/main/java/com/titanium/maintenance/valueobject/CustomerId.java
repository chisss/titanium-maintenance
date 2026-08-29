package com.titanium.maintenance.valueobject;

import com.titanium.common.util.SnowflakeIdGenerator;

/**
 * 客户标识值对象。
 *
 * @param id 客户唯一标识
 */
public record CustomerId(String id) {

    public static CustomerId generate() {
        return new CustomerId(SnowflakeIdGenerator.generate());
    }

    public static CustomerId of(String id) {
        return new CustomerId(id);
    }
}
