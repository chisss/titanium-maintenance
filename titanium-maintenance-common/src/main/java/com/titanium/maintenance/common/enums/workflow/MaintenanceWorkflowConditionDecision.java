package com.titanium.maintenance.common.enums.workflow;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** 条件任务的权威执行结论。 */
@Getter
public enum MaintenanceWorkflowConditionDecision implements BaseEnum {
    EXECUTE(1, "EXECUTE", "执行"),
    SKIP(2, "SKIP", "跳过");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    MaintenanceWorkflowConditionDecision(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
