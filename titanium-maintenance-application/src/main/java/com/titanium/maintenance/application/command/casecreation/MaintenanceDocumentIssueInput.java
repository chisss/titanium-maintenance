package com.titanium.maintenance.application.command.casecreation;

import com.titanium.maintenance.common.enums.config.MaintenanceChannel;

/**
 * 出具保全凭证的应用层输入。
 * <p>
 * 凭证模板编码**不作为入参**：它由案件冻结配置的输出规则决定，应用层读取配置后组装证据，
 * 与核保证据「不接受调用方自报结论」同一红线。调用方只提供凭证编号。
 * </p>
 */
public record MaintenanceDocumentIssueInput(
        String maintenanceId,
        String taskId,
        String operationId,
        String voucherNo,
        String operatorId,
        String tenantId,
        MaintenanceChannel source) {
}
