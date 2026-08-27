package com.titanium.maintenance.common.enums.workflow;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** 追溯影响分析生命周期状态。 */
@Getter
public enum MaintenanceRetroactiveImpactAnalysisStatus implements BaseEnum {
    ANALYZING(1, "ANALYZING", "分析中"),
    COMPLETED(2, "COMPLETED", "分析完成"),
    FAILED(3, "FAILED", "分析失败");

    private final Integer enumCode;
    private final String code;
    private final String name;

    MaintenanceRetroactiveImpactAnalysisStatus(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
