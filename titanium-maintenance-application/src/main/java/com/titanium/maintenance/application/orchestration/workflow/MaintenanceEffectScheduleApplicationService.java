package com.titanium.maintenance.application.orchestration.workflow;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import com.titanium.maintenance.application.command.MaintenanceEffectApplicationInput;
import com.titanium.maintenance.application.command.MaintenanceEffectScheduleOperationInput;
import com.titanium.maintenance.application.configuration.MaintenanceEffectSchedulingProperties;
import com.titanium.maintenance.application.model.MaintenanceEffectApplicationResult;
import com.titanium.maintenance.application.model.MaintenanceEffectScheduleResult;
import com.titanium.maintenance.command.CompleteMaintenanceEffectScheduleCommand;
import com.titanium.maintenance.command.PauseMaintenanceEffectScheduleCommand;
import com.titanium.maintenance.command.RecordMaintenanceEffectScheduleAttemptCommand;
import com.titanium.maintenance.command.RecordMaintenanceEffectScheduleFailureCommand;
import com.titanium.maintenance.command.ResumeMaintenanceEffectScheduleCommand;
import com.titanium.maintenance.command.ScheduleMaintenanceEffectCommand;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceBillingPostingStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceEffectScheduleStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceEffectStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceFundSettlementStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenancePremiumQuoteStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowTaskStatus;
import com.titanium.maintenance.common.exception.BusinessException;
import com.titanium.maintenance.common.exception.MaintenanceNotFoundException;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.port.BillingPremiumLifecyclePort;
import com.titanium.maintenance.port.BillingPremiumLifecyclePort.PostingFact;
import com.titanium.maintenance.port.BillingPremiumLifecyclePort.PostingRequest;
import com.titanium.maintenance.port.MaintenanceEffectScheduleLeasePort;
import com.titanium.maintenance.port.MaintenanceEffectScheduleLeasePort.ScheduleLease;
import com.titanium.maintenance.port.PaymentPremiumCollectionPort;
import com.titanium.maintenance.port.PolicyMaintenanceSnapshotPort;
import com.titanium.maintenance.port.PolicyMaintenanceSnapshotPort.PolicyMaintenanceSnapshotRequest;
import com.titanium.maintenance.port.TenantTimeZonePort;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceWorkflowTaskViewRepository;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.query.view.MaintenanceWorkflowTaskView;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.casecreation.PolicyMaintenanceSnapshot;
import com.titanium.maintenance.valueobject.workflow.MaintenanceEffectSchedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 未来生效计划管理、权威重校验与租约执行编排。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaintenanceEffectScheduleApplicationService {

    private static final Set<String> PAYMENT_SUCCEEDED = Set.of("SUCCESS", "SUCCEEDED", "PAID", "COMPLETED");
    private static final String SCHEDULER_OPERATOR = "maintenance-effect-scheduler";

    private final CommandGateway commandGateway;
    private final MaintenanceViewRepository maintenanceViewRepository;
    private final MaintenanceWorkflowTaskViewRepository taskViewRepository;
    private final PolicyMaintenanceSnapshotPort policySnapshotPort;
    private final BillingPremiumLifecyclePort billingPort;
    private final PaymentPremiumCollectionPort paymentPort;
    private final MaintenanceEffectApplicationService effectApplicationService;
    private final MaintenanceEffectScheduleLeasePort leasePort;
    private final TenantTimeZonePort tenantTimeZonePort;
    private final MaintenanceEffectSchedulingProperties properties;

    private final String schedulerOwner = "scheduler:" + UUID.randomUUID();

    public CompletableFuture<MaintenanceEffectScheduleResult> create(
            MaintenanceEffectScheduleOperationInput input) {
        MaintenanceView view = requireCase(input);
        if (!MaintenanceEffectSchedule.supportsScheduling(view.getEffectiveTimeType())) {
            throw validation("effectiveTimeType", "当前案件不是未来生效案件");
        }
        String zoneId = tenantTimeZonePort.resolveZoneId(input.tenantId());
        LocalDateTime tenantExecutionAt = resolveNextExecutionAt(view, input.tenantId(), zoneId);
        if (!tenantExecutionAt.isAfter(LocalDateTime.now(ZoneId.of(zoneId)))) {
            throw validation("nextExecutionAt", "未来生效计划时间必须晚于当前租户时间");
        }
        LocalDateTime nextExecutionAt = toUtc(tenantExecutionAt, zoneId);
        ScheduleMaintenanceEffectCommand command = new ScheduleMaintenanceEffectCommand(
                MaintenanceId.of(input.maintenanceId()), scheduleId(input.maintenanceId()),
                zoneId, nextExecutionAt, input.operatorId());
        return send(command).thenApply(ignored -> new MaintenanceEffectScheduleResult(
                command.scheduleId(), view.getEffectiveTimeType(), MaintenanceEffectScheduleStatus.ACTIVE,
                zoneId, nextExecutionAt, 0, null, null, null, null));
    }

    public CompletableFuture<MaintenanceEffectScheduleResult> pause(
            MaintenanceEffectScheduleOperationInput input) {
        MaintenanceView view = requireSchedule(input);
        return send(new PauseMaintenanceEffectScheduleCommand(
                MaintenanceId.of(input.maintenanceId()), view.getEffectScheduleId(),
                requireReason(input), input.operatorId()))
                .thenApply(ignored -> result(view, MaintenanceEffectScheduleStatus.PAUSED,
                        view.getEffectScheduleNextExecutionAt(), view.getEffectScheduleLastErrorCode(),
                        view.getEffectScheduleLastErrorMessage()));
    }

    public CompletableFuture<MaintenanceEffectScheduleResult> resume(
            MaintenanceEffectScheduleOperationInput input) {
        MaintenanceView view = requireSchedule(input);
        LocalDateTime now = utcNow();
        LocalDateTime nextExecutionAt = view.getEffectScheduleNextExecutionAt().isAfter(now)
                ? view.getEffectScheduleNextExecutionAt() : now;
        return send(new ResumeMaintenanceEffectScheduleCommand(
                MaintenanceId.of(input.maintenanceId()), view.getEffectScheduleId(), input.operationId(),
                nextExecutionAt, requireReason(input), input.operatorId()))
                .thenApply(ignored -> result(
                        view, MaintenanceEffectScheduleStatus.ACTIVE, nextExecutionAt, null, null));
    }

    public MaintenanceEffectApplicationResult executeNow(MaintenanceEffectScheduleOperationInput input) {
        MaintenanceView view = requireSchedule(input);
        MaintenanceEffectApplicationResult completed = completedScheduleResult(view);
        if (completed != null) {
            return completed;
        }
        LocalDateTime now = utcNow();
        if (view.getEffectScheduleNextExecutionAt().isAfter(now)) {
            throw validation("nextExecutionAt", "未来生效计划尚未到达执行时点");
        }
        String owner = "manual:" + input.operatorId() + ":" + input.operationId();
        ScheduleLease lease = leasePort.acquireNow(
                        input.tenantId(), input.maintenanceId(), owner, now,
                        now.plus(properties.getLeaseDuration()))
                .orElseThrow(() -> validation("lease", "计划正在由其他节点执行或当前状态不可执行"));
        return executeLease(lease, owner, input.operatorId(), input.source());
    }

    public void executeDue() {
        LocalDateTime now = utcNow();
        List<ScheduleLease> leases = leasePort.acquireDue(
                schedulerOwner, now, now.plus(properties.getLeaseDuration()), properties.getBatchSize());
        for (ScheduleLease lease : leases) {
            try {
                executeLease(lease, schedulerOwner, SCHEDULER_OPERATOR, MaintenanceChannel.API);
            } catch (RuntimeException exception) {
                log.warn("未来生效计划执行失败: maintenanceId={}, error={}",
                        lease.maintenanceId(), safeMessage(rootCause(exception)));
            }
        }
    }

    private MaintenanceEffectApplicationResult executeLease(
            ScheduleLease lease,
            String leaseOwner,
            String operatorId,
            MaintenanceChannel source) {
        String attemptId = lease.scheduleId() + ":attempt:" + (lease.attemptCount() + 1);
        LocalDateTime attemptedAt = utcNow();
        boolean attemptRecorded = false;
        boolean effectApplied = false;
        try {
            MaintenanceEffectApplicationResult recovered = completeAppliedSchedule(lease, operatorId);
            if (recovered != null) {
                return recovered;
            }
            send(new RecordMaintenanceEffectScheduleAttemptCommand(
                    MaintenanceId.of(lease.maintenanceId()), lease.scheduleId(), attemptId,
                    attemptedAt, operatorId)).join();
            attemptRecorded = true;
            LocalDateTime tenantEffectiveAt = fromUtc(lease.nextExecutionAt(), lease.tenantZoneId());
            revalidate(lease, operatorId, fromUtc(attemptedAt, lease.tenantZoneId()));
            MaintenanceEffectApplicationResult result = effectApplicationService.applyScheduled(
                    new MaintenanceEffectApplicationInput(
                            lease.maintenanceId(), lease.effectTaskId(), attemptId,
                            operatorId, lease.tenantId(), source),
                    tenantEffectiveAt).join();
            effectApplied = true;
            send(new CompleteMaintenanceEffectScheduleCommand(
                    MaintenanceId.of(lease.maintenanceId()), lease.scheduleId(), attemptId,
                    utcNow(), operatorId)).join();
            return result;
        } catch (RuntimeException exception) {
            Throwable cause = rootCause(exception);
            if (attemptRecorded && !effectApplied) {
                recordFailure(lease, attemptId, attemptedAt, operatorId, cause);
            }
            throw cause instanceof RuntimeException runtimeException
                    ? runtimeException : new IllegalStateException(cause);
        } finally {
            leasePort.release(lease.maintenanceId(), leaseOwner);
        }
    }

    private MaintenanceEffectApplicationResult completeAppliedSchedule(ScheduleLease lease, String operatorId) {
        Optional<MaintenanceView> candidate = maintenanceViewRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        lease.maintenanceId(), lease.tenantId());
        if (candidate.isEmpty() || candidate.get().getEffectStatus() != MaintenanceEffectStatus.APPLIED) {
            return null;
        }
        MaintenanceView view = candidate.get();
        if (blank(view.getEffectScheduleLastAttemptId())) {
            throw validation("lastAttemptId", "已生效案件缺少可关闭计划的最近执行标识");
        }
        MaintenanceWorkflowTaskView appliedTask = taskViewRepository
                .findByTenantIdAndMaintenanceIdOrderByItemOrderAscSequenceAsc(
                        lease.tenantId(), lease.maintenanceId())
                .stream()
                .filter(task -> task.getStepType() == MaintenanceStepType.EFFECT)
                .filter(task -> task.getStatus() != MaintenanceWorkflowTaskStatus.SKIPPED)
                .filter(this::hasPolicyApplicationResult)
                .findFirst()
                .orElseThrow(() -> validation("policyApplication", "已生效案件缺少 Policy 权威回执投影"));
        send(new CompleteMaintenanceEffectScheduleCommand(
                MaintenanceId.of(lease.maintenanceId()), lease.scheduleId(),
                view.getEffectScheduleLastAttemptId(), utcNow(), operatorId)).join();
        return applicationResult(appliedTask);
    }

    private MaintenanceEffectApplicationResult completedScheduleResult(MaintenanceView view) {
        if (view.getEffectScheduleStatus() != MaintenanceEffectScheduleStatus.COMPLETED
                || view.getEffectStatus() != MaintenanceEffectStatus.APPLIED) {
            return null;
        }
        MaintenanceWorkflowTaskView appliedTask = taskViewRepository
                .findByTenantIdAndMaintenanceIdOrderByItemOrderAscSequenceAsc(
                        view.getTenantId(), view.getMaintenanceId())
                .stream()
                .filter(task -> task.getStepType() == MaintenanceStepType.EFFECT)
                .filter(task -> task.getStatus() != MaintenanceWorkflowTaskStatus.SKIPPED)
                .filter(this::hasPolicyApplicationResult)
                .findFirst()
                .orElseThrow(() -> validation("policyApplication", "已完成计划缺少 Policy 权威回执投影"));
        return applicationResult(appliedTask);
    }

    private MaintenanceEffectApplicationResult applicationResult(MaintenanceWorkflowTaskView task) {
        return new MaintenanceEffectApplicationResult(
                task.getEffectRequestId(), task.getPolicyEndorsementNo(),
                task.getPolicyActualVersion(), task.getPolicyApplicationHash(),
                task.getPolicyAppliedAt());
    }

    private boolean hasPolicyApplicationResult(MaintenanceWorkflowTaskView task) {
        return !blank(task.getEffectRequestId())
                && !blank(task.getPolicyEndorsementNo())
                && task.getPolicyActualVersion() != null
                && !blank(task.getPolicyApplicationHash())
                && task.getPolicyAppliedAt() != null;
    }

    private void recordFailure(
            ScheduleLease lease,
            String attemptId,
            LocalDateTime attemptedAt,
            String operatorId,
            Throwable cause) {
        int attemptNumber = lease.attemptCount() + 1;
        boolean terminal = terminal(cause) || attemptNumber >= properties.getMaxAttempts();
        LocalDateTime retryAt = terminal ? null : attemptedAt.plus(properties.getRetryDelay());
        try {
            send(new RecordMaintenanceEffectScheduleFailureCommand(
                    MaintenanceId.of(lease.maintenanceId()), lease.scheduleId(), attemptId,
                    errorCode(cause), safeMessage(cause), retryAt, terminal, operatorId)).join();
        } catch (RuntimeException failureException) {
            Throwable failureCause = rootCause(failureException);
            if (failureCause != cause) {
                cause.addSuppressed(failureCause);
            }
            log.error("未来生效计划失败事实记录失败: maintenanceId={}, attemptId={}, error={}",
                    lease.maintenanceId(), attemptId, safeMessage(failureCause));
        }
    }

    private void revalidate(ScheduleLease lease, String operatorId, LocalDateTime now) {
        MaintenanceView view = maintenanceViewRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        lease.maintenanceId(), lease.tenantId())
                .orElseThrow(MaintenanceNotFoundException::new);
        PolicyMaintenanceSnapshot current = policySnapshotPort.capture(
                new PolicyMaintenanceSnapshotRequest(view.getPolicyId(), lease.tenantId()));
        if (!Objects.equals(current.policyId().id(), view.getPolicyId())
                || current.policyVersion() != view.getPolicyBaselineVersion()) {
            throw validation("policyVersion", "到期 Policy 版本已变化，禁止按旧基准自动生效");
        }
        List<MaintenanceWorkflowTaskView> tasks = taskViewRepository
                .findByTenantIdAndMaintenanceIdOrderByItemOrderAscSequenceAsc(
                        lease.tenantId(), lease.maintenanceId());
        validateEffectTasks(tasks);
        tasks.stream()
                .filter(task -> task.getStepType() == MaintenanceStepType.FEE_SETTLEMENT)
                .filter(task -> task.getStatus() != MaintenanceWorkflowTaskStatus.SKIPPED)
                .forEach(task -> revalidateFinancialFact(view, task, operatorId, now));
    }

    private void validateEffectTasks(List<MaintenanceWorkflowTaskView> tasks) {
        List<MaintenanceWorkflowTaskView> effects = tasks.stream()
                .filter(task -> task.getStepType() == MaintenanceStepType.EFFECT)
                .filter(task -> task.getStatus() != MaintenanceWorkflowTaskStatus.SKIPPED)
                .toList();
        if (effects.isEmpty() || effects.stream().anyMatch(task ->
                task.getStatus() != MaintenanceWorkflowTaskStatus.READY
                        && task.getStatus() != MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL)) {
            throw new MaintenanceEffectSchedulePendingException("到期时案件流程尚未进入可生效状态");
        }
    }

    private void revalidateFinancialFact(
            MaintenanceView view,
            MaintenanceWorkflowTaskView task,
            String operatorId,
            LocalDateTime now) {
        if (task.getPremiumQuoteStatus() == MaintenancePremiumQuoteStatus.NOT_REQUIRED) {
            return;
        }
        if (task.getPremiumQuoteStatus() != MaintenancePremiumQuoteStatus.QUOTED
                || task.getPremiumQuoteId() == null || task.getPremiumQuoteResultHash() == null) {
            throw validation("premiumQuote", "到期时缺少可勾稽的 Product 报价事实");
        }
        if (task.getBillingPostingId() == null
                && task.getPremiumQuoteValidUntil() != null
                && !now.isBefore(task.getPremiumQuoteValidUntil())) {
            throw validation("premiumQuote", "到期时 Product 报价已过期且尚未形成有效入账");
        }
        PostingFact posting = billingPort.post(new PostingRequest(
                view.getTenantId(), task.getPremiumQuoteId(), task.getPremiumQuoteResultHash(),
                view.getPolicyId(), view.getCustomerId(), operatorId));
        validatePosting(task, posting);
        switch (posting.direction()) {
            case NONE -> {
                if (task.getFundSettlementStatus() != MaintenanceFundSettlementStatus.NOT_REQUIRED) {
                    throw validation("fundSettlement", "无差额入账缺少无需资金处理事实");
                }
            }
            case DEBIT -> validateCollection(view, task, posting);
            case CREDIT -> validateRefund(task, posting);
        }
    }

    private void validatePosting(MaintenanceWorkflowTaskView task, PostingFact posting) {
        if (posting == null || !Objects.equals(posting.postingId(), task.getBillingPostingId())
                || !Objects.equals(posting.adjustmentId(), task.getPremiumQuoteId())
                || !Objects.equals(posting.resultHash(), task.getPremiumQuoteResultHash())
                || posting.direction() != task.getPremiumQuoteDirection()
                || compare(posting.amount(), task.getPremiumQuoteAmount()) != 0
                || !equalsIgnoreCase(posting.currency(), task.getPremiumQuoteCurrency())
                || MaintenanceBillingPostingStatus.fromCode(posting.status())
                        != MaintenanceBillingPostingStatus.POSTED) {
            throw validation("billingPosting", "到期 Billing 当前事实与案件报价/入账检查点不一致");
        }
    }

    private void validateCollection(
            MaintenanceView view,
            MaintenanceWorkflowTaskView task,
            PostingFact posting) {
        if (task.getFundSettlementOrderId() == null) {
            throw validation("fundSettlement", "到期收款缺少 Payment 单号");
        }
        var payment = paymentPort.get(view.getTenantId(), task.getFundSettlementOrderId());
        if (payment == null || !PAYMENT_SUCCEEDED.contains(normalize(payment.status()))
                || !Objects.equals(payment.policyId(), view.getPolicyId())
                || !Objects.equals(payment.customerId(), view.getCustomerId())
                || compare(payment.amount(), posting.amount()) != 0
                || !equalsIgnoreCase(payment.currency(), posting.currency())) {
            throw validation("fundSettlement", "到期 Payment 收款事实未成功或未通过案件勾稽");
        }
    }

    private void validateRefund(MaintenanceWorkflowTaskView task, PostingFact posting) {
        if (!PAYMENT_SUCCEEDED.contains(normalize(posting.refundStatus()))
                || !Objects.equals(posting.refundInstructionId(), task.getFundSettlementInstructionId())
                || !Objects.equals(posting.refundOrderId(), task.getFundSettlementOrderId())) {
            throw validation("fundSettlement", "到期 Payment 退款事实未成功或未通过案件勾稽");
        }
    }

    private LocalDateTime resolveNextExecutionAt(MaintenanceView view, String tenantId, String zoneId) {
        if (view.getEffectiveTimeType() == EffectiveTimeType.FUTURE
                || view.getEffectiveTimeType() == EffectiveTimeType.SPECIFIED_DATE) {
            if (view.getSpecificEffectiveDate() == null) {
                throw validation("specificEffectiveDate", "未来或指定日案件缺少生效时间");
            }
            return view.getSpecificEffectiveDate();
        }
        PolicyMaintenanceSnapshot snapshot = policySnapshotPort.capture(
                new PolicyMaintenanceSnapshotRequest(view.getPolicyId(), tenantId));
        var source = view.getEffectiveTimeType() == EffectiveTimeType.NEXT_BILLING_DATE
                ? snapshot.nextBillingDateAt() : snapshot.nextPolicyAnniversaryAt();
        if (source == null) {
            throw validation("nextExecutionAt", "Policy 未提供当前生效类型对应的未来日期");
        }
        return source.atZoneSameInstant(ZoneId.of(zoneId)).toLocalDateTime();
    }

    private MaintenanceView requireCase(MaintenanceEffectScheduleOperationInput input) {
        validateInput(input);
        return maintenanceViewRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        input.maintenanceId(), input.tenantId())
                .orElseThrow(MaintenanceNotFoundException::new);
    }

    private MaintenanceView requireSchedule(MaintenanceEffectScheduleOperationInput input) {
        MaintenanceView view = requireCase(input);
        if (view.getEffectScheduleId() == null || view.getEffectScheduleStatus() == null
                || view.getEffectScheduleTenantZoneId() == null
                || view.getEffectScheduleNextExecutionAt() == null) {
            throw validation("schedule", "案件尚未建立未来生效计划");
        }
        return view;
    }

    private void validateInput(MaintenanceEffectScheduleOperationInput input) {
        if (input == null || blank(input.maintenanceId()) || blank(input.operationId())
                || blank(input.operatorId()) || blank(input.tenantId()) || input.source() == null) {
            throw validation("input", "未来生效计划操作上下文不完整");
        }
    }

    private String requireReason(MaintenanceEffectScheduleOperationInput input) {
        if (blank(input.reason())) {
            throw validation("reason", "计划暂停或恢复必须说明原因");
        }
        return input.reason().trim();
    }

    private MaintenanceEffectScheduleResult result(
            MaintenanceView view,
            MaintenanceEffectScheduleStatus status,
            LocalDateTime nextExecutionAt,
            String errorCode,
            String errorMessage) {
        return new MaintenanceEffectScheduleResult(
                view.getEffectScheduleId(), view.getEffectiveTimeType(), status,
                view.getEffectScheduleTenantZoneId(), nextExecutionAt,
                view.getEffectScheduleAttemptCount(), view.getEffectScheduleLastAttemptId(),
                view.getEffectScheduleLastAttemptAt(), errorCode, errorMessage);
    }

    private String scheduleId(String maintenanceId) {
        return maintenanceId + ":effect";
    }

    private LocalDateTime toUtc(LocalDateTime tenantTime, String zoneId) {
        return tenantTime.atZone(ZoneId.of(zoneId))
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private LocalDateTime fromUtc(LocalDateTime utcTime, String zoneId) {
        return utcTime.atZone(ZoneOffset.UTC)
                .withZoneSameInstant(ZoneId.of(zoneId)).toLocalDateTime();
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private boolean terminal(Throwable cause) {
        return cause instanceof MaintenanceValidationException
                || cause instanceof MaintenanceNotFoundException;
    }

    private String errorCode(Throwable cause) {
        return cause instanceof BusinessException businessException
                ? businessException.getErrorCode() : cause.getClass().getSimpleName();
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable.getClass().getSimpleName();
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private int compare(BigDecimal left, BigDecimal right) {
        return left == null || right == null ? Integer.MIN_VALUE : left.compareTo(right);
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private MaintenanceValidationException validation(String field, String message) {
        return new MaintenanceValidationException("MaintenanceEffectSchedule", field, message);
    }

    private CompletableFuture<Void> send(Object command) {
        return commandGateway.send(command).thenApply(ignored -> null);
    }
}
