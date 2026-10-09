package com.titanium.maintenance.valueobject.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

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

    /**
     * 生效方式 ×「是否需要未来生效计划」必须是**穷尽且显式**的分类。
     *
     * <p>
     * 两道判据：① 两侧分区必须覆盖全部枚举常量（新增生效方式时穷尽 switch 已在编译期拦住漏改，
     * 本用例拦的是「表态了但扔进错误的一侧」）；② 两侧集合**逐成员相等**，任一侧搬错类别即红。
     * 落进「建案即执行」侧意味着该方式**永远不会被排期执行**，只允许时点在请求内已确定的方式
     * （立即 / 追溯），其余一律必须可排期。
     * </p>
     */
    @Test
    void shouldClassifyEveryEffectiveTimeTypeExplicitly() {
        Set<EffectiveTimeType> schedulable = Set.of(
                EffectiveTimeType.FUTURE, EffectiveTimeType.SPECIFIED_DATE,
                EffectiveTimeType.NEXT_BILLING_DATE, EffectiveTimeType.POLICY_ANNIVERSARY,
                EffectiveTimeType.NEXT_PERIOD);
        Set<EffectiveTimeType> immediate = Set.of(
                EffectiveTimeType.IMMEDIATE, EffectiveTimeType.RETROACTIVE);

        Set<EffectiveTimeType> actualSchedulable = classify(true);
        Set<EffectiveTimeType> actualImmediate = classify(false);

        assertEquals(EffectiveTimeType.values().length, schedulable.size() + immediate.size());
        assertFalse(actualSchedulable.isEmpty(), "可排期侧为空说明过滤恒假，判据自身失效");
        assertFalse(actualImmediate.isEmpty(), "建案即执行侧为空说明过滤恒真，判据自身失效");
        assertEquals(schedulable, actualSchedulable);
        assertEquals(immediate, actualImmediate);
    }

    /** 次期生效（NEXT_PERIOD）必须可建未来计划，否则案件建出来永不被执行（改造前即此形态）。 */
    @Test
    void shouldAllowSchedulingNextPeriod() {
        MaintenanceEffectSchedule schedule = MaintenanceEffectSchedule.create(
                "case-1:effect", EffectiveTimeType.NEXT_PERIOD, "Asia/Shanghai", EXECUTION_AT, CREATED_AT);

        assertTrue(MaintenanceEffectSchedule.supportsScheduling(EffectiveTimeType.NEXT_PERIOD));
        assertEquals(EffectiveTimeType.NEXT_PERIOD, schedule.effectiveTimeType());
        assertEquals(MaintenanceEffectScheduleStatus.ACTIVE, schedule.status());
    }

    private Set<EffectiveTimeType> classify(boolean schedulable) {
        return Arrays.stream(EffectiveTimeType.values())
                .filter(type -> MaintenanceEffectSchedule.supportsScheduling(type) == schedulable)
                .collect(Collectors.toSet());
    }
}
