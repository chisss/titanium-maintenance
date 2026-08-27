package com.titanium.maintenance.common.enums;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** 获取 Policy 建案快照失败的明确原因。 */
@Getter
public enum PolicyMaintenanceSnapshotFailureReason implements BaseEnum {
    NOT_FOUND(1, "NOT_FOUND", "保单不存在"),
    INACTIVE(2, "INACTIVE", "保单非活动状态"),
    TENANT_MISMATCH(3, "TENANT_MISMATCH", "租户回显不一致"),
    VERSION_MISSING(4, "VERSION_MISSING", "基准版本缺失"),
    CONTRACT_INVALID(5, "CONTRACT_INVALID", "快照契约无效"),
    UNAVAILABLE(6, "UNAVAILABLE", "快照服务不可用");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    PolicyMaintenanceSnapshotFailureReason(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }

    public static PolicyMaintenanceSnapshotFailureReason fromCode(String code) {
        return BaseEnum.fromCode(PolicyMaintenanceSnapshotFailureReason.class, code);
    }
}
