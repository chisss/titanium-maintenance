package com.titanium.maintenance.event;

import java.time.LocalDateTime;

import com.titanium.maintenance.enums.MaintenanceType;
import com.titanium.maintenance.valueobject.MaintenanceId;

/**
 * 保全执行完成事件
 * <p>
 * 携带 policyId 与 maintenanceType 作为跨域上下文：保全是"变更发起者/审批者"，policy 是"执行者"。 policy
 * 域监听本事件（经 Kafka），按 maintenanceType 翻译为对应保单命令，完成保全→保单状态变更的回写闭环。
 * </p>
 *
 * @param policyId 关联保单ID（回写闭环目标）
 * @param maintenanceType 保全类型（policy 侧据此翻译为暂停/恢复/终止等命令）
 */
public record MaintenanceExecutedEvent(MaintenanceId maintenanceId, String policyId, MaintenanceType maintenanceType,
                                       LocalDateTime effectiveTime, String executionDetails, LocalDateTime updatedAt,
                                       String updatedBy, String tenantId) {
}
