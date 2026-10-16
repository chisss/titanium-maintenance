package com.titanium.maintenance.aggregate;

import java.time.LocalDateTime;
import java.util.Objects;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateLifecycle;

import com.titanium.maintenance.command.CompleteMaintenanceRetroactiveImpactAnalysisCommand;
import com.titanium.maintenance.command.CompleteMaintenanceRetroactivePeriodRecalculationCommand;
import com.titanium.maintenance.command.CompleteMaintenanceRetroactivePeriodResolutionCommand;
import com.titanium.maintenance.command.FailMaintenanceRetroactiveImpactAnalysisCommand;
import com.titanium.maintenance.command.FailMaintenanceRetroactivePeriodRecalculationCommand;
import com.titanium.maintenance.command.FailMaintenanceRetroactivePeriodResolutionCommand;
import com.titanium.maintenance.command.RecordMaintenanceRetroactiveProductRecalculationCommand;
import com.titanium.maintenance.command.StartMaintenanceRetroactiveImpactAnalysisCommand;
import com.titanium.maintenance.command.StartMaintenanceRetroactivePeriodRecalculationCommand;
import com.titanium.maintenance.command.StartMaintenanceRetroactivePeriodResolutionCommand;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactAnalysisStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactivePeriodRecalculationStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactivePeriodResolutionStatus;
import com.titanium.maintenance.common.exception.MaintenanceNotFoundException;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.event.MaintenanceRetroactiveImpactAnalysisCompletedEvent;
import com.titanium.maintenance.event.MaintenanceRetroactiveImpactAnalysisFailedEvent;
import com.titanium.maintenance.event.MaintenanceRetroactiveImpactAnalysisStartedEvent;
import com.titanium.maintenance.event.MaintenanceRetroactivePeriodRecalculationCompletedEvent;
import com.titanium.maintenance.event.MaintenanceRetroactivePeriodRecalculationFailedEvent;
import com.titanium.maintenance.event.MaintenanceRetroactivePeriodRecalculationStartedEvent;
import com.titanium.maintenance.event.MaintenanceRetroactivePeriodResolutionCompletedEvent;
import com.titanium.maintenance.event.MaintenanceRetroactivePeriodResolutionFailedEvent;
import com.titanium.maintenance.event.MaintenanceRetroactivePeriodResolutionStartedEvent;
import com.titanium.maintenance.event.MaintenanceRetroactiveProductRecalculationRecordedEvent;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveImpactAnalysis;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactivePeriodRecalculation;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactivePeriodResolution;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Maintenance 聚合的「追溯（Retroactive）处置」成员实体（DSN-01c 自聚合根下沉）。
 * <p>
 * <b>职责边界</b>：承载追溯三段链路——影响分析 → 期间重算 → 关闭期间处理——的状态与业务规则。
 * 三段状态均为不可变值对象（{@link MaintenanceRetroactiveImpactAnalysis} 等），本实体持有其最新版本，
 * 命令处理器校验后 apply 事件、由本实体的 {@code @EventSourcingHandler} 接管状态重建。
 * </p>
 * <p>
 * <b>成员不互引（🔴 勿破坏）</b>：本实体只持有自身主题的状态，不引用聚合内其它成员；
 * 快照序列化（Axon JacksonSerializer，字段可见性 ANY）会把成员内联进聚合 JSON，
 * 成员互引会导致同一对象被序列化两次、反序列化出相互独立的副本 ⇒ 状态不同步。
 * 跨主题协调（如依赖保全项/生效任务的处置）留在聚合根。
 * </p>
 * <p>
 * <b>镜像上下文（由聚合根同步，🔴 新增变更点时须同批维护）</b>：{@code initializationCompleted}
 * 在案件初始化完成事件（聚合根 {@code on(MaintenanceCaseInitializationCompletedEvent)}）时经
 * {@link #markInitialized()} 同步；{@code effectiveTimeType} / {@code specificEffectiveDate} 在创建时注入后不变。
 * </p>
 */
@Getter
@NoArgsConstructor
public class MaintenanceRetroactiveResolution {

    /** 租户ID（创建时注入，命令归属校验用） */
    private String                             tenantId;

    /** 聚合标识（创建时注入，事件发布携带） */
    private MaintenanceId                      maintenanceId;

    /** 案件是否已完成初始化（聚合根在初始化完成事件时同步） */
    private boolean                            initializationCompleted;

    /** 生效时间类型（创建时注入，追溯校验用；创建后不变） */
    private EffectiveTimeType                  effectiveTimeType;

    /** 指定生效时间（创建时注入，追溯分析起点校验用；创建后不变） */
    private LocalDateTime                      specificEffectiveDate;

    /** 追溯影响分析（最近一次版本） */
    private MaintenanceRetroactiveImpactAnalysis       retroactiveImpactAnalysis;

    /** 追溯期间重算（最近一次版本） */
    private MaintenanceRetroactivePeriodRecalculation  retroactivePeriodRecalculation;

    /** 追溯关闭期间处理（最近一次版本） */
    private MaintenanceRetroactivePeriodResolution     retroactivePeriodResolution;

    MaintenanceRetroactiveResolution(String tenantId, MaintenanceId maintenanceId, EffectiveTimeType effectiveTimeType,
            LocalDateTime specificEffectiveDate) {
        this.tenantId = tenantId;
        this.maintenanceId = maintenanceId;
        this.effectiveTimeType = effectiveTimeType;
        this.specificEffectiveDate = specificEffectiveDate;
    }

    /** 案件初始化完成时由聚合根同步（追溯三段链路均以「已初始化」为前置）。 */
    void markInitialized() {
        this.initializationCompleted = true;
    }

    /** 冻结一次追溯影响分析范围；新操作会形成递增版本。 */
    @CommandHandler
    public void handle(StartMaintenanceRetroactiveImpactAnalysisCommand command) {
        requireSameTenant(command.tenantId());
        if (!initializationCompleted || effectiveTimeType != EffectiveTimeType.RETROACTIVE
                || specificEffectiveDate == null) {
            throw new MaintenanceValidationException(
                    "StartMaintenanceRetroactiveImpactAnalysisCommand", "case", "只有已初始化的追溯案件可以分析影响");
        }
        if (!specificEffectiveDate.equals(command.scopeFrom())) {
            throw new MaintenanceValidationException(
                    "StartMaintenanceRetroactiveImpactAnalysisCommand", "scopeFrom", "分析起点必须等于案件追溯生效时间");
        }
        if (retroactiveImpactAnalysis != null
                && retroactiveImpactAnalysis.operationId().equals(command.operationId())) {
            if (retroactiveImpactAnalysis.sameStartRequest(command.operationId(), command.requestHash())) {
                return;
            }
            throw new MaintenanceValidationException(
                    "StartMaintenanceRetroactiveImpactAnalysisCommand", "operationId", "同一操作标识的分析请求不一致");
        }
        if (retroactiveImpactAnalysis != null
                && retroactiveImpactAnalysis.status() == MaintenanceRetroactiveImpactAnalysisStatus.ANALYZING) {
            throw new MaintenanceValidationException(
                    "StartMaintenanceRetroactiveImpactAnalysisCommand", "status", "已有追溯影响分析正在执行");
        }
        int nextVersion = retroactiveImpactAnalysis == null ? 1 : retroactiveImpactAnalysis.analysisVersion() + 1;
        MaintenanceRetroactiveImpactAnalysis analysis = MaintenanceRetroactiveImpactAnalysis.start(
                command.analysisId(), nextVersion, command.operationId(), command.requestHash(),
                command.scopeFrom(), command.scopeTo(), command.startedAt());
        AggregateLifecycle.apply(new MaintenanceRetroactiveImpactAnalysisStartedEvent(
                maintenanceId, analysis, command.operatorId(), tenantId));
    }

    /** 保存全部权威域的结构化影响清单；完成分析不改变案件生效状态。 */
    @CommandHandler
    public void handle(CompleteMaintenanceRetroactiveImpactAnalysisCommand command) {
        requireSameTenant(command.tenantId());
        requireRetroactiveImpactAnalysis(command.analysisId(), command.operationId());
        MaintenanceRetroactiveImpactAnalysis completed = retroactiveImpactAnalysis.complete(
                command.coveredDomains(), command.items(), command.evidenceVersion(),
                command.resultHash(), command.completedAt());
        if (completed == retroactiveImpactAnalysis) {
            return;
        }
        AggregateLifecycle.apply(new MaintenanceRetroactiveImpactAnalysisCompletedEvent(
                maintenanceId, completed, command.operatorId(), tenantId));
    }

    /** 权威取证失败时记录可重试失败事实。 */
    @CommandHandler
    public void handle(FailMaintenanceRetroactiveImpactAnalysisCommand command) {
        requireSameTenant(command.tenantId());
        requireRetroactiveImpactAnalysis(command.analysisId(), command.operationId());
        MaintenanceRetroactiveImpactAnalysis failed = retroactiveImpactAnalysis.fail(
                command.failureCode(), command.failureMessage(), command.failedAt());
        if (failed == retroactiveImpactAnalysis) {
            return;
        }
        AggregateLifecycle.apply(new MaintenanceRetroactiveImpactAnalysisFailedEvent(
                maintenanceId, failed, command.operatorId(), tenantId));
    }

    /** 冻结影响分析版本及本次 Product/Billing 期间重算请求。 */
    @CommandHandler
    public void handle(StartMaintenanceRetroactivePeriodRecalculationCommand command) {
        requireSameTenant(command.tenantId());
        if (!initializationCompleted || effectiveTimeType != EffectiveTimeType.RETROACTIVE
                || retroactiveImpactAnalysis == null
                || retroactiveImpactAnalysis.status() != MaintenanceRetroactiveImpactAnalysisStatus.COMPLETED) {
            throw new MaintenanceValidationException(
                    "StartMaintenanceRetroactivePeriodRecalculationCommand", "analysis", "必须先完成追溯影响分析");
        }
        if (!retroactiveImpactAnalysis.analysisId().equals(command.analysisId())
                || retroactiveImpactAnalysis.analysisVersion() != command.analysisVersion()
                || !retroactiveImpactAnalysis.resultHash().equals(command.analysisResultHash())) {
            throw new MaintenanceValidationException(
                    "StartMaintenanceRetroactivePeriodRecalculationCommand", "analysis", "影响分析版本或摘要已经漂移");
        }
        if (retroactivePeriodRecalculation != null
                && retroactivePeriodRecalculation.operationId().equals(command.operationId())) {
            if (retroactivePeriodRecalculation.sameRequest(command.operationId(), command.requestHash())) {
                return;
            }
            throw new MaintenanceValidationException(
                    "StartMaintenanceRetroactivePeriodRecalculationCommand", "operationId", "同一操作标识的重算请求不一致");
        }
        if (retroactivePeriodRecalculation != null
                && retroactivePeriodRecalculation.status()
                        != MaintenanceRetroactivePeriodRecalculationStatus.FAILED
                && !retroactivePeriodRecalculation.terminal()) {
            throw new MaintenanceValidationException(
                    "StartMaintenanceRetroactivePeriodRecalculationCommand", "status", "已有追溯期间重算正在执行");
        }
        int nextVersion = retroactivePeriodRecalculation == null
                ? 1 : retroactivePeriodRecalculation.periodRecalculationVersion() + 1;
        MaintenanceRetroactivePeriodRecalculation recalculation =
                MaintenanceRetroactivePeriodRecalculation.start(
                        command.periodRecalculationId(), nextVersion, command.operationId(), command.requestHash(),
                        command.analysisId(), command.analysisVersion(), command.analysisResultHash(),
                        command.startedAt());
        AggregateLifecycle.apply(new MaintenanceRetroactivePeriodRecalculationStartedEvent(
                maintenanceId, recalculation, command.operatorId(), tenantId));
    }

    /** 保存 Product 期间重算权威检查点，Billing 失败后重试可复用。 */
    @CommandHandler
    public void handle(RecordMaintenanceRetroactiveProductRecalculationCommand command) {
        requireSameTenant(command.tenantId());
        requireRetroactivePeriodRecalculation(command.periodRecalculationId(), command.operationId());
        MaintenanceRetroactivePeriodRecalculation recalculation = retroactivePeriodRecalculation.recordProduct(
                command.evidence(), command.recordedAt());
        if (recalculation == retroactivePeriodRecalculation) {
            return;
        }
        AggregateLifecycle.apply(new MaintenanceRetroactiveProductRecalculationRecordedEvent(
                maintenanceId, recalculation, command.operatorId(), tenantId));
    }

    /** 保存 Billing 期间调整或关闭期间复核事实。 */
    @CommandHandler
    public void handle(CompleteMaintenanceRetroactivePeriodRecalculationCommand command) {
        requireSameTenant(command.tenantId());
        requireRetroactivePeriodRecalculation(command.periodRecalculationId(), command.operationId());
        MaintenanceRetroactivePeriodRecalculation recalculation = retroactivePeriodRecalculation.completeBilling(
                command.evidence(), command.completedAt());
        if (recalculation == retroactivePeriodRecalculation) {
            return;
        }
        AggregateLifecycle.apply(new MaintenanceRetroactivePeriodRecalculationCompletedEvent(
                maintenanceId, recalculation, command.operatorId(), tenantId));
    }

    /** 保存重算失败事实，同时保留已经成功的 Product 检查点。 */
    @CommandHandler
    public void handle(FailMaintenanceRetroactivePeriodRecalculationCommand command) {
        requireSameTenant(command.tenantId());
        requireRetroactivePeriodRecalculation(command.periodRecalculationId(), command.operationId());
        MaintenanceRetroactivePeriodRecalculation recalculation = retroactivePeriodRecalculation.fail(
                command.failureCode(), command.failureMessage(), command.failedAt());
        if (recalculation == retroactivePeriodRecalculation) {
            return;
        }
        AggregateLifecycle.apply(new MaintenanceRetroactivePeriodRecalculationFailedEvent(
                maintenanceId, recalculation, command.operatorId(), tenantId));
    }

    /** 冻结关闭会计期间处理请求，只允许处理当前 Billing 复核批次。 */
    @CommandHandler
    public void handle(StartMaintenanceRetroactivePeriodResolutionCommand command) {
        requireSameTenant(command.tenantId());
        if (retroactivePeriodRecalculation == null
                || retroactivePeriodRecalculation.status()
                        != MaintenanceRetroactivePeriodRecalculationStatus.REVIEW_REQUIRED
                || retroactivePeriodRecalculation.billingEvidence() == null
                || retroactivePeriodRecalculation.billingEvidence().reviewCount() < 1) {
            throw new MaintenanceValidationException(
                    "StartMaintenanceRetroactivePeriodResolutionCommand", "recalculation",
                    "必须先完成存在关闭期间复核项的期间重算");
        }
        var billingEvidence = retroactivePeriodRecalculation.billingEvidence();
        if (!billingEvidence.batchId().equals(command.billingBatchId())
                || !billingEvidence.resultHash().equals(command.sourceBatchResultHash())) {
            throw new MaintenanceValidationException(
                    "StartMaintenanceRetroactivePeriodResolutionCommand", "billingBatch",
                    "关闭期间处理请求与当前Billing批次不一致");
        }
        if (retroactivePeriodResolution != null
                && retroactivePeriodResolution.operationId().equals(command.operationId())) {
            if (retroactivePeriodResolution.sameRequest(command.operationId(), command.requestHash())) {
                return;
            }
            throw new MaintenanceValidationException(
                    "StartMaintenanceRetroactivePeriodResolutionCommand", "operationId",
                    "同一操作标识的关闭期间处理请求不一致");
        }
        if (retroactivePeriodResolution != null
                && retroactivePeriodResolution.status()
                        != MaintenanceRetroactivePeriodResolutionStatus.FAILED) {
            throw new MaintenanceValidationException(
                    "StartMaintenanceRetroactivePeriodResolutionCommand", "status",
                    "已有关闭期间处理正在执行或已经完成");
        }
        MaintenanceRetroactivePeriodResolution resolution = MaintenanceRetroactivePeriodResolution.start(
                command.periodResolutionId(), command.operationId(), command.requestHash(),
                command.billingBatchId(), command.sourceBatchResultHash(), command.targetAccountingPeriod(),
                command.reason(), command.startedAt());
        AggregateLifecycle.apply(new MaintenanceRetroactivePeriodResolutionStartedEvent(
                maintenanceId, resolution, command.operatorId(), tenantId));
    }

    /** 保存 Billing 关闭期间处理权威结论。 */
    @CommandHandler
    public void handle(CompleteMaintenanceRetroactivePeriodResolutionCommand command) {
        requireSameTenant(command.tenantId());
        requireRetroactivePeriodResolution(command.periodResolutionId(), command.operationId());
        if (command.evidence() == null) {
            throw new MaintenanceValidationException(
                    "CompleteMaintenanceRetroactivePeriodResolutionCommand", "evidence",
                    "Billing关闭期间处理结论不能为空");
        }
        MaintenanceRetroactivePeriodResolution resolution = retroactivePeriodResolution.complete(
                command.evidence(), command.completedAt());
        if (resolution == retroactivePeriodResolution) {
            return;
        }
        AggregateLifecycle.apply(new MaintenanceRetroactivePeriodResolutionCompletedEvent(
                maintenanceId, resolution, command.operatorId(), tenantId));
    }

    /** 保存关闭期间处理失败事实，允许同一请求幂等续跑。 */
    @CommandHandler
    public void handle(FailMaintenanceRetroactivePeriodResolutionCommand command) {
        requireSameTenant(command.tenantId());
        requireRetroactivePeriodResolution(command.periodResolutionId(), command.operationId());
        MaintenanceRetroactivePeriodResolution resolution = retroactivePeriodResolution.fail(
                command.failureCode(), command.failureMessage(), command.failedAt());
        if (resolution == retroactivePeriodResolution) {
            return;
        }
        AggregateLifecycle.apply(new MaintenanceRetroactivePeriodResolutionFailedEvent(
                maintenanceId, resolution, command.operatorId(), tenantId));
    }

    // ==================== 事件溯源处理器（业务状态，审计字段由聚合根同事件处理器维护） ====================

    @EventSourcingHandler
    public void on(MaintenanceRetroactiveImpactAnalysisStartedEvent event) {
        this.retroactiveImpactAnalysis = event.analysis();
    }

    @EventSourcingHandler
    public void on(MaintenanceRetroactiveImpactAnalysisCompletedEvent event) {
        this.retroactiveImpactAnalysis = event.analysis();
    }

    @EventSourcingHandler
    public void on(MaintenanceRetroactiveImpactAnalysisFailedEvent event) {
        this.retroactiveImpactAnalysis = event.analysis();
    }

    @EventSourcingHandler
    public void on(MaintenanceRetroactivePeriodRecalculationStartedEvent event) {
        this.retroactivePeriodRecalculation = event.recalculation();
    }

    @EventSourcingHandler
    public void on(MaintenanceRetroactiveProductRecalculationRecordedEvent event) {
        this.retroactivePeriodRecalculation = event.recalculation();
    }

    @EventSourcingHandler
    public void on(MaintenanceRetroactivePeriodRecalculationCompletedEvent event) {
        this.retroactivePeriodRecalculation = event.recalculation();
    }

    @EventSourcingHandler
    public void on(MaintenanceRetroactivePeriodRecalculationFailedEvent event) {
        this.retroactivePeriodRecalculation = event.recalculation();
    }

    @EventSourcingHandler
    public void on(MaintenanceRetroactivePeriodResolutionStartedEvent event) {
        this.retroactivePeriodResolution = event.resolution();
    }

    @EventSourcingHandler
    public void on(MaintenanceRetroactivePeriodResolutionCompletedEvent event) {
        this.retroactivePeriodResolution = event.resolution();
    }

    @EventSourcingHandler
    public void on(MaintenanceRetroactivePeriodResolutionFailedEvent event) {
        this.retroactivePeriodResolution = event.resolution();
    }

    // ==================== 私有规则 ====================

    private void requireRetroactiveImpactAnalysis(String analysisId, String operationId) {
        if (retroactiveImpactAnalysis == null || analysisId == null || operationId == null
                || !retroactiveImpactAnalysis.analysisId().equals(analysisId.trim())
                || !retroactiveImpactAnalysis.operationId().equals(operationId.trim())) {
            throw new MaintenanceValidationException(
                    "MaintenanceRetroactiveImpactAnalysis", "analysisId", "追溯影响分析不存在或操作标识不匹配");
        }
    }

    private void requireRetroactivePeriodRecalculation(String recalculationId, String operationId) {
        if (retroactivePeriodRecalculation == null || recalculationId == null || operationId == null
                || !retroactivePeriodRecalculation.periodRecalculationId().equals(recalculationId.trim())
                || !retroactivePeriodRecalculation.operationId().equals(operationId.trim())) {
            throw new MaintenanceValidationException(
                    "MaintenanceRetroactivePeriodRecalculation", "periodRecalculationId",
                    "追溯期间重算不存在或操作标识不匹配");
        }
    }

    private void requireRetroactivePeriodResolution(String resolutionId, String operationId) {
        if (retroactivePeriodResolution == null || resolutionId == null || operationId == null
                || !retroactivePeriodResolution.periodResolutionId().equals(resolutionId.trim())
                || !retroactivePeriodResolution.operationId().equals(operationId.trim())) {
            throw new MaintenanceValidationException(
                    "MaintenanceRetroactivePeriodResolution", "periodResolutionId",
                    "关闭会计期间处理不存在或操作标识不匹配");
        }
    }

    /**
     * 跨租户写入守护（与聚合根同名规则逐字同规，失败关闭）：租户缺失、空白或不符
     * 一律以「保全不存在」拒绝，不区分「不存在」与「不属于本租户」，避免跨租户探测。
     */
    private void requireSameTenant(String commandTenantId) {
        if (commandTenantId == null || commandTenantId.isBlank() || !Objects.equals(commandTenantId, tenantId)) {
            throw new MaintenanceNotFoundException();
        }
    }
}
