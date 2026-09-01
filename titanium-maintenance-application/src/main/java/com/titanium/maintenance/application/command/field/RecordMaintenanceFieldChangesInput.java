package com.titanium.maintenance.application.command.field;

import java.util.List;

import com.titanium.maintenance.common.enums.change.PolicyFieldDataType;

/** 独立案件字段草稿写入口参数。 */
public record RecordMaintenanceFieldChangesInput(
        String maintenanceId,
        String itemCode,
        List<FieldProposalInput> proposals,
        String operatorId,
        String tenantId) {

    /** 单个结构化字段提案，不接受任意对象合并。 */
    public record FieldProposalInput(
            String objectId,
            String fieldCode,
            PolicyFieldDataType dataType,
            String canonicalValue) {
    }
}
