package com.titanium.maintenance.common.enums.workflow;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** 保全案件流程任务状态。 */
@Getter
public enum MaintenanceWorkflowTaskStatus implements BaseEnum {
    PENDING(1, "PENDING", "等待前置步骤"),
    READY(2, "READY", "可处理"),
    WAITING_CONDITION(3, "WAITING_CONDITION", "等待条件判定"),
    IN_PROGRESS(4, "IN_PROGRESS", "处理中"),
    COMPLETED(5, "COMPLETED", "已完成"),
    SKIPPED(6, "SKIPPED", "已跳过"),
    REJECTED(7, "REJECTED", "已拒绝"),
    FAILED(8, "FAILED", "处理失败"),
    WAITING_EXTERNAL(9, "WAITING_EXTERNAL", "等待外部处理"),
    QUOTED(10, "QUOTED", "已报价待结算");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    MaintenanceWorkflowTaskStatus(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
