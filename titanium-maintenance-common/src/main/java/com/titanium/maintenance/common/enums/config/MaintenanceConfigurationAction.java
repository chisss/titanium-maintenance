package com.titanium.maintenance.common.enums.config;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** 保全项配置生命周期操作。 */
@Getter
public enum MaintenanceConfigurationAction implements BaseEnum {
    CREATED(1, "CREATED", "创建草稿"),
    CONTENT_REPLACED(2, "CONTENT_REPLACED", "替换草稿内容"),
    SUBMITTED(3, "SUBMITTED", "提交审批"),
    APPROVED(4, "APPROVED", "审批通过"),
    REJECTED(5, "REJECTED", "审批驳回"),
    RETURNED_TO_DRAFT(6, "RETURNED_TO_DRAFT", "退回草稿"),
    PUBLISHED(7, "PUBLISHED", "发布"),
    RETIRED(8, "RETIRED", "退役"),
    REVISION_CREATED(9, "REVISION_CREATED", "创建修订版"),
    DRAFT_DELETED(10, "DRAFT_DELETED", "删除草稿");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    MaintenanceConfigurationAction(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
