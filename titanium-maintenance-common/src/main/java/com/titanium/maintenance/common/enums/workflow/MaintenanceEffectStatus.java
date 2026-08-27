package com.titanium.maintenance.common.enums.workflow;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** 保全案件独立于流程和费用的生效状态。 */
@Getter
public enum MaintenanceEffectStatus implements BaseEnum {
    NOT_STARTED(1, "NOT_STARTED", "未发起"),
    EFFECTING(2, "EFFECTING", "生效处理中"),
    SCHEDULED(3, "SCHEDULED", "已计划"),
    APPLIED(4, "APPLIED", "已生效"),
    FAILED(5, "FAILED", "生效失败"),
    CONFLICTED(6, "CONFLICTED", "存在冲突");

    private final Integer enumCode;
    private final String code;
    private final String name;

    MaintenanceEffectStatus(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
