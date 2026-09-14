package com.titanium.maintenance.common.enums.config;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** 保全流程标准步骤。 */
@Getter
public enum MaintenanceStepType implements BaseEnum {
    CREATE(1, "CREATE", "创建保全"),
    DATA_ENTRY(2, "DATA_ENTRY", "保全信息录入"),
    VALIDATION(3, "VALIDATION", "业务校验"),
    REVIEW(4, "REVIEW", "保全审核"),
    UNDERWRITING(5, "UNDERWRITING", "核保"),
    FEE_SETTLEMENT(6, "FEE_SETTLEMENT", "保全收退费"),
    WAITING_EFFECTIVE(7, "WAITING_EFFECTIVE", "等待生效"),
    EFFECT(8, "EFFECT", "保全生效"),
    DOCUMENT(9, "DOCUMENT", "出具凭证"),
    COMPLETE(10, "COMPLETE", "完成");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    MaintenanceStepType(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }

    /**
     * 是否为收口步骤：在保全生效完成之后执行，不修改保单要素、责任与金额。
     * <p>
     * 判据：{@link #DOCUMENT}（出具凭证）与 {@link #COMPLETE}（完成）在标准步骤序中位于 {@link #EFFECT} 之后，
     * 承载「凭证出具 + 项目收口」而非业务变更，故案件级生效完成后仍须可达（见聚合 {@code requireWorkflowMutable}）；
     * 其余步骤在案件完成后一律冻结，避免生效后再次改动保单内容。
     * </p>
     */
    public boolean closingPhase() {
        return this == DOCUMENT || this == COMPLETE;
    }
}
