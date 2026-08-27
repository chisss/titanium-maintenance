package com.titanium.maintenance.valueobject.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceEffectScheduleStatus;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

class MaintenanceEffectScheduleTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 8, 25, 12, 0);
    private static final LocalDateTime EXECUTION_AT = LocalDateTime.of(2026, 9, 1, 1, 0);

    @Test
    void shouldTrackPauseResumeAttemptRetryAndCompletion() {
        MaintenanceEffectSchedule created = MaintenanceEffectSchedule.create(
                "case-1:effect", EffectiveTimeType.FUTURE, "Asia/Shanghai", EXECUTION_AT, CREATED_AT);
        MaintenanceEffectSchedule paused = created.pause(CREATED_AT.plusMinutes(1));
        MaintenanceEffectSchedule resumed = paused.resume(EXECUTION_AT.plusHours(1), CREATED_AT.plusMinutes(2));
        MaintenanceEffectSchedule attempted = resumed.recordAttempt("attempt-1", CREATED_AT.plusMinutes(3));
        MaintenanceEffectSchedule retrying = attempted.recordFailure(
                "attempt-1", "POLICY_UNAVAILABLE", "Policy 暂时不可用",
                CREATED_AT.plusMinutes(8), false, CREATED_AT.plusMinutes(4));
        MaintenanceEffectSchedule secondAttempt = retrying.recordAttempt(
                "attempt-2", CREATED_AT.plusMinutes(8));
        MaintenanceEffectSchedule completed = secondAttempt.complete(
                "attempt-2", CREATED_AT.plusMinutes(9));

        assertEquals(MaintenanceEffectScheduleStatus.PAUSED, paused.status());
        assertEquals(MaintenanceEffectScheduleStatus.ACTIVE, retrying.status());
        assertEquals(2, secondAttempt.attemptCount());
        assertEquals(MaintenanceEffectScheduleStatus.COMPLETED, completed.status());
    }

    @Test
    void shouldRejectUnsupportedImmediateScheduleAndMismatchedAttempt() {
        assertThrows(MaintenanceValidationException.class, () -> MaintenanceEffectSchedule.create(
                "case-1:effect", EffectiveTimeType.IMMEDIATE, "Asia/Shanghai", EXECUTION_AT, CREATED_AT));
        MaintenanceEffectSchedule schedule = MaintenanceEffectSchedule.create(
                "case-1:effect", EffectiveTimeType.POLICY_ANNIVERSARY,
                "Asia/Shanghai", EXECUTION_AT, CREATED_AT).recordAttempt("attempt-1", CREATED_AT.plusMinutes(1));
        assertThrows(MaintenanceValidationException.class, () -> schedule.complete(
                "attempt-other", CREATED_AT.plusMinutes(2)));
    }
}
