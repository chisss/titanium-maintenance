package com.titanium.maintenance.aggregate;

import java.time.LocalDateTime;
import java.util.Objects;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateLifecycle;

import com.titanium.maintenance.command.CompleteMaintenanceEffectScheduleCommand;
import com.titanium.maintenance.command.PauseMaintenanceEffectScheduleCommand;
import com.titanium.maintenance.command.RecordMaintenanceEffectScheduleAttemptCommand;
import com.titanium.maintenance.common.enums.workflow.MaintenanceEffectScheduleStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceEffectStatus;
import com.titanium.maintenance.common.exception.MaintenanceNotFoundException;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.event.MaintenanceEffectCompensationRequiredEvent;
import com.titanium.maintenance.event.MaintenanceEffectCompensationResolvedEvent;
import com.titanium.maintenance.event.MaintenanceEffectScheduleAttemptedEvent;
import com.titanium.maintenance.event.MaintenanceEffectScheduleCompletedEvent;
import com.titanium.maintenance.event.MaintenanceEffectScheduleFailedEvent;
import com.titanium.maintenance.event.MaintenanceEffectSchedulePausedEvent;
import com.titanium.maintenance.event.MaintenanceEffectScheduleResumedEvent;
import com.titanium.maintenance.event.MaintenanceEffectScheduledEvent;
import com.titanium.maintenance.event.MaintenanceEffectStatusChangedEvent;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceEffectCompensationEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceEffectSchedule;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Maintenance 聚合的「生效计划（Effect Plan）」成员实体（DSN-01c 自聚合根下沉，批 4）。
 * <p>
 * <b>职责边界</b>：承载案件生效状态的载体与未来生效计划的执行生命周期——生效状态
 * （{@link #effectStatus}，由 {@code MaintenanceEffectStatusChangedEvent} 回放重建）、未来生效计划
 * （{@link #effectSchedule}，建立 / 暂停 / 恢复 / 执行尝试 / 失败 / 完成的六类事件回放重建）
 * 与人工补偿事实（{@link #effectCompensationEvidence} /
 * {@link #effectCompensationRequired}）。计划状态载体为不可变记录
 * {@link MaintenanceEffectSchedule}，命令处理器校验后 apply 事件、由本实体的
 * {@code @EventSourcingHandler} 接管状态重建。
 * </p>
 * <p>
 * <b>成员不互引（🔴 勿破坏）</b>：本实体只持有自身主题的状态，不引用聚合内其它成员；快照序列化
 * （Axon JacksonSerializer）会把成员内联进聚合 JSON，成员互引会导致同一对象被序列化两次、反序列化出
 * 相互独立的副本 ⇒ 状态不同步。依赖其它主题的判定留在聚合根。
 * </p>
 * <p>
 * <b>下沉边界（🔴 为何只下沉三个处理器）</b>：只有完全操作本成员自身状态的处理器随之下沉
 * （暂停 / 记录执行尝试 / 关闭计划）；以下三类处理器<b>留驻聚合根</b>，经本成员的包级方法
 * （{@link #requireSchedule(String)}、{@link #resume(LocalDateTime, LocalDateTime)}、
 * {@link #recordFailure}、{@link #currentEffectStatus()}）转发：
 * </p>
 * <ol>
 * <li>依赖流程任务成员的处理器（建立 / 恢复 / 记录失败计划须读写 {@code workflow} 的活跃生效任务，
 *     如 {@code MaintenanceEffectScheduleApplicationService} 建立的计划在恢复时要联动失败任务重试）；</li>
 * <li>{@code changeEffectStatus} 的写路径——它被四个主题的处理器调用于 12 处（计划族、案件级协奏族、
 *     补偿族、字段冲突族），且「转为已生效」分支依赖聚合根的保全项与字段事实（组装批单要素），
 *     属跨成员协调，不是单一成员可闭环的行为；</li>
 * <li>案件级协奏族（请求生效 / 五条回执收口 / 生效失败）——本身就是跨流程任务与生效两主题的编排器。</li>
 * </ol>
 */
@Getter
@NoArgsConstructor
public class MaintenanceEffectPlan {

    /** 租户ID（创建时注入，命令归属校验用） */
    private String                                 tenantId;

    /** 聚合标识（创建时注入，事件发布与异常构造用） */
    private MaintenanceId                          maintenanceId;

    /** 当前生效状态（事件回放重建；案件生效状态机的唯一载体） */
    private MaintenanceEffectStatus                effectStatus;

    /** 未来生效计划（建立 / 暂停 / 恢复 / 尝试 / 失败 / 完成六类事件回放重建，未建立时为 null） */
    private MaintenanceEffectSchedule              effectSchedule;

    /** 人工补偿事实（Policy 已成功而案件回执写入失败时记录，未记录时为 null） */
    private MaintenanceEffectCompensationEvidence  effectCompensationEvidence;

    /** 补偿事实未结清标识（记录补偿事实事件置真、结清事件置假） */
    private boolean                                effectCompensationRequired;

    MaintenanceEffectPlan(String tenantId, MaintenanceId maintenanceId) {
        this.tenantId = tenantId;
        this.maintenanceId = maintenanceId;
        this.effectStatus = MaintenanceEffectStatus.NOT_STARTED;
    }

    // ==================== 命令处理器（完全操作本成员自身状态者随之下沉） ====================

    /** 暂停尚未完成的未来生效计划。 */
    @CommandHandler
    public void handle(PauseMaintenanceEffectScheduleCommand command) {
        requireSameTenant(command.tenantId());
        requireSchedule(command.scheduleId());
        if (command.reason() == null || command.reason().isBlank()) {
            throw new MaintenanceValidationException(
                    "PauseMaintenanceEffectScheduleCommand", "reason", "暂停原因不能为空");
        }
        if (effectSchedule.status() == MaintenanceEffectScheduleStatus.PAUSED) {
            return;
        }
        LocalDateTime pausedAt = LocalDateTime.now();
        effectSchedule.pause(pausedAt);
        AggregateLifecycle.apply(new MaintenanceEffectSchedulePausedEvent(
                maintenanceId, effectSchedule.scheduleId(), command.reason().trim(), pausedAt,
                command.operatorId(), tenantId));
    }

    /** 记录一次持有租约的计划执行尝试。 */
    @CommandHandler
    public void handle(RecordMaintenanceEffectScheduleAttemptCommand command) {
        requireSameTenant(command.tenantId());
        requireSchedule(command.scheduleId());
        if (command.attemptId() != null && command.attemptId().equals(effectSchedule.lastAttemptId())) {
            return;
        }
        MaintenanceEffectSchedule attempted = effectSchedule.recordAttempt(
                command.attemptId(), command.attemptedAt());
        AggregateLifecycle.apply(new MaintenanceEffectScheduleAttemptedEvent(
                maintenanceId, attempted.scheduleId(), attempted.lastAttemptId(), attempted.attemptCount(),
                attempted.lastAttemptAt(), command.operatorId(), tenantId));
    }

    /** Policy 权威回执已记录后关闭计划。 */
    @CommandHandler
    public void handle(CompleteMaintenanceEffectScheduleCommand command) {
        requireSameTenant(command.tenantId());
        requireSchedule(command.scheduleId());
        if (effectSchedule.status() == MaintenanceEffectScheduleStatus.COMPLETED) {
            if (effectSchedule.lastAttemptId().equals(command.attemptId())) {
                return;
            }
            throw new MaintenanceValidationException(
                    "CompleteMaintenanceEffectScheduleCommand", "attemptId", "已完成计划的执行标识不匹配");
        }
        if (currentEffectStatus() != MaintenanceEffectStatus.APPLIED) {
            throw new MaintenanceValidationException(
                    "CompleteMaintenanceEffectScheduleCommand", "effectStatus", "案件尚未取得 Policy 权威成功回执");
        }
        effectSchedule.complete(command.attemptId(), command.completedAt());
        AggregateLifecycle.apply(new MaintenanceEffectScheduleCompletedEvent(
                maintenanceId, effectSchedule.scheduleId(), command.attemptId(), command.completedAt(),
                command.operatorId(), tenantId));
    }

    // ==================== 事件溯源处理器（业务状态重建；审计字段与联动由聚合根侧维护） ====================

    /** 生效状态变更回放：业务状态重建。审计字段与跨成员联动（案件状态、结算 / 流程成员镜像）由聚合根侧同名处理器维护（双监听为设计意图）。 */
    @EventSourcingHandler
    public void on(MaintenanceEffectStatusChangedEvent event) {
        this.effectStatus = event.currentStatus();
    }

    /** 未来生效计划建立回放：业务状态重建。审计字段由聚合根侧同名处理器维护（双监听为设计意图）。 */
    @EventSourcingHandler
    public void on(MaintenanceEffectScheduledEvent event) {
        this.effectSchedule = event.schedule();
    }

    /** 未来生效计划暂停回放：业务状态重建。审计字段由聚合根侧同名处理器维护（双监听为设计意图）。 */
    @EventSourcingHandler
    public void on(MaintenanceEffectSchedulePausedEvent event) {
        this.effectSchedule = effectSchedule.pause(event.pausedAt());
    }

    /** 未来生效计划恢复回放：业务状态重建。审计字段由聚合根侧同名处理器维护（双监听为设计意图）。 */
    @EventSourcingHandler
    public void on(MaintenanceEffectScheduleResumedEvent event) {
        this.effectSchedule = effectSchedule.resume(event.nextExecutionAt(), event.resumedAt());
    }

    /** 未来生效计划执行尝试回放：业务状态重建。审计字段由聚合根侧同名处理器维护（双监听为设计意图）。 */
    @EventSourcingHandler
    public void on(MaintenanceEffectScheduleAttemptedEvent event) {
        this.effectSchedule = effectSchedule.recordAttempt(event.attemptId(), event.attemptedAt());
    }

    /** 未来生效计划失败回放：业务状态重建。审计字段由聚合根侧同名处理器维护（双监听为设计意图）。 */
    @EventSourcingHandler
    public void on(MaintenanceEffectScheduleFailedEvent event) {
        this.effectSchedule = effectSchedule.recordFailure(
                event.attemptId(), event.errorCode(), event.errorMessage(), event.retryAt(),
                event.terminal(), event.failedAt());
    }

    /** 未来生效计划完成回放：业务状态重建。审计字段由聚合根侧同名处理器维护（双监听为设计意图）。 */
    @EventSourcingHandler
    public void on(MaintenanceEffectScheduleCompletedEvent event) {
        this.effectSchedule = effectSchedule.complete(event.attemptId(), event.completedAt());
    }

    /** 补偿事实记录回放：业务状态重建。审计字段由聚合根侧同名处理器维护（双监听为设计意图）。 */
    @EventSourcingHandler
    public void on(MaintenanceEffectCompensationRequiredEvent event) {
        this.effectCompensationEvidence = event.evidence();
        this.effectCompensationRequired = true;
    }

    /** 补偿事实结清回放：业务状态重建。审计字段由聚合根侧同名处理器维护（双监听为设计意图）。 */
    @EventSourcingHandler
    public void on(MaintenanceEffectCompensationResolvedEvent event) {
        this.effectCompensationRequired = false;
    }

    // ==================== 供聚合根留驻处理器转发的包级方法 ====================

    /** 校验计划标识：不存在或不匹配即拒绝（聚合根留驻的计划族处理器转发调用）。 */
    void requireSchedule(String scheduleId) {
        if (effectSchedule == null || scheduleId == null
                || !effectSchedule.scheduleId().equals(scheduleId.trim())) {
            throw new MaintenanceValidationException(
                    "MaintenanceEffectSchedule", "scheduleId", "未来生效计划不存在或不匹配");
        }
    }

    /** 当前生效状态（{@code null} 回退「未开始」；聚合根与本成员共用同一判定）。 */
    MaintenanceEffectStatus currentEffectStatus() {
        return effectStatus == null ? MaintenanceEffectStatus.NOT_STARTED : effectStatus;
    }

    /** 恢复未来生效计划（聚合根留驻处理器转发：恢复须联动流程任务重试，本成员不引流程主题）。 */
    void resume(LocalDateTime nextExecutionAt, LocalDateTime resumedAt) {
        effectSchedule.resume(nextExecutionAt, resumedAt);
    }

    /** 记录计划失败（聚合根留驻处理器转发：可重试失败须联动流程任务恢复，本成员不引流程主题）。 */
    void recordFailure(String attemptId, String errorCode, String errorMessage, LocalDateTime retryAt,
            boolean terminal, LocalDateTime failedAt) {
        effectSchedule.recordFailure(attemptId, errorCode, errorMessage, retryAt, terminal, failedAt);
    }

    /**
     * 校验命令携带的租户与案件归属租户一致（聚合根守卫的成员副本，规则逐字同源）。
     * <p>
     * 租户缺失、空白或不符一律以「保全不存在」拒绝——不区分「不存在」与「不属于本租户」，避免跨租户探测。
     * 成员实体的处理器同样直接处理外部命令（Axon 按 {@code @AggregateMember} 自动路由），故同受守护约束。
     * </p>
     *
     * @param commandTenantId 命令携带的租户ID
     */
    private void requireSameTenant(String commandTenantId) {
        if (commandTenantId == null || commandTenantId.isBlank() || !Objects.equals(commandTenantId, tenantId)) {
            throw new MaintenanceNotFoundException();
        }
    }
}
