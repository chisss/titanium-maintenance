package com.titanium.maintenance.api.request;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 基于既有生命周期差额发起保单费用冲正。 */
public record SettleMaintenanceReversalRequest(
        @NotBlank String sourceAdjustmentId,
        @NotNull LocalDateTime businessTime,
        @NotBlank String reason,
        @NotBlank String updatedBy) {
}
