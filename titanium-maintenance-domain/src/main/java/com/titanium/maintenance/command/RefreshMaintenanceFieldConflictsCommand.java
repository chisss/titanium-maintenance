package com.titanium.maintenance.command;

import java.time.OffsetDateTime;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.casecreation.PolicyMaintenanceSnapshot;

/** 使用 Policy 最新快照刷新案件全部字段冲突。 */
public record RefreshMaintenanceFieldConflictsCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String operationId,
        String requestHash,
        PolicyMaintenanceSnapshot currentPolicySnapshot,
        OffsetDateTime refreshedAt,
        String refreshedBy,
        String tenantId) {
}
