package com.titanium.maintenance.valueobject.workflow;

import java.time.LocalDateTime;
import java.time.ZoneId;

import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceEffectScheduleStatus;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 案件级未来生效计划；租约由基础设施运行时协调，不进入领域事实。 */
public record MaintenanceEffectSchedule(
        String scheduleId,
        EffectiveTimeType effectiveTimeType,
        String tenantZoneId,
        LocalDateTime nextExecutionAt,
        MaintenanceEffectScheduleStatus status,
        int attemptCount,
        String lastAttemptId,
        LocalDateTime lastAttemptAt,
        String lastErrorCode,
        String lastErrorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public MaintenanceEffectSchedule {
        scheduleId = requireText("scheduleId", scheduleId);
        tenantZoneId = requireText("tenantZoneId", tenantZoneId);
        try {
            ZoneId.of(tenantZoneId);
        } catch (RuntimeException exception) {
            throw invalid("tenantZoneId", "租户时区无效");
        }
        if (!supportsScheduling(effectiveTimeType) || nextExecutionAt == null || status == null
                || attemptCount < 0 || createdAt == null || updatedAt == null) {
            throw invalid("schedule", "未来生效计划字段不完整");
        }
        lastAttemptId = normalize(lastAttemptId);
        lastErrorCode = normalize(lastErrorCode);
        lastErrorMessage = normalize(lastErrorMessage);
        if ((lastAttemptId == null) != (lastAttemptAt == null)) {
            throw invalid("lastAttempt", "最近执行标识与时间必须同时存在");
        }
        if ((lastErrorCode == null) != (lastErrorMessage == null)) {
            throw invalid("lastError", "最近错误码与错误信息必须同时存在");
        }
    }

    public static MaintenanceEffectSchedule create(
            String scheduleId,
            EffectiveTimeType effectiveTimeType,
            String tenantZoneId,
            LocalDateTime nextExecutionAt,
            LocalDateTime createdAt) {
        return new MaintenanceEffectSchedule(
                scheduleId, effectiveTimeType, tenantZoneId, nextExecutionAt,
                MaintenanceEffectScheduleStatus.ACTIVE, 0, null, null, null, null,
                createdAt, createdAt);
    }

    public MaintenanceEffectSchedule pause(LocalDateTime pausedAt) {
        if (status != MaintenanceEffectScheduleStatus.ACTIVE) {
            throw invalid("status", "只有待执行计划可以暂停");
        }
        return new MaintenanceEffectSchedule(
                scheduleId, effectiveTimeType, tenantZoneId, nextExecutionAt,
                MaintenanceEffectScheduleStatus.PAUSED, attemptCount, lastAttemptId, lastAttemptAt,
                lastErrorCode, lastErrorMessage, createdAt, pausedAt);
    }

    public MaintenanceEffectSchedule resume(LocalDateTime resumedExecutionAt, LocalDateTime resumedAt) {
        if (status != MaintenanceEffectScheduleStatus.PAUSED
                && status != MaintenanceEffectScheduleStatus.FAILED) {
            throw invalid("status", "只有已暂停或失败计划可以恢复");
        }
        return new MaintenanceEffectSchedule(
                scheduleId, effectiveTimeType, tenantZoneId, resumedExecutionAt,
                MaintenanceEffectScheduleStatus.ACTIVE, attemptCount, lastAttemptId, lastAttemptAt,
                null, null, createdAt, resumedAt);
    }

    public MaintenanceEffectSchedule recordAttempt(String attemptId, LocalDateTime attemptedAt) {
        attemptId = requireText("attemptId", attemptId);
        if (status != MaintenanceEffectScheduleStatus.ACTIVE) {
            throw invalid("status", "当前计划不可执行");
        }
        if (attemptId.equals(lastAttemptId)) {
            return this;
        }
        return new MaintenanceEffectSchedule(
                scheduleId, effectiveTimeType, tenantZoneId, nextExecutionAt, status,
                attemptCount + 1, attemptId, attemptedAt, null, null, createdAt, attemptedAt);
    }

    public MaintenanceEffectSchedule recordFailure(
            String attemptId,
            String errorCode,
            String errorMessage,
            LocalDateTime retryAt,
            boolean terminal,
            LocalDateTime failedAt) {
        requireCurrentAttempt(attemptId);
        if (!terminal && retryAt == null) {
            throw invalid("retryAt", "可重试失败必须提供下一执行时间");
        }
        return new MaintenanceEffectSchedule(
                scheduleId, effectiveTimeType, tenantZoneId,
                terminal ? nextExecutionAt : retryAt,
                terminal ? MaintenanceEffectScheduleStatus.FAILED : MaintenanceEffectScheduleStatus.ACTIVE,
                attemptCount, lastAttemptId, lastAttemptAt,
                requireText("errorCode", errorCode), requireText("errorMessage", errorMessage),
                createdAt, failedAt);
    }

    public MaintenanceEffectSchedule complete(String attemptId, LocalDateTime completedAt) {
        requireCurrentAttempt(attemptId);
        return new MaintenanceEffectSchedule(
                scheduleId, effectiveTimeType, tenantZoneId, nextExecutionAt,
                MaintenanceEffectScheduleStatus.COMPLETED, attemptCount, lastAttemptId, lastAttemptAt,
                null, null, createdAt, completedAt);
    }

    public boolean samePlan(String candidateScheduleId, String candidateZoneId, LocalDateTime candidateExecutionAt) {
        return scheduleId.equals(candidateScheduleId)
                && tenantZoneId.equals(candidateZoneId)
                && nextExecutionAt.equals(candidateExecutionAt);
    }

    public static boolean supportsScheduling(EffectiveTimeType type) {
        return type == EffectiveTimeType.FUTURE
                || type == EffectiveTimeType.SPECIFIED_DATE
                || type == EffectiveTimeType.NEXT_BILLING_DATE
                || type == EffectiveTimeType.POLICY_ANNIVERSARY;
    }

    private void requireCurrentAttempt(String attemptId) {
        if (lastAttemptId == null || !lastAttemptId.equals(requireText("attemptId", attemptId))) {
            throw invalid("attemptId", "执行结果与最近一次计划尝试不一致");
        }
    }

    private static String requireText(String field, String value) {
        if (value == null || value.isBlank()) {
            throw invalid(field, "字段不能为空");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static MaintenanceValidationException invalid(String field, String message) {
        return new MaintenanceValidationException("MaintenanceEffectSchedule", field, message);
    }
}
