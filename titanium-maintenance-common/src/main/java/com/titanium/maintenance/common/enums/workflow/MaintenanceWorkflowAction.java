package com.titanium.maintenance.common.enums.workflow;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** 保全流程任务操作。 */
@Getter
public enum MaintenanceWorkflowAction implements BaseEnum {
    CLAIM(1, "CLAIM", "领取"),
    START(2, "START", "开始"),
    COMPLETE(3, "COMPLETE", "完成"),
    FAIL(4, "FAIL", "失败"),
    RETRY(5, "RETRY", "重试"),
    DECIDE_CONDITION(6, "DECIDE_CONDITION", "条件判定"),
    DECIDE_REVIEW(7, "DECIDE_REVIEW", "审核决定"),
    DECIDE_UNDERWRITING(8, "DECIDE_UNDERWRITING", "核保决定"),
    RECORD_PREMIUM_QUOTE(9, "RECORD_PREMIUM_QUOTE", "记录保全报价"),
    SETTLE_PREMIUM(10, "SETTLE_PREMIUM", "执行保全收退费门禁"),
    FAIL_PREMIUM_SETTLEMENT(11, "FAIL_PREMIUM_SETTLEMENT", "记录保全收退费失败"),
    REQUEST_EFFECT(12, "REQUEST_EFFECT", "发起保全生效"),
    RECORD_POLICY_APPLICATION(13, "RECORD_POLICY_APPLICATION", "记录Policy生效回执"),
    FAIL_EFFECT(14, "FAIL_EFFECT", "记录保全生效失败"),
    WITHDRAW_ITEM(15, "WITHDRAW_ITEM", "撤销保全项目");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    MaintenanceWorkflowAction(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
