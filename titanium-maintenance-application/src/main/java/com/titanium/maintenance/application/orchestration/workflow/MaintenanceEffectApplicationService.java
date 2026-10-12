package com.titanium.maintenance.application.orchestration.workflow;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import com.titanium.maintenance.application.command.effect.MaintenanceEffectApplicationInput;
import com.titanium.maintenance.application.command.field.RefreshMaintenanceFieldConflictsInput;
import com.titanium.maintenance.application.model.effect.LoanDisbursementFact;
import com.titanium.maintenance.application.model.effect.LoanDisbursementOrder;
import com.titanium.maintenance.application.model.effect.LoanRepaymentFact;
import com.titanium.maintenance.application.model.effect.LoanRepaymentOrder;
import com.titanium.maintenance.application.model.effect.MaintenanceEffectApplicationResult;
import com.titanium.maintenance.application.model.effect.MaintenanceInvestmentSwitchInput;
import com.titanium.maintenance.application.model.effect.MaintenanceLoanDisbursementInput;
import com.titanium.maintenance.application.model.effect.MaintenanceLoanRepaymentInput;
import com.titanium.maintenance.application.model.effect.ReducedPaidUpFact;
import com.titanium.maintenance.application.model.effect.ReducedPaidUpOrder;
import com.titanium.maintenance.application.model.field.MaintenanceFieldConflictOperationResult;
import com.titanium.maintenance.command.FailMaintenanceCaseEffectCommand;
import com.titanium.maintenance.command.RecordMaintenanceCaseInvestmentSwitchCommand;
import com.titanium.maintenance.command.RecordMaintenanceCaseLoanDisbursementCommand;
import com.titanium.maintenance.command.RecordMaintenanceCaseLoanRepaymentCommand;
import com.titanium.maintenance.command.RecordMaintenanceCasePolicyApplicationCommand;
import com.titanium.maintenance.command.RecordMaintenanceCasePolicyReissueCommand;
import com.titanium.maintenance.command.RecordMaintenanceEffectCompensationCommand;
import com.titanium.maintenance.command.RequestMaintenanceCaseEffectCommand;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.change.MaintenanceFieldConflictStatus;
import com.titanium.maintenance.common.enums.change.PolicyFieldDataType;
import com.titanium.maintenance.common.enums.config.MaintenanceEffectPayloadKind;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceBillingPostingStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenancePremiumQuoteStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactAnalysisStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactivePeriodRecalculationStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactivePeriodResolutionStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowTaskStatus;
import com.titanium.maintenance.common.exception.MaintenanceNotFoundException;
import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.port.billing.BillingRetroactivePeriodResolutionPort;
import com.titanium.maintenance.port.billing.BillingRetroactivePeriodResolutionPort.ResolutionFact;
import com.titanium.maintenance.port.document.PolicyReissueDocumentPort;
import com.titanium.maintenance.port.investment.InvestmentAccountRedeemPort;
import com.titanium.maintenance.port.investment.InvestmentAccountRedeemPort.InvestmentRedeemFact;
import com.titanium.maintenance.port.investment.InvestmentAccountRedeemPort.InvestmentRedeemRequest;
import com.titanium.maintenance.port.investment.InvestmentAccountSwitchPort;
import com.titanium.maintenance.port.investment.InvestmentAccountSwitchPort.InvestmentSwitchFact;
import com.titanium.maintenance.port.investment.InvestmentAccountSwitchPort.InvestmentSwitchRequest;
import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort;
import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort.PolicyFieldCatalogRequest;
import com.titanium.maintenance.port.policy.PolicyMaintenanceApplicationPort;
import com.titanium.maintenance.port.policy.PolicyMaintenanceApplicationPort.ApplicationFact;
import com.titanium.maintenance.port.policy.PolicyMaintenanceApplicationPort.ApplicationRequest;
import com.titanium.maintenance.port.policy.PolicyMaintenanceApplicationPort.FieldChange;
import com.titanium.maintenance.port.policy.PolicyMaintenanceApplicationPort.RetroactiveEvidence;
import com.titanium.maintenance.query.repository.MaintenanceFieldChangeViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceRetroactivePeriodAdjustmentViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceSnapshotViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceWorkflowTaskViewRepository;
import com.titanium.maintenance.query.view.MaintenanceFieldChangeView;
import com.titanium.maintenance.query.view.MaintenanceRetroactivePeriodAdjustmentView;
import com.titanium.maintenance.query.view.MaintenanceSnapshotView;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.query.view.MaintenanceWorkflowTaskView;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.change.MaintenanceSnapshotReference;
import com.titanium.maintenance.valueobject.effect.MaintenanceAmountEffect;
import com.titanium.maintenance.valueobject.effect.MaintenanceServiceRestrictionEffect;
import com.titanium.maintenance.valueobject.item.MaintenanceItemCode;
import com.titanium.maintenance.valueobject.reissue.PolicyReissueDocument;
import com.titanium.maintenance.valueobject.workflow.MaintenanceAppliedFieldEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceEffectCompensationEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceEffectRequestEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceEffectSchedule;
import com.titanium.maintenance.valueobject.workflow.MaintenanceInvestmentSwitchEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceLoanDisbursementEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceLoanRepaymentEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenancePolicyApplicationEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenancePolicyReissueEvidence;
import com.titanium.metadata.enums.maintenance.MaintenanceType;
import com.titanium.metadata.enums.maintenance.PolicyMaintenanceAction;
import com.titanium.metadata.enums.policy.PolicyEnum.TerminationReason;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 编排“冻结请求事实 → Policy 正式应用 → 权威回执事实”的正式生效用例。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaintenanceEffectApplicationService {

    private final CommandGateway commandGateway;
    private final MaintenanceViewRepository maintenanceViewRepository;
    private final MaintenanceWorkflowTaskViewRepository taskViewRepository;
    private final MaintenanceFieldChangeViewRepository fieldChangeViewRepository;
    private final MaintenanceSnapshotViewRepository snapshotViewRepository;
    private final MaintenanceRetroactivePeriodAdjustmentViewRepository periodAdjustmentViewRepository;
    private final MaintenanceFieldConflictApplicationService fieldConflictApplicationService;
    private final PolicyFieldCatalogPort policyFieldCatalogPort;
    private final PolicyMaintenanceApplicationPort policyApplicationPort;
    private final BillingRetroactivePeriodResolutionPort billingResolutionPort;
    private final InvestmentAccountSwitchPort investmentAccountSwitchPort;
    private final InvestmentAccountRedeemPort investmentAccountRedeemPort;
    private final PolicyReissueDocumentPort policyReissueDocumentPort;
    private final LoanDisbursementOrchestrator loanDisbursementOrchestrator;
    private final LoanRepaymentOrchestrator loanRepaymentOrchestrator;
    private final ReducedPaidUpOrchestrator reducedPaidUpOrchestrator;

    public CompletableFuture<MaintenanceEffectApplicationResult> apply(MaintenanceEffectApplicationInput input) {
        EffectContext context = requireContext(input, false, null);
        MaintenanceEffectApplicationResult recovered = recoverCompletedResult(context.effectTasks());
        if (recovered != null) {
            return CompletableFuture.completedFuture(recovered);
        }
        EffectiveTimeType timeType = context.caseView().getEffectiveTimeType();
        LocalDateTime effectiveAt = timeType == EffectiveTimeType.RETROACTIVE
                ? context.caseView().getSpecificEffectiveDate() : LocalDateTime.now();
        return prepareConflicts(input, context).thenCompose(prepared -> {
            if (isInvestmentSwitchCase(prepared.effectTasks())) {
                return invokeInvestmentSwitch(input, prepared, timeType, effectiveAt);
            }
            if (isLoanCase(prepared.effectTasks())) {
                return invokeLoanDisbursement(input, prepared);
            }
            if (isLoanRepaymentCase(prepared.effectTasks())) {
                return invokeLoanRepayment(input, prepared);
            }
            if (isPolicyReissueCase(prepared.effectTasks())) {
                return invokePolicyReissue(input, prepared);
            }
            if (isReducedPaidUpCase(prepared.effectTasks())) {
                return invokeReducedPaidUp(input, prepared, timeType, effectiveAt);
            }
            ApplicationRequest request = buildRequest(
                    input, prepared, timeType, effectiveAt, prepared.applicationFieldChanges());
            CompletableFuture<Void> requestFrozen = freezeRequestIfRequired(input, prepared, request);
            return requestFrozen.thenCompose(ignored -> invokePolicy(input, prepared, request));
        });
    }

    /** 调度租约持有者专用入口；计划创建阶段不会调用此方法。 */
    public CompletableFuture<MaintenanceEffectApplicationResult> applyScheduled(
            MaintenanceEffectApplicationInput input,
            LocalDateTime scheduledEffectiveAt) {
        EffectContext context = requireContext(input, true, scheduledEffectiveAt);
        MaintenanceEffectApplicationResult recovered = recoverCompletedResult(context.effectTasks());
        if (recovered != null) {
            return CompletableFuture.completedFuture(recovered);
        }
        return prepareConflicts(input, context).thenCompose(prepared -> {
            if (isInvestmentSwitchCase(prepared.effectTasks())) {
                throw validation("investmentSwitch", "账户转换案件不支持计划调度，须由人工立即执行生效");
            }
            if (isLoanCase(prepared.effectTasks())) {
                throw validation("loanDisbursement", "保单贷款案件不支持计划调度，须由人工立即执行生效");
            }
            if (isLoanRepaymentCase(prepared.effectTasks())) {
                throw validation("loanRepayment", "保单贷款还款案件不支持计划调度，须由人工立即执行生效");
            }
            if (isPolicyReissueCase(prepared.effectTasks())) {
                throw validation("policyReissue", "保单补发案件不支持计划调度，须由人工立即执行生效");
            }
            ApplicationRequest request = buildRequest(
                    input, prepared, prepared.caseView().getEffectiveTimeType(), scheduledEffectiveAt,
                    prepared.applicationFieldChanges());
            CompletableFuture<Void> requestFrozen = freezeRequestIfRequired(input, prepared, request);
            return requestFrozen.thenCompose(ignored -> invokePolicy(input, prepared, request));
        });
    }

    private CompletableFuture<EffectContext> prepareConflicts(
            MaintenanceEffectApplicationInput input,
            EffectContext context) {
        if (context.effectTasks().getFirst().getStatus() == MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL) {
            return CompletableFuture.completedFuture(context);
        }
        long expectedVersion = context.expectedPolicyVersion();
        RefreshMaintenanceFieldConflictsInput refreshInput = new RefreshMaintenanceFieldConflictsInput(
                input.maintenanceId(), input.operationId() + ":conflict-refresh",
                input.operatorId(), input.tenantId());
        return fieldConflictApplicationService.refreshIfVersionChanged(refreshInput, expectedVersion)
                .thenApply(result -> {
                    if (result != null && result.conflictCount() > 0) {
                        throw validation("fieldChanges", "Policy 已发生顺序外变更，必须先显式解决字段冲突");
                    }
                    return context.withRefreshedConflicts(result);
                });
    }

    /**
     * 账户转换出口：冻结通用生效请求证据 → 调投资域执行转换 → 记录账户转换权威回执。
     * <p>
     * 🔴 与 Policy 出口的两点结构差异：①请求证据只冻结，不构造 {@code ApplicationRequest}（后者的不变量要求
     * 「字段非空或状态动作改状态」，账户转换两者皆不满足，构造即抛）；②回执落到 {@code RecordMaintenanceCaseInvestmentSwitchCommand}，
     * 产物是账户转换事实而非批单。
     * </p>
     */
    private CompletableFuture<MaintenanceEffectApplicationResult> invokeInvestmentSwitch(
            MaintenanceEffectApplicationInput input,
            EffectContext context,
            EffectiveTimeType effectiveTimeType,
            LocalDateTime requestedEffectiveAt) {
        MaintenanceWorkflowTaskView evidenceTask = context.effectTasks().getFirst();
        boolean reuseFrozenRequest = evidenceTask.getStatus() == MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL;
        InvestmentSwitchRequest switchRequest = investmentSwitchRequest(input, context);
        String requestHash = switchRequest.requestPayloadHash();
        if (reuseFrozenRequest) {
            if (!Objects.equals(evidenceTask.getEffectRequestHash(), requestHash)) {
                throw validation("investmentSwitch", "账户转换重试载荷与已冻结请求不一致");
            }
            return executeInvestmentSwitch(input, context, switchRequest, evidenceTask.getEffectRequestId());
        }
        LocalDateTime effectiveAt = evidenceTask.getEffectRequestedEffectiveAt() != null
                ? evidenceTask.getEffectRequestedEffectiveAt() : requestedEffectiveAt;
        EffectiveTimeType timeType = evidenceTask.getEffectTimeType() != null
                ? evidenceTask.getEffectTimeType() : effectiveTimeType;
        long expectedVersion = evidenceTask.getEffectExpectedPolicyVersion() != null
                ? evidenceTask.getEffectExpectedPolicyVersion() : context.expectedPolicyVersion();
        String requestId = evidenceTask.getEffectRequestId() != null
                ? evidenceTask.getEffectRequestId()
                : PolicyMaintenanceApplicationPort.stableCaseRequestId(input.tenantId(), input.maintenanceId());
        MaintenanceEffectRequestEvidence evidence = new MaintenanceEffectRequestEvidence(
                requestId, requestHash, expectedVersion, timeType, effectiveAt,
                context.proposedSnapshotHash(), LocalDateTime.now());
        String operationId = PolicyMaintenanceApplicationPort.stageOperationId(
                input.operationId(), "request", context.retryCount());
        return send(new RequestMaintenanceCaseEffectCommand(
                MaintenanceId.of(input.maintenanceId()), context.taskIds(), operationId,
                evidence, input.operatorId(), input.tenantId()))
                .thenCompose(ignored -> executeInvestmentSwitch(input, context, switchRequest, requestId));
    }

    private CompletableFuture<MaintenanceEffectApplicationResult> executeInvestmentSwitch(
            MaintenanceEffectApplicationInput input,
            EffectContext context,
            InvestmentSwitchRequest switchRequest,
            String requestId) {
        InvestmentSwitchFact fact;
        try {
            fact = investmentAccountSwitchPort.switchByPolicy(switchRequest);
        } catch (RuntimeException exception) {
            return recordFailure(input, context, "INVESTMENT_SWITCH_FAILED", exception);
        }
        if (fact == null) {
            return recordFailure(input, context, "INVESTMENT_SWITCH_FAILED", new MaintenanceRemoteCallException(
                    "保单名下无可用投资账户，账户转换无处可施, policyId=" + switchRequest.policyId(),
                    MaintenanceErrorCode.MAINTENANCE_INVESTMENT_ACCOUNT_MISSING));
        }
        MaintenanceInvestmentSwitchEvidence evidence = new MaintenanceInvestmentSwitchEvidence(
                requestId, fact.accountId(), switchRequest.switchOutUnits(), switchRequest.targetUnitPrice(),
                switchRequest.targetFund(), fact.unitPrice(), fact.totalUnits(), fact.accountValue(),
                fact.currency(), LocalDateTime.now());
        String operationId = PolicyMaintenanceApplicationPort.stageOperationId(
                input.operationId(), "receipt", context.retryCount());
        return send(new RecordMaintenanceCaseInvestmentSwitchCommand(
                MaintenanceId.of(input.maintenanceId()), context.taskIds(), operationId,
                evidence, input.operatorId(), input.tenantId()))
                .thenApply(ignored -> new MaintenanceEffectApplicationResult(
                        requestId, null, context.expectedPolicyVersion(),
                        evidence.contentHash(), evidence.switchedAt()))
                .exceptionallyCompose(exception -> recordFailure(
                        input, context, "INVESTMENT_SWITCH_FAILED", runtimeCause(rootCause(exception))));
    }

    private InvestmentSwitchRequest investmentSwitchRequest(
            MaintenanceEffectApplicationInput input,
            EffectContext context) {
        MaintenanceInvestmentSwitchInput parameters = input.investmentSwitch();
        if (parameters == null) {
            throw validation("investmentSwitch", "账户转换案件必须携带转换参数(转出单位、目标净值与标的)");
        }
        return new InvestmentSwitchRequest(
                input.tenantId(), context.caseView().getPolicyId(),
                parameters.switchOutUnits(), parameters.targetUnitPrice(),
                parameters.targetCurrency(), parameters.targetFund(), input.operatorId());
    }

    /** 账户转换出口判定：全部生效任务同属账户转换保全项（混合案件由 {@code requireContext} 显式拒绝）。 */
    private boolean isInvestmentSwitchCase(List<MaintenanceWorkflowTaskView> effectTasks) {
        return !effectTasks.isEmpty() && effectTasks.stream().allMatch(this::isInvestmentSwitchTask);
    }

    private boolean isInvestmentSwitchTask(MaintenanceWorkflowTaskView task) {
        return MaintenanceItemCode.of(task.getItemCode()).legacyMaintenanceType() == MaintenanceType.FUND_SWITCH;
    }

    /**
     * 保单贷款出口：冻结通用生效请求证据 → 放款三连写 → 记录贷款放款权威回执。
     * <p>
     * 与账户转换出口同构：请求证据只冻结（不构造 {@code ApplicationRequest}——放款不改保单字段与状态），
     * 回执落到 {@code RecordMaintenanceCaseLoanDisbursementCommand}，产物是贷款放款事实而非批单。
     * 请求载荷摘要只锁申请金额（放款日期是执行时点元数据，幂等重试时以保单域首次入账回显为准）。
     * </p>
     */
    private CompletableFuture<MaintenanceEffectApplicationResult> invokeLoanDisbursement(
            MaintenanceEffectApplicationInput input,
            EffectContext context) {
        MaintenanceWorkflowTaskView evidenceTask = context.effectTasks().getFirst();
        boolean reuseFrozenRequest = evidenceTask.getStatus() == MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL;
        LoanDisbursementOrder order = loanDisbursementOrder(input, context);
        String requestHash = order.requestPayloadHash();
        if (reuseFrozenRequest) {
            if (!Objects.equals(evidenceTask.getEffectRequestHash(), requestHash)) {
                throw validation("loanDisbursement", "贷款重试载荷与已冻结请求不一致");
            }
            return executeLoanDisbursement(input, context, order, evidenceTask.getEffectRequestId());
        }
        String requestId = evidenceTask.getEffectRequestId() != null
                ? evidenceTask.getEffectRequestId()
                : PolicyMaintenanceApplicationPort.stableCaseRequestId(input.tenantId(), input.maintenanceId());
        MaintenanceEffectRequestEvidence evidence = new MaintenanceEffectRequestEvidence(
                requestId, requestHash, context.expectedPolicyVersion(),
                context.caseView().getEffectiveTimeType(),
                evidenceTask.getEffectRequestedEffectiveAt() != null
                        ? evidenceTask.getEffectRequestedEffectiveAt() : LocalDateTime.now(),
                context.proposedSnapshotHash(), LocalDateTime.now());
        String operationId = PolicyMaintenanceApplicationPort.stageOperationId(
                input.operationId(), "request", context.retryCount());
        return send(new RequestMaintenanceCaseEffectCommand(
                MaintenanceId.of(input.maintenanceId()), context.taskIds(), operationId,
                evidence, input.operatorId(), input.tenantId()))
                .thenCompose(ignored -> executeLoanDisbursement(input, context, order, requestId));
    }

    private CompletableFuture<MaintenanceEffectApplicationResult> executeLoanDisbursement(
            MaintenanceEffectApplicationInput input,
            EffectContext context,
            LoanDisbursementOrder order,
            String requestId) {
        LoanDisbursementFact fact;
        try {
            fact = loanDisbursementOrchestrator.disburse(order);
        } catch (RuntimeException exception) {
            return recordFailure(input, context, "LOAN_DISBURSEMENT_FAILED", exception);
        }
        MaintenanceLoanDisbursementEvidence evidence = new MaintenanceLoanDisbursementEvidence(
                requestId, fact.loanId(), fact.policyId(), fact.loanAmount(), fact.interestRate(),
                fact.loanDate(), fact.billingPostingId(), fact.paymentOrderId(), LocalDateTime.now());
        String operationId = PolicyMaintenanceApplicationPort.stageOperationId(
                input.operationId(), "receipt", context.retryCount());
        return send(new RecordMaintenanceCaseLoanDisbursementCommand(
                MaintenanceId.of(input.maintenanceId()), context.taskIds(), operationId,
                evidence, input.operatorId(), input.tenantId()))
                .thenApply(ignored -> new MaintenanceEffectApplicationResult(
                        requestId, null, context.expectedPolicyVersion(),
                        evidence.contentHash(), evidence.disbursedAt()))
                .exceptionallyCompose(exception -> recordFailure(
                        input, context, "LOAN_DISBURSEMENT_FAILED", runtimeCause(rootCause(exception))));
    }

    private LoanDisbursementOrder loanDisbursementOrder(
            MaintenanceEffectApplicationInput input,
            EffectContext context) {
        MaintenanceLoanDisbursementInput parameters = input.loanDisbursement();
        if (parameters == null) {
            throw validation("loanDisbursement", "保单贷款案件必须携带申请金额参数");
        }
        MaintenanceView caseView = context.caseView();
        return new LoanDisbursementOrder(
                input.tenantId(), input.maintenanceId(), caseView.getPolicyId(),
                caseView.getProductId(), caseView.getCustomerId(), input.operatorId(),
                parameters.requestedAmount(), LocalDateTime.now());
    }

    /** 保单贷款出口判定：全部生效任务同属贷款保全项（混合案件由 {@code requireContext} 显式拒绝）。 */
    private boolean isLoanCase(List<MaintenanceWorkflowTaskView> effectTasks) {
        return !effectTasks.isEmpty() && effectTasks.stream().allMatch(this::isLoanTask);
    }

    private boolean isLoanTask(MaintenanceWorkflowTaskView task) {
        return MaintenanceItemCode.of(task.getItemCode()).legacyMaintenanceType() == MaintenanceType.POLICY_LOAN;
    }

    /**
     * 保单贷款还款出口：冻结通用生效请求证据 → 还款三连写 → 记录贷款还款权威回执。
     * <p>
     * 与放款出口同构：请求证据只冻结（不构造 {@code ApplicationRequest}——还款不改保单字段与状态），
     * 回执落到 {@code RecordMaintenanceCaseLoanRepaymentCommand}，产物是贷款还款事实而非批单。
     * 请求载荷摘要只锁「目标明细 + 还款额」（还款日期是执行时点元数据，幂等重试时以保单域首次记账回显为准）。
     * </p>
     */
    private CompletableFuture<MaintenanceEffectApplicationResult> invokeLoanRepayment(
            MaintenanceEffectApplicationInput input,
            EffectContext context) {
        MaintenanceWorkflowTaskView evidenceTask = context.effectTasks().getFirst();
        boolean reuseFrozenRequest = evidenceTask.getStatus() == MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL;
        LoanRepaymentOrder order = loanRepaymentOrder(input, context);
        String requestHash = order.requestPayloadHash();
        if (reuseFrozenRequest) {
            if (!Objects.equals(evidenceTask.getEffectRequestHash(), requestHash)) {
                throw validation("loanRepayment", "贷款还款重试载荷与已冻结请求不一致");
            }
            return executeLoanRepayment(input, context, order, evidenceTask.getEffectRequestId());
        }
        String requestId = evidenceTask.getEffectRequestId() != null
                ? evidenceTask.getEffectRequestId()
                : PolicyMaintenanceApplicationPort.stableCaseRequestId(input.tenantId(), input.maintenanceId());
        MaintenanceEffectRequestEvidence evidence = new MaintenanceEffectRequestEvidence(
                requestId, requestHash, context.expectedPolicyVersion(),
                context.caseView().getEffectiveTimeType(),
                evidenceTask.getEffectRequestedEffectiveAt() != null
                        ? evidenceTask.getEffectRequestedEffectiveAt() : LocalDateTime.now(),
                context.proposedSnapshotHash(), LocalDateTime.now());
        String operationId = PolicyMaintenanceApplicationPort.stageOperationId(
                input.operationId(), "request", context.retryCount());
        return send(new RequestMaintenanceCaseEffectCommand(
                MaintenanceId.of(input.maintenanceId()), context.taskIds(), operationId,
                evidence, input.operatorId(), input.tenantId()))
                .thenCompose(ignored -> executeLoanRepayment(input, context, order, requestId));
    }

    private CompletableFuture<MaintenanceEffectApplicationResult> executeLoanRepayment(
            MaintenanceEffectApplicationInput input,
            EffectContext context,
            LoanRepaymentOrder order,
            String requestId) {
        LoanRepaymentFact fact;
        try {
            fact = loanRepaymentOrchestrator.repay(order);
        } catch (RuntimeException exception) {
            return recordFailure(input, context, "LOAN_REPAYMENT_FAILED", exception);
        }
        MaintenanceLoanRepaymentEvidence evidence = fact.toEvidence(requestId, LocalDateTime.now());
        String operationId = PolicyMaintenanceApplicationPort.stageOperationId(
                input.operationId(), "receipt", context.retryCount());
        return send(new RecordMaintenanceCaseLoanRepaymentCommand(
                MaintenanceId.of(input.maintenanceId()), context.taskIds(), operationId,
                evidence, input.operatorId(), input.tenantId()))
                .thenApply(ignored -> new MaintenanceEffectApplicationResult(
                        requestId, null, context.expectedPolicyVersion(),
                        evidence.contentHash(), evidence.repaidAt()))
                .exceptionallyCompose(exception -> recordFailure(
                        input, context, "LOAN_REPAYMENT_FAILED", runtimeCause(rootCause(exception))));
    }

    private LoanRepaymentOrder loanRepaymentOrder(
            MaintenanceEffectApplicationInput input,
            EffectContext context) {
        MaintenanceLoanRepaymentInput parameters = input.loanRepayment();
        if (parameters == null) {
            throw validation("loanRepayment", "保单贷款还款案件必须携带目标明细与还款额参数");
        }
        MaintenanceView caseView = context.caseView();
        return new LoanRepaymentOrder(
                input.tenantId(), input.maintenanceId(), caseView.getPolicyId(),
                caseView.getCustomerId(), input.operatorId(),
                parameters.loanId(), parameters.repaymentAmount(), LocalDateTime.now());
    }

    /** 保单贷款还款出口判定：全部生效任务同属贷款还款保全项（混合案件由 {@code requireContext} 显式拒绝）。 */
    private boolean isLoanRepaymentCase(List<MaintenanceWorkflowTaskView> effectTasks) {
        return !effectTasks.isEmpty() && effectTasks.stream().allMatch(this::isLoanRepaymentTask);
    }

    private boolean isLoanRepaymentTask(MaintenanceWorkflowTaskView task) {
        return MaintenanceItemCode.of(task.getItemCode()).legacyMaintenanceType() == MaintenanceType.LOAN_REPAYMENT;
    }

    /**
     * 保单补发出口：冻结通用生效请求证据 → 交付补发单证请求事实 → 记录补发请求权威回执（g04-09）。
     * <p>
     * 与贷款/账户转换出口同构：请求证据只冻结（不构造 {@code ApplicationRequest}——补发不改保单字段与状态），
     * 回执落到 {@code RecordMaintenanceCasePolicyReissueCommand}，产物是补发请求事实而非批单。
     * </p>
     * <p>
     * 🔴 <b>出站失败即生效失败（fail-closed），与批单出口的「旁路产物」取向相反</b>：案件的唯一目的就是这份
     * 补发请求，请求未离开本域而案件显示已生效，等于凭空多出一个谁也补不回来的缺口。判据是「该产物是否为
     * 案件生效的实质内容」，不是「是否同为 document 域出站」。
     * </p>
     * <p>
     * 🔴 <b>幂等</b>：补发无调用方自报参数，载荷完全由案件事实决定，故重算摘要恒定；请求号亦为稳定派生
     * （{@link PolicyReissueDocument#deriveReissueRequestId}），重试与重放都收敛到同一请求号，对端据以去重。
     * </p>
     */
    private CompletableFuture<MaintenanceEffectApplicationResult> invokePolicyReissue(
            MaintenanceEffectApplicationInput input,
            EffectContext context) {
        MaintenanceWorkflowTaskView evidenceTask = context.effectTasks().getFirst();
        boolean reuseFrozenRequest = evidenceTask.getStatus() == MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL;
        PolicyReissueDocument document = policyReissueDocument(input, context, evidenceTask);
        String requestHash = document.requestHash();
        if (reuseFrozenRequest) {
            if (!Objects.equals(evidenceTask.getEffectRequestHash(), requestHash)) {
                throw validation("policyReissue", "保单补发重试载荷与已冻结请求不一致");
            }
            return executePolicyReissue(input, context, document, evidenceTask.getEffectRequestId());
        }
        String requestId = evidenceTask.getEffectRequestId() != null
                ? evidenceTask.getEffectRequestId()
                : PolicyMaintenanceApplicationPort.stableCaseRequestId(input.tenantId(), input.maintenanceId());
        MaintenanceEffectRequestEvidence evidence = new MaintenanceEffectRequestEvidence(
                requestId, requestHash, context.expectedPolicyVersion(),
                context.caseView().getEffectiveTimeType(),
                evidenceTask.getEffectRequestedEffectiveAt() != null
                        ? evidenceTask.getEffectRequestedEffectiveAt() : LocalDateTime.now(),
                context.proposedSnapshotHash(), LocalDateTime.now());
        String operationId = PolicyMaintenanceApplicationPort.stageOperationId(
                input.operationId(), "request", context.retryCount());
        return send(new RequestMaintenanceCaseEffectCommand(
                MaintenanceId.of(input.maintenanceId()), context.taskIds(), operationId,
                evidence, input.operatorId(), input.tenantId()))
                .thenCompose(ignored -> executePolicyReissue(input, context, document, requestId));
    }

    private CompletableFuture<MaintenanceEffectApplicationResult> executePolicyReissue(
            MaintenanceEffectApplicationInput input,
            EffectContext context,
            PolicyReissueDocument document,
            String requestId) {
        String deliveredRequestId;
        try {
            deliveredRequestId = policyReissueDocumentPort.requestPolicyReissue(document);
        } catch (RuntimeException exception) {
            return recordFailure(input, context, "POLICY_REISSUE_FAILED", exception);
        }
        MaintenancePolicyReissueEvidence evidence = new MaintenancePolicyReissueEvidence(
                requestId, document.policyId(), deliveredRequestId, LocalDateTime.now());
        String operationId = PolicyMaintenanceApplicationPort.stageOperationId(
                input.operationId(), "receipt", context.retryCount());
        return send(new RecordMaintenanceCasePolicyReissueCommand(
                MaintenanceId.of(input.maintenanceId()), context.taskIds(), operationId,
                evidence, input.operatorId(), input.tenantId()))
                .thenApply(ignored -> new MaintenanceEffectApplicationResult(
                        requestId, null, context.expectedPolicyVersion(),
                        evidence.contentHash(), evidence.requestedAt()))
                .exceptionallyCompose(exception -> recordFailure(
                        input, context, "POLICY_REISSUE_FAILED", runtimeCause(rootCause(exception))));
    }

    /** 组装补发单证请求事实：要素全部取自案件冻结事实，无调用方自报参数。 */
    private PolicyReissueDocument policyReissueDocument(
            MaintenanceEffectApplicationInput input,
            EffectContext context,
            MaintenanceWorkflowTaskView evidenceTask) {
        return new PolicyReissueDocument(
                input.maintenanceId(), context.caseView().getPolicyId(),
                MaintenanceItemCode.of(evidenceTask.getItemCode()).legacyMaintenanceType().getCode(),
                PolicyReissueDocument.deriveReissueRequestId(input.tenantId(), input.maintenanceId()),
                LocalDateTime.now(), input.tenantId());
    }

    /** 保单补发出口判定：全部生效任务同属保单补发保全项（混合案件由 {@code requireContext} 显式拒绝）。 */
    private boolean isPolicyReissueCase(List<MaintenanceWorkflowTaskView> effectTasks) {
        return !effectTasks.isEmpty() && effectTasks.stream().allMatch(this::isPolicyReissueTask);
    }

    private boolean isPolicyReissueTask(MaintenanceWorkflowTaskView task) {
        return MaintenanceItemCode.of(task.getItemCode()).legacyMaintenanceType() == MaintenanceType.POLICY_REISSUE;
    }

    /**
     * 减额缴清出口：核定新保额 → 冻结通用生效请求证据 → 以两条派生字段变更走标准 Policy 生效出口。
     * <p>
     * 与贷款/账户转换出口的形态差异：本出口不改本域之外的领域事实，产物即合同要素变更，故权威回执复用
     * {@code RecordMaintenanceCasePolicyApplicationCommand}，<b>不新增业务凭证、不新增回执证据</b>。
     * </p>
     * <p>
     * 🔴 核定（含「新保额 ≤ 原保额」校验）先于任何写入：拒绝发生在请求冻结之前，案件被记为生效失败，
     * 不会留下半份冻结请求或错误的合同变更。
     * </p>
     * <p>
     * 🔴 幂等重试：核定每次都会重取现金价值/费率（跨域取数无法回到历史时点），漂移由
     * {@code freezeRequestIfRequired} → 载荷摘要比对拒绝，不另设本出口专用的幂等号。
     * </p>
     */
    private CompletableFuture<MaintenanceEffectApplicationResult> invokeReducedPaidUp(
            MaintenanceEffectApplicationInput input,
            EffectContext context,
            EffectiveTimeType effectiveTimeType,
            LocalDateTime requestedEffectiveAt) {
        ReducedPaidUpFact fact;
        try {
            fact = reducedPaidUpOrchestrator.calculate(
                    reducedPaidUpOrder(input, context, requestedEffectiveAt));
        } catch (RuntimeException exception) {
            return recordFailure(input, context, "REDUCED_PAID_UP_FAILED", exception);
        }
        List<FieldChange> changes = fact.toFieldChanges(context.effectTasks().getFirst().getItemCode());
        ApplicationRequest request = buildRequest(
                input, context, effectiveTimeType, requestedEffectiveAt, changes);
        CompletableFuture<Void> requestFrozen = freezeRequestIfRequired(input, context, request);
        return requestFrozen.thenCompose(ignored -> invokePolicy(input, context, request));
    }

    /**
     * 组装减额缴清核定指令：业务时点取本次生效时点（费率版本与合同变更同刻锚定，不取第二次当前时间）。
     */
    private ReducedPaidUpOrder reducedPaidUpOrder(
            MaintenanceEffectApplicationInput input,
            EffectContext context,
            LocalDateTime effectiveAt) {
        MaintenanceView caseView = context.caseView();
        return new ReducedPaidUpOrder(input.tenantId(), input.maintenanceId(),
                caseView.getPolicyId(), caseView.getProductId(), effectiveAt);
    }

    /** 减额缴清出口判定：全部生效任务同属减额缴清保全项（混合案件由 {@code requireContext} 显式拒绝）。 */
    private boolean isReducedPaidUpCase(List<MaintenanceWorkflowTaskView> effectTasks) {
        return !effectTasks.isEmpty() && effectTasks.stream().allMatch(this::isReducedPaidUpTask);
    }

    private boolean isReducedPaidUpTask(MaintenanceWorkflowTaskView task) {
        return MaintenanceItemCode.of(task.getItemCode()).legacyMaintenanceType()
                == MaintenanceType.REDUCED_PAID_UP;
    }

    /**
     * 案件的生效载荷种类：按保全项目录取值，同一案件内必须同种。
     * <p>
     * 🔴 这是空交易闸口的<b>唯一事实来源</b>——原先闸口把「不走字段回写的出口」逐个串成
     * {@code || xxxCase}，每新增一类出口就要在多处同步补串，漏一处即静默拦死整类业务。改为目录取值后，
     * 三处判据（{@code requireContext} 闸口、{@code buildRequest} 闸口、{@code ApplicationRequest}
     * 不变量）共享同一事实，新增一类只需改 {@link MaintenanceItemCode} 的目录。
     * </p>
     * <p>
     * 异种混案在此兜底拒绝：各专用出口虽已有各自的混案检查（文案更具体），但闸口不能依赖「上面那道一定先跑」，
     * 否则一旦某条新增路径绕过前置检查，{@code kinds.getFirst()} 就会按顺序偶然取值而静默放行。
     * </p>
     */
    private MaintenanceEffectPayloadKind payloadKind(List<MaintenanceWorkflowTaskView> effectTasks) {
        Set<MaintenanceEffectPayloadKind> kinds = effectTasks.stream()
                .map(MaintenanceWorkflowTaskView::getItemCode)
                .map(MaintenanceItemCode::of)
                .map(MaintenanceItemCode::effectPayloadKind)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (kinds.size() > 1) {
            throw validation("payloadKind", "同一案件不能混合不同生效载荷种类的保全项");
        }
        return kinds.iterator().next();
    }

    private boolean isAmountTask(MaintenanceWorkflowTaskView task) {
        return MaintenanceItemCode.of(task.getItemCode()).amountEffect();
    }

    private boolean isAmountCase(List<MaintenanceWorkflowTaskView> effectTasks) {
        return !effectTasks.isEmpty() && effectTasks.stream().allMatch(this::isAmountTask);
    }

    /** 服务受限型任务判据：生效载荷是保单上一条受限标记的施加或解除（挂失 / 解除挂失）。 */
    private boolean isServiceRestrictionTask(MaintenanceWorkflowTaskView task) {
        return MaintenanceItemCode.of(task.getItemCode()).serviceRestrictionEffect();
    }

    private boolean isServiceRestrictionCase(List<MaintenanceWorkflowTaskView> effectTasks) {
        return !effectTasks.isEmpty() && effectTasks.stream().allMatch(this::isServiceRestrictionTask);
    }

    /**
     * 解析服务受限型案件的生效载荷——<b>由保全项自身唯一确定，不由调用方自报</b>。
     * <p>
     * 受限的<b>种类</b>与<b>动作</b>（施加 / 解除）都由保全项编码经
     * {@link MaintenanceType#policyServiceRestrictionType()} 与
     * {@link MaintenanceType#policyServiceRestrictionAction()} 推出：挂失恒为「施加 LOSS_REPORT 受限」、
     * 解除挂失恒为「解除 LOSS_REPORT 受限」。调用方无可填的字段，也就不存在「报错种类」这一失效形态。
     * </p>
     * <p>
     * 🔴 <b>施加与解除不得并存于同一案件</b>：两者作用在同一条保单受限标记上，同案提交的净效果取决于
     * 聚合内的处理顺序，事后无从对账；且两者的受理前置条件相反（对未受限者解除、对已受限者重复施加都应被拒），
     * 并存时无论按哪一条校验都会误伤另一条。故与金额型「一批差额一次变更」同规，要求唯一保全项。
     * </p>
     */
    private MaintenanceServiceRestrictionEffect requireServiceRestrictionEffect(
            List<MaintenanceWorkflowTaskView> effectTasks) {
        boolean anyRestriction = effectTasks.stream().anyMatch(this::isServiceRestrictionTask);
        if (!anyRestriction) {
            return null;
        }
        if (effectTasks.size() != 1) {
            throw validation("serviceRestriction",
                    "服务受限型保全项不得与其它生效项混合在同一案件，且施加与解除不得并存");
        }
        MaintenanceType type = MaintenanceItemCode.of(effectTasks.getFirst().getItemCode())
                .legacyMaintenanceType();
        return new MaintenanceServiceRestrictionEffect(
                type.policyServiceRestrictionType(), type.policyServiceRestrictionAction());
    }

    /**
     * 金额型案件的<b>唯一保全项编码</b>——保单域据它推导批改类型，且一批差额只能对应一次合同变更。
     * <p>
     * 🔴 为何要求「唯一」而非取首个：{@link #payloadKind} 只保证案件内载荷<b>同种</b>，
     * 允许多个金额项共存（如同时追加缴费与部分领取）。此时两笔差额各对应一次独立的合同变更，
     * 而下游批单只承载一笔财务事实——按顺序取首个即静默丢弃另一笔，且丢弃哪一笔取决于任务排序，
     * 事后无从对账。故显式拒绝，由调用方拆案。
     * </p>
     */
    private String requireSingleAmountItem(List<MaintenanceWorkflowTaskView> effectTasks) {
        Set<String> items = effectTasks.stream()
                .map(MaintenanceWorkflowTaskView::getItemCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (items.size() != 1) {
            throw validation("amountEffect", "金额型案件必须只包含一个保全项，不能并存多笔收付差额");
        }
        return items.iterator().next();
    }

    /**
     * 解析金额型案件的收付差额事实——<b>不由调用方自报，也不在生效环节重新计价</b>。
     * <p>
     * 权威顺序：① Billing 入账事实（差额已实际登记，形态与「部分领取取原入账事实」同规，取其
     * {@code POSTED} 且方向非空、金额为正者）；② 回落 Product 报价事实（差额已核定但尚未入账）。
     * 二者是同一差额在流程上先后两阶段的登记，后者是前者的结算版本，故①存在时以①为准
     * <b>不构成口径冲突</b>；只有当同一来源出现多笔互不相同的差额时才判为歧义。
     * </p>
     * <p>
     * 🔴 两处兜底都为「拒绝」而非「放行」：金额型案件的变更实质就是这笔差额，取不到差额即空交易，
     * 放行等于写入一条「什么都没发生」的批单。
     * </p>
     */
    private MaintenanceAmountEffect requireAmountEffect(
            List<MaintenanceWorkflowTaskView> caseTasks,
            String itemCode) {
        List<MaintenanceWorkflowTaskView> feeTasks = caseTasks.stream()
                .filter(task -> task.getStepType() == MaintenanceStepType.FEE_SETTLEMENT)
                .toList();
        Set<MaintenanceAmountEffect> postings = feeTasks.stream()
                .filter(this::posted)
                .map(task -> new MaintenanceAmountEffect(itemCode, task.getBillingPostingAmount(),
                        task.getBillingPostingDirection(), task.getBillingPostingCurrency(),
                        task.getBillingPostingId()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (postings.size() > 1) {
            throw validation("amountEffect", "金额型案件存在多笔不一致的 Billing 入账差额，无法确定生效载荷");
        }
        if (postings.size() == 1) {
            return postings.iterator().next();
        }
        Set<MaintenanceAmountEffect> quotes = feeTasks.stream()
                .filter(this::quoted)
                .map(task -> new MaintenanceAmountEffect(itemCode, task.getPremiumQuoteAmount(),
                        task.getPremiumQuoteDirection(), task.getPremiumQuoteCurrency(),
                        task.getPremiumQuoteId()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (quotes.size() > 1) {
            throw validation("amountEffect", "金额型案件存在多笔不一致的 Product 报价差额，无法确定生效载荷");
        }
        if (quotes.isEmpty()) {
            throw validation("amountEffect", "金额型案件缺少已登记的收付差额事实，不能提交空交易");
        }
        return quotes.iterator().next();
    }

    /** Billing 入账事实是否可作为权威差额：须已入账、方向明确、金额为正、凭证齐备。 */
    private boolean posted(MaintenanceWorkflowTaskView task) {
        return task.getBillingPostingStatus() == MaintenanceBillingPostingStatus.POSTED
                && task.getBillingPostingDirection() != null
                && task.getBillingPostingDirection() != MaintenanceBalanceDirection.NONE
                && task.getBillingPostingAmount() != null
                && task.getBillingPostingAmount().signum() > 0
                && task.getBillingPostingCurrency() != null
                && task.getBillingPostingId() != null;
    }

    /** Product 报价事实是否可作为回落差额：须已报价、方向明确、金额为正、凭证齐备。 */
    private boolean quoted(MaintenanceWorkflowTaskView task) {
        return task.getPremiumQuoteStatus() == MaintenancePremiumQuoteStatus.QUOTED
                && task.getPremiumQuoteDirection() != null
                && task.getPremiumQuoteDirection() != MaintenanceBalanceDirection.NONE
                && task.getPremiumQuoteAmount() != null
                && task.getPremiumQuoteAmount().signum() > 0
                && task.getPremiumQuoteCurrency() != null
                && task.getPremiumQuoteId() != null;
    }

    private CompletableFuture<MaintenanceEffectApplicationResult> invokePolicy(
            MaintenanceEffectApplicationInput input,
            EffectContext context,
            ApplicationRequest request) {
        ApplicationFact fact;
        try {
            fact = policyApplicationPort.apply(request);
        } catch (RuntimeException exception) {
            return recordFailure(input, context, "POLICY_APPLICATION_FAILED", exception);
        }
        MaintenancePolicyApplicationEvidence evidence;
        try {
            evidence = toEvidence(request, fact);
        } catch (RuntimeException exception) {
            return recordCompensation(input, context, fact, exception);
        }
        try {
            redeemPartialWithdrawal(input, context);
        } catch (RuntimeException exception) {
            log.error("部分领取的投资侧赎回未完成，转补偿通道: maintenanceId={}, policyId={}, 原因={}",
                    input.maintenanceId(), context.caseView().getPolicyId(), exception.getMessage(), exception);
            return recordCompensation(input, context, fact, exception);
        }
        String operationId = PolicyMaintenanceApplicationPort.stageOperationId(
                input.operationId(), "receipt", context.retryCount());
        return send(new RecordMaintenanceCasePolicyApplicationCommand(
                MaintenanceId.of(input.maintenanceId()), context.taskIds(), operationId,
                evidence, input.operatorId(), input.tenantId()))
                .thenApply(ignored -> new MaintenanceEffectApplicationResult(
                        fact.requestId(), fact.endorsementNo(), fact.actualPolicyVersion(),
                        fact.applicationHash(), fact.appliedAt()))
                .exceptionallyCompose(exception -> recordCompensation(
                        input, context, fact, rootCause(exception)));
    }

    /**
     * 部分领取的<b>投资侧效果</b>：把客户实领金额交投资域账户，由其按当日净值折算为赎回单位数并扣减账户价值
     * （G14/g14-06，「部分领取 → 投资侧赎回单位」；折算精度同 AC-03，入口只在账户侧）。
     * <p>
     * 🔴 <b>必须置于 policy 生效之后、案件回执之前</b>——两个方向的次序都错不得：
     * </p>
     * <ul>
     * <li><b>不得先于 policy 生效</b>：赎回是资金动作，而本域唯一可用的补偿通道 {@link #recordCompensation}
     * 的合法性前提正是「合同变更已生效、客户已可领钱」。若赎回先行而 policy 拒绝，赎回既无处补偿（合同未变），
     * 也无从撤销（投资域无反向命令），只能挂人工；</li>
     * <li><b>不得后于案件回执</b>：回执 {@code RecordMaintenanceCasePolicyApplicationCommand} 会把生效任务直接置为
     * {@code COMPLETED}，而 {@link #recoverCompletedResult} 在全部任务 COMPLETED 时短路返回——赎回若排在回执之后
     * 而未发生，重试将<b>永远不会再走到赎回</b>：客户领了钱、账户价值未扣减，且案件侧无任何失败痕迹
     * （静默少单位）。置于回执之前时，赎回失败即走补偿通道，案件停在补偿态、可人工接续。</li>
     * </ul>
     * <p>
     * ⚠️ <b>已知窗口（如实登记，勿误认为已幂等）</b>：赎回成功而紧接的回执命令失败时，重试会<b>重复赎回</b>
     * （at-least-once）。本出口与账户转换出口（{@code executeInvestmentSwitch}）同形，均未引入幂等键。运行时缓解
     * 有两处：① 赎回金额与 policy 生效请求<b>同源</b>（同取 {@link EffectContext#amountEffect()}），后者已由请求
     * 冻结机制（{@link #validateFrozenRequest}）保证重试金额一致；② 请求携带由案件号派生的稳定
     * {@code reason}，重复赎回可按案在投资域事件流中检出并人工冲正。若后续要做强幂等，正确落点是<b>投资域按稳定
     * 请求号去重</b>（与 policy 侧 {@code stableCaseRequestId} 同法），而非在本域加状态。
     * </p>
     */
    private void redeemPartialWithdrawal(
            MaintenanceEffectApplicationInput input,
            EffectContext context) {
        if (!isPartialWithdrawalCase(context.effectTasks())) {
            return;
        }
        MaintenanceAmountEffect amountEffect = context.amountEffect();
        // 不变量断言（非可达的业务分支）：部分领取属金额型，闸口 requireContext 已解析出差额事实；
        // 若此处取不到，宁可失败可见也不静默跳过赎回——静默少单位正是本任务要修的缺陷形态。
        if (amountEffect == null) {
            throw validation("amountEffect", "部分领取案件缺少已登记的收付差额事实，无法折算赎回单位数");
        }
        InvestmentRedeemFact fact = investmentAccountRedeemPort.redeemByPolicy(new InvestmentRedeemRequest(
                input.tenantId(), context.caseView().getPolicyId(), input.maintenanceId(),
                amountEffect.amount(), amountEffect.currency(), input.operatorId()));
        if (fact == null) {
            throw new MaintenanceRemoteCallException(
                    "保单名下无可用投资账户，部分领取的赎回无处可施, policyId=" + context.caseView().getPolicyId(),
                    MaintenanceErrorCode.MAINTENANCE_INVESTMENT_ACCOUNT_MISSING);
        }
        log.info("[部分领取] 投资侧赎回完成: maintenanceId={}, accountId={}, 实领金额={} {}, 赎回后账户价值={}",
                input.maintenanceId(), fact.accountId(), amountEffect.amount(), amountEffect.currency(),
                fact.accountValue());
    }

    /** 部分领取出口判定：全部生效任务同属部分领取保全项（混案已由 {@code requireContext} 显式拒绝）。 */
    private boolean isPartialWithdrawalCase(List<MaintenanceWorkflowTaskView> effectTasks) {
        return !effectTasks.isEmpty() && effectTasks.stream()
                .allMatch(task -> MaintenanceItemCode.of(task.getItemCode()).legacyMaintenanceType()
                        == MaintenanceType.PARTIAL_WITHDRAWAL);
    }

    private CompletableFuture<MaintenanceEffectApplicationResult> recordCompensation(
            MaintenanceEffectApplicationInput input,
            EffectContext context,
            ApplicationFact fact,
            Throwable exception) {
        RuntimeException cause = runtimeCause(exception);
        String compensationId = PolicyMaintenanceApplicationPort.stageOperationId(
                input.operationId(), "compensation", context.retryCount());
        MaintenanceEffectCompensationEvidence evidence = new MaintenanceEffectCompensationEvidence(
                compensationId, fact.requestId(), fact.endorsementNo(), fact.actualPolicyVersion(),
                fact.applicationHash(), failureReason(cause), LocalDateTime.now(), input.operatorId());
        return send(new RecordMaintenanceEffectCompensationCommand(
                MaintenanceId.of(input.maintenanceId()), context.taskIds().getFirst(), evidence,
                input.operatorId(), input.tenantId()))
                .thenCompose(ignored -> CompletableFuture.failedFuture(cause));
    }

    private CompletableFuture<MaintenanceEffectApplicationResult> recordFailure(
            MaintenanceEffectApplicationInput input,
            EffectContext context,
            String failureCode,
            RuntimeException exception) {
        String operationId = PolicyMaintenanceApplicationPort.stageOperationId(
                input.operationId(), "failure", context.retryCount());
        String reason = failureReason(exception);
        // 🔴 原始失败原因是权威：记录失败事实这一步本身再失败（并发下状态已变等）**不得掩盖它**——
        //    否则调用方读到的是记录通道的错误码，真正的业务拒绝被静默吞掉、接口无从归因
        //    （实测：减额缴清「新保额超原保额」71007012 曾被 71000004 完全取代）。
        return send(new FailMaintenanceCaseEffectCommand(
                MaintenanceId.of(input.maintenanceId()), context.taskIds(), operationId,
                failureCode, reason, input.operatorId(), input.tenantId()))
                .exceptionally(recordFailure -> {
                    log.error("记录生效失败事实未成功，原始失败原因优先透出: maintenanceId={}, failureCode={}",
                            input.maintenanceId(), failureCode, recordFailure);
                    exception.addSuppressed(recordFailure);
                    return null;
                })
                .thenCompose(ignored -> CompletableFuture.failedFuture(exception));
    }

    private CompletableFuture<Void> freezeRequestIfRequired(
            MaintenanceEffectApplicationInput input,
            EffectContext context,
            ApplicationRequest request) {
        if (context.effectTasks().getFirst().getStatus() == MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL) {
            context.effectTasks().forEach(task -> validateFrozenRequest(task, request));
            return CompletableFuture.completedFuture(null);
        }
        LocalDateTime requestedAt = LocalDateTime.now();
        MaintenanceEffectRequestEvidence evidence = new MaintenanceEffectRequestEvidence(
                request.requestId(), request.requestPayloadHash(), request.expectedPolicyVersion(),
                EffectiveTimeType.fromCode(request.effectiveTimeType()), request.effectiveAt(),
                request.proposedSnapshotHash(), requestedAt);
        String operationId = PolicyMaintenanceApplicationPort.stageOperationId(
                input.operationId(), "request", context.retryCount());
        return send(new RequestMaintenanceCaseEffectCommand(
                MaintenanceId.of(input.maintenanceId()), context.taskIds(), operationId,
                evidence, input.operatorId(), input.tenantId()));
    }

    /**
     * 构造提交给 Policy 域的生效请求。
     * <p>
     * 🔴 字段变更由调用方显式传入：普通字段型案件的变更取自 {@code context.applicationFieldChanges()}，
     * 减额缴清则传入由核定事实派生的两条变更。不在此处回落取值——两处各自显式，避免「派生案件静默改走
     * 案件提案字段」这类分流漂移。
     * </p>
     */
    private ApplicationRequest buildRequest(
            MaintenanceEffectApplicationInput input,
            EffectContext context,
            EffectiveTimeType effectiveTimeType,
            LocalDateTime requestedEffectiveAt,
            List<FieldChange> changes) {
        MaintenanceWorkflowTaskView evidenceTask = context.effectTasks().getFirst();
        boolean reuseFrozenRequest = evidenceTask.getStatus() == MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL;
        LocalDateTime effectiveAt = reuseFrozenRequest && evidenceTask.getEffectRequestedEffectiveAt() != null
                ? evidenceTask.getEffectRequestedEffectiveAt() : requestedEffectiveAt;
        EffectiveTimeType requestTimeType = reuseFrozenRequest && evidenceTask.getEffectTimeType() != null
                ? evidenceTask.getEffectTimeType() : effectiveTimeType;
        long expectedVersion = reuseFrozenRequest && evidenceTask.getEffectExpectedPolicyVersion() != null
                ? evidenceTask.getEffectExpectedPolicyVersion()
                : context.expectedPolicyVersion();
        PolicyMaintenanceAction stateAction = stateAction(context.effectTasks());
        // 🔴 与 ApplicationRequest 的空交易判据同构（四种载荷至少其一）：金额型案件的变更实质是
        //    context 已在闸口解析出的差额事实，故此处必须把它计入，否则金额型案件在此二次被误拒；
        //    服务受限型同理——挂失既不写字段也无状态动作，变更的实质就是这条受限标记本身。
        MaintenanceAmountEffect amountEffect = context.amountEffect();
        MaintenanceServiceRestrictionEffect serviceRestrictionEffect =
                requireServiceRestrictionEffect(context.effectTasks());
        if (changes.isEmpty() && !stateAction.changesStatus() && amountEffect == null
                && serviceRestrictionEffect == null) {
            throw validation("fieldChanges", "字段冲突解决后案件已无实际变更，不能提交空 Policy 交易");
        }
        String summary = changeSummary(
                input.maintenanceId(), context.effectTasks(), changes, stateAction, amountEffect,
                serviceRestrictionEffect);
        String requestId = reuseFrozenRequest && evidenceTask.getEffectRequestId() != null
                ? evidenceTask.getEffectRequestId()
                : PolicyMaintenanceApplicationPort.stableCaseRequestId(
                        input.tenantId(), input.maintenanceId());
        return new ApplicationRequest(
                input.tenantId(), context.caseView().getPolicyId(),
                requestId,
                input.maintenanceId(), expectedVersion, null,
                context.proposedSnapshotHash(), requestTimeType.getCode(),
                effectiveAt, summary, changes, amountEffect, serviceRestrictionEffect, stateAction,
                stateReason(input.maintenanceId(), stateAction),
                terminationReason(context.effectTasks(), stateAction), context.retroactiveEvidence(),
                input.operatorId());
    }

    private EffectContext requireContext(
            MaintenanceEffectApplicationInput input,
            boolean scheduled,
            LocalDateTime scheduledEffectiveAt) {
        if (input == null || input.source() == null) {
            throw validation("input", "生效请求上下文不能为空");
        }
        MaintenanceView caseView = maintenanceViewRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        input.maintenanceId(), input.tenantId())
                .orElseThrow(MaintenanceNotFoundException::new);
        MaintenanceWorkflowTaskView task = taskViewRepository
                .findByTenantIdAndMaintenanceIdAndTaskId(
                        input.tenantId(), input.maintenanceId(), input.taskId())
                .orElseThrow(MaintenanceNotFoundException::new);
        if (task.getStepType() != MaintenanceStepType.EFFECT
                || (task.getStatus() != MaintenanceWorkflowTaskStatus.READY
                        && task.getStatus() != MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL
                        && task.getStatus() != MaintenanceWorkflowTaskStatus.COMPLETED)) {
            throw validation("task", "生效任务必须处于 READY、WAITING_EXTERNAL 或 COMPLETED");
        }
        List<MaintenanceWorkflowTaskView> caseTasks = taskViewRepository
                .findByTenantIdAndMaintenanceIdOrderByItemOrderAscSequenceAsc(
                        input.tenantId(), input.maintenanceId());
        List<MaintenanceWorkflowTaskView> effectTasks = caseTasks.stream()
                .filter(candidate -> candidate.getStepType() == MaintenanceStepType.EFFECT)
                .filter(candidate -> candidate.getStatus() != MaintenanceWorkflowTaskStatus.SKIPPED)
                .toList();
        if (effectTasks.isEmpty() || effectTasks.stream().noneMatch(candidate ->
                Objects.equals(candidate.getTaskId(), input.taskId()))) {
            throw validation("effectTasks", "案件不存在请求指定的可执行生效任务");
        }
        boolean allReady = effectTasks.stream().allMatch(candidate ->
                candidate.getStatus() == MaintenanceWorkflowTaskStatus.READY);
        boolean allWaiting = effectTasks.stream().allMatch(candidate ->
                candidate.getStatus() == MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL);
        boolean allCompleted = effectTasks.stream().allMatch(candidate ->
                candidate.getStatus() == MaintenanceWorkflowTaskStatus.COMPLETED);
        if (!allReady && !allWaiting && !allCompleted) {
            throw validation("effectTasks", "案件全部生效任务必须同时处于 READY、WAITING_EXTERNAL 或 COMPLETED");
        }
        if (allCompleted) {
            return new EffectContext(caseView, effectTasks, null, List.of(), null, null, null);
        }
        // 🔴 减额缴清的形态约束前置于生效时点闸口：新保额由「当前现金价值 ÷ 趸缴净费率」核定，
        //    时点后移会拿今日现金价值去支撑彼时的合同变更（静默精算错误），故仅支持立即生效；
        //    且它不与其它保全项混合（案件级原子转换，非字段级批改）。
        //    位置在追溯证据校验之前是有意的——否则回溯案件会先报「追溯影响分析未完成」这一无关错误。
        boolean reducedPaidUpCase = isReducedPaidUpCase(effectTasks);
        if (!reducedPaidUpCase && effectTasks.stream().anyMatch(this::isReducedPaidUpTask)) {
            throw validation("reducedPaidUp", "减额缴清保全项不得与其它生效项混合在同一案件");
        }
        if (reducedPaidUpCase && caseView.getEffectiveTimeType() != EffectiveTimeType.IMMEDIATE) {
            throw validation("effectiveTimeType", "减额缴清案件仅支持立即生效");
        }
        RetroactiveEvidence retroactiveEvidence = null;
        if (!scheduled && caseView.getEffectiveTimeType() == EffectiveTimeType.RETROACTIVE) {
            retroactiveEvidence = requireRetroactiveEvidence(input, caseView);
        } else if (!scheduled && caseView.getEffectiveTimeType() != EffectiveTimeType.IMMEDIATE) {
            throw validation("effectiveTimeType", "未来生效案件必须由计划调度或人工立即执行入口处理");
        }
        if (scheduled && (!MaintenanceEffectSchedule.supportsScheduling(caseView.getEffectiveTimeType())
                || scheduledEffectiveAt == null)) {
            throw validation("effectiveTimeType", "当前案件不是可调度的未来生效案件");
        }
        MaintenanceSnapshotView snapshot = snapshotViewRepository
                .findByMaintenanceIdAndTenantId(input.maintenanceId(), input.tenantId())
                .orElseThrow(MaintenanceNotFoundException::new);
        List<MaintenanceFieldChangeView> fields = fieldChangeViewRepository
                .findByTenantIdAndMaintenanceIdOrderByItemCodeAscFieldCodeAscObjectIdAsc(
                        input.tenantId(), input.maintenanceId());
        if (fields.stream().anyMatch(
                field -> field.getConflictStatus() == MaintenanceFieldConflictStatus.DETECTED)) {
            throw validation("fieldChanges", "生效不能存在未解决字段冲突");
        }
        PolicyMaintenanceAction stateAction = stateAction(effectTasks);
        boolean switchCase = isInvestmentSwitchCase(effectTasks);
        if (!switchCase && effectTasks.stream().anyMatch(this::isInvestmentSwitchTask)) {
            throw validation("investmentSwitch", "账户转换保全项不得与其它生效项混合在同一案件");
        }
        boolean loanCase = isLoanCase(effectTasks);
        if (!loanCase && effectTasks.stream().anyMatch(this::isLoanTask)) {
            throw validation("loanDisbursement", "保单贷款保全项不得与其它生效项混合在同一案件");
        }
        if (loanCase && caseView.getEffectiveTimeType() != EffectiveTimeType.IMMEDIATE) {
            throw validation("effectiveTimeType", "保单贷款案件仅支持立即生效");
        }
        boolean loanRepaymentCase = isLoanRepaymentCase(effectTasks);
        if (!loanRepaymentCase && effectTasks.stream().anyMatch(this::isLoanRepaymentTask)) {
            throw validation("loanRepayment", "保单贷款还款保全项不得与其它生效项混合在同一案件");
        }
        if (loanRepaymentCase && caseView.getEffectiveTimeType() != EffectiveTimeType.IMMEDIATE) {
            throw validation("effectiveTimeType", "保单贷款还款案件仅支持立即生效");
        }
        // 🔴 保单补发同属案件级原子动作（一案对应一份补发请求），不得与其它生效项混合：混案后「这份补发请求
        //    对应哪次合同变更」无从确定，且补发请求事实是一份自足载荷，承载不了其它保全项的语义。
        //    与贷款出口同取「仅支持立即生效」——补发请求不锚定合同变更时点，允许未来生效只会让「何时寄送」
        //    与「何时生效」两个时点互相矛盾。
        boolean policyReissueCase = isPolicyReissueCase(effectTasks);
        if (!policyReissueCase && effectTasks.stream().anyMatch(this::isPolicyReissueTask)) {
            throw validation("policyReissue", "保单补发保全项不得与其它生效项混合在同一案件");
        }
        if (policyReissueCase && caseView.getEffectiveTimeType() != EffectiveTimeType.IMMEDIATE) {
            throw validation("effectiveTimeType", "保单补发案件仅支持立即生效");
        }
        // 🔴 金额型保全项与专用出口同为案件级原子动作（一笔差额对应一次合同变更），不得与其它生效项混合：
        //    混案后「这笔钱对应哪次变更」无从确定，且下游批单只能承载一笔财务事实。
        //    判据用 allMatch/anyMatch 自算，**不**借道 payloadKind()——后者在混种案件上会先抛「载荷种类不混合」
        //    的通用文案，把本条更具体的归因挡成不可达代码（守卫为真却守在没人能到达的路径上）。
        boolean amountCase = isAmountCase(effectTasks);
        if (!amountCase && effectTasks.stream().anyMatch(this::isAmountTask)) {
            throw validation("amountEffect", "金额型保全项不得与其它生效项混合在同一案件");
        }
        // 🔴 服务受限型（挂失 / 解除挂失）同属案件级原子动作，且施加与解除作用在同一条受限标记上：
        //    混案或同案并存会让净效果取决于聚合内的处理顺序，事后无从对账，故一律拒绝。判据同样自算
        //    allMatch/anyMatch，不借道 payloadKind()（理由同上一段）。
        boolean serviceRestrictionCase = isServiceRestrictionCase(effectTasks);
        if (!serviceRestrictionCase && effectTasks.stream().anyMatch(this::isServiceRestrictionTask)) {
            throw validation("serviceRestriction", "服务受限型保全项不得与其它生效项混合在同一案件");
        }
        if (serviceRestrictionCase && effectTasks.size() != 1) {
            throw validation("serviceRestriction", "服务受限型案件必须只包含一个保全项，施加与解除不得并存");
        }
        // 🔴 空交易闸口按「生效载荷种类」判定，不按保全项逐个串 `||`：
        //    字段型要求有结构化字段变更或合同状态动作；金额型要求已登记的收付差额事实；
        //    专用出口的载荷另有出处（其前置条件已在上方各自独立成段），此处只承认、不重复校验。
        if (payloadKind(effectTasks) == MaintenanceEffectPayloadKind.FIELD
                && fields.isEmpty() && !stateAction.changesStatus()) {
            throw validation("fieldChanges", "字段型案件必须包含结构化字段变更");
        }
        if (!fields.isEmpty()) {
            LocalDateTime capabilityEffectiveAt = scheduled
                    ? scheduledEffectiveAt
                    : caseView.getEffectiveTimeType() == EffectiveTimeType.RETROACTIVE
                            ? caseView.getSpecificEffectiveDate()
                            : effectiveAt(effectTasks.getFirst());
            validateExecutionCapabilities(input, fields, effectiveDate(capabilityEffectiveAt));
        }
        MaintenanceAmountEffect amountEffect = amountCase
                ? requireAmountEffect(caseTasks, requireSingleAmountItem(effectTasks))
                : null;
        return new EffectContext(
                caseView, effectTasks, snapshot, fields, retroactiveEvidence, amountEffect, null);
    }

    private MaintenanceEffectApplicationResult recoverCompletedResult(
            List<MaintenanceWorkflowTaskView> effectTasks) {
        if (effectTasks.stream().anyMatch(task -> task.getStatus() != MaintenanceWorkflowTaskStatus.COMPLETED)) {
            return null;
        }
        MaintenanceWorkflowTaskView authoritative = effectTasks.getFirst();
        validateCompletedEvidence(authoritative);
        effectTasks.stream().skip(1).forEach(task -> {
            validateCompletedEvidence(task);
            if (!sameCompletedEvidence(authoritative, task)) {
                throw validation("policyApplication", "已完成生效任务未共享同一 Policy 请求和权威回执");
            }
        });
        if (hasText(authoritative.getSwitchAccountId())) {
            return new MaintenanceEffectApplicationResult(
                    authoritative.getEffectRequestId(), null,
                    authoritative.getEffectExpectedPolicyVersion() == null
                            ? 0L : authoritative.getEffectExpectedPolicyVersion(),
                    authoritative.getSwitchEvidenceHash(), authoritative.getSwitchSwitchedAt());
        }
        if (hasText(authoritative.getLoanId())) {
            return new MaintenanceEffectApplicationResult(
                    authoritative.getEffectRequestId(), null,
                    authoritative.getEffectExpectedPolicyVersion() == null
                            ? 0L : authoritative.getEffectExpectedPolicyVersion(),
                    authoritative.getLastEvidenceHash(), authoritative.getLoanDisbursedAt());
        }
        if (hasText(authoritative.getLoanRepaymentId())) {
            return new MaintenanceEffectApplicationResult(
                    authoritative.getEffectRequestId(), null,
                    authoritative.getEffectExpectedPolicyVersion() == null
                            ? 0L : authoritative.getEffectExpectedPolicyVersion(),
                    authoritative.getLastEvidenceHash(), authoritative.getLoanRepaidAt());
        }
        if (hasText(authoritative.getReissueRequestId())) {
            return new MaintenanceEffectApplicationResult(
                    authoritative.getEffectRequestId(), null,
                    authoritative.getEffectExpectedPolicyVersion() == null
                            ? 0L : authoritative.getEffectExpectedPolicyVersion(),
                    authoritative.getLastEvidenceHash(), authoritative.getReissueRequestedAt());
        }
        return new MaintenanceEffectApplicationResult(
                authoritative.getEffectRequestId(), authoritative.getPolicyEndorsementNo(),
                authoritative.getPolicyActualVersion(), authoritative.getPolicyApplicationHash(),
                authoritative.getPolicyAppliedAt());
    }

    private void validateCompletedEvidence(MaintenanceWorkflowTaskView task) {
        boolean invalidRequest = !hasText(task.getEffectRequestId())
                || !isHash(task.getEffectRequestHash())
                || task.getEffectExpectedPolicyVersion() == null
                || task.getEffectTimeType() == null
                || task.getEffectRequestedEffectiveAt() == null
                || !isHash(task.getEffectProposedSnapshotHash());
        if (invalidRequest) {
            throw validation("policyApplication", "已完成生效任务缺少完整生效请求投影");
        }
        // 账户转换出口：无批单号、不推进保单版本、无应用快照，故不适用保单回执不变量，以账户回执列齐备为准
        if (hasText(task.getSwitchAccountId())) {
            return;
        }
        // 保单贷款出口：同账户转换，无批单号/无应用快照，以贷款回执列齐备为准（与 switchAccountId 互斥）
        if (hasText(task.getLoanId())) {
            return;
        }
        // 保单贷款还款出口：同放款，无批单号/无应用快照（与 switchAccountId、loanId 互斥）
        if (hasText(task.getLoanRepaymentId())) {
            return;
        }
        // 保单补发出口：同放款，无批单号/无应用快照（与上述三个出口列互斥）
        if (hasText(task.getReissueRequestId())) {
            return;
        }
        boolean invalidApplication = !hasText(task.getPolicyEndorsementNo())
                || task.getPolicyActualVersion() == null
                || !isHash(task.getPolicyApplicationHash())
                || task.getPolicyStateAction() == null
                || task.getPolicyAppliedAt() == null;
        boolean invalidSnapshot = !hasText(task.getAppliedSnapshotStorageKey())
                || !isHash(task.getAppliedSnapshotHash())
                || !Objects.equals(task.getPolicyActualVersion(), task.getAppliedSnapshotPolicyVersion())
                || !hasText(task.getAppliedSnapshotCapturedAt())
                || !hasText(task.getAppliedFieldsJson());
        boolean invalidStatusChange = task.getPolicyStateAction() != null
                && task.getPolicyStateAction().changesStatus()
                && (!hasText(task.getPolicyStatusBefore()) || !hasText(task.getPolicyStatusAfter()));
        if (invalidApplication || invalidSnapshot || invalidStatusChange) {
            throw validation("policyApplication", "已完成生效任务缺少完整 Policy 回执或应用快照投影");
        }
    }

    private boolean sameCompletedEvidence(
            MaintenanceWorkflowTaskView expected,
            MaintenanceWorkflowTaskView actual) {
        return Objects.equals(expected.getEffectRequestId(), actual.getEffectRequestId())
                && Objects.equals(expected.getEffectRequestHash(), actual.getEffectRequestHash())
                && Objects.equals(expected.getEffectExpectedPolicyVersion(), actual.getEffectExpectedPolicyVersion())
                && Objects.equals(expected.getEffectTimeType(), actual.getEffectTimeType())
                && Objects.equals(expected.getEffectRequestedEffectiveAt(), actual.getEffectRequestedEffectiveAt())
                && Objects.equals(expected.getEffectProposedSnapshotHash(), actual.getEffectProposedSnapshotHash())
                && Objects.equals(expected.getPolicyEndorsementNo(), actual.getPolicyEndorsementNo())
                && Objects.equals(expected.getPolicyActualVersion(), actual.getPolicyActualVersion())
                && Objects.equals(expected.getPolicyApplicationHash(), actual.getPolicyApplicationHash())
                && Objects.equals(expected.getPolicyStateAction(), actual.getPolicyStateAction())
                && Objects.equals(expected.getPolicyStatusBefore(), actual.getPolicyStatusBefore())
                && Objects.equals(expected.getPolicyStatusAfter(), actual.getPolicyStatusAfter())
                && Objects.equals(expected.getAppliedSnapshotStorageKey(), actual.getAppliedSnapshotStorageKey())
                && Objects.equals(expected.getAppliedSnapshotHash(), actual.getAppliedSnapshotHash())
                && Objects.equals(expected.getAppliedSnapshotPolicyVersion(), actual.getAppliedSnapshotPolicyVersion())
                && Objects.equals(expected.getAppliedSnapshotCapturedAt(), actual.getAppliedSnapshotCapturedAt())
                && Objects.equals(expected.getAppliedFieldsJson(), actual.getAppliedFieldsJson())
                && Objects.equals(expected.getPolicyAppliedAt(), actual.getPolicyAppliedAt())
                && Objects.equals(expected.getSwitchAccountId(), actual.getSwitchAccountId())
                && Objects.equals(expected.getSwitchEvidenceHash(), actual.getSwitchEvidenceHash())
                && Objects.equals(expected.getSwitchUnitPrice(), actual.getSwitchUnitPrice())
                && Objects.equals(expected.getSwitchTotalUnits(), actual.getSwitchTotalUnits())
                && Objects.equals(expected.getSwitchAccountValue(), actual.getSwitchAccountValue())
                && Objects.equals(expected.getSwitchCurrency(), actual.getSwitchCurrency())
                && Objects.equals(expected.getSwitchSwitchedAt(), actual.getSwitchSwitchedAt())
                && Objects.equals(expected.getLoanId(), actual.getLoanId())
                && Objects.equals(expected.getLoanAmount(), actual.getLoanAmount())
                && Objects.equals(expected.getLoanInterestRate(), actual.getLoanInterestRate())
                && Objects.equals(expected.getLoanDate(), actual.getLoanDate())
                && Objects.equals(expected.getLoanBillingPostingId(), actual.getLoanBillingPostingId())
                && Objects.equals(expected.getLoanPaymentOrderId(), actual.getLoanPaymentOrderId())
                && Objects.equals(expected.getLoanDisbursedAt(), actual.getLoanDisbursedAt())
                && Objects.equals(expected.getLoanRepaymentId(), actual.getLoanRepaymentId())
                && Objects.equals(expected.getLoanRepaymentAmount(), actual.getLoanRepaymentAmount())
                && Objects.equals(expected.getLoanRepaymentInterestPortion(),
                        actual.getLoanRepaymentInterestPortion())
                && Objects.equals(expected.getLoanRepaymentPrincipalPortion(),
                        actual.getLoanRepaymentPrincipalPortion())
                && Objects.equals(expected.getLoanRepaymentAccruedInterest(),
                        actual.getLoanRepaymentAccruedInterest())
                && Objects.equals(expected.getLoanOutstandingPrincipalAfter(),
                        actual.getLoanOutstandingPrincipalAfter())
                && Objects.equals(expected.getLoanOutstandingInterestAfter(),
                        actual.getLoanOutstandingInterestAfter())
                && Objects.equals(expected.getLoanRepaymentSettled(), actual.getLoanRepaymentSettled())
                && Objects.equals(expected.getLoanRepaymentDate(), actual.getLoanRepaymentDate())
                && Objects.equals(expected.getLoanRepaymentBillingPostingId(),
                        actual.getLoanRepaymentBillingPostingId())
                && Objects.equals(expected.getLoanRepaymentPaymentOrderId(),
                        actual.getLoanRepaymentPaymentOrderId())
                && Objects.equals(expected.getLoanRepaidAt(), actual.getLoanRepaidAt())
                && Objects.equals(expected.getReissueRequestId(), actual.getReissueRequestId())
                && Objects.equals(expected.getReissueRequestedAt(), actual.getReissueRequestedAt());
    }

    private RetroactiveEvidence requireRetroactiveEvidence(
            MaintenanceEffectApplicationInput input,
            MaintenanceView view) {
        if (view.getSpecificEffectiveDate() == null
                || view.getRetroactiveImpactStatus() != MaintenanceRetroactiveImpactAnalysisStatus.COMPLETED
                || view.getRetroactiveImpactBlockingCount() != 0
                || view.getRetroactiveImpactPendingCount() != 0) {
            throw validation("retroactiveImpact", "追溯影响分析未完成或仍存在阻断、待处理影响项");
        }
        if (view.getRetroactivePeriodRecalculationStatus()
                        != MaintenanceRetroactivePeriodRecalculationStatus.COMPLETED
                && view.getRetroactivePeriodRecalculationStatus()
                        != MaintenanceRetroactivePeriodRecalculationStatus.REVIEW_REQUIRED) {
            throw validation("retroactivePeriodRecalculation", "追溯期间重算尚未完成");
        }
        if (!hasText(view.getRetroactiveImpactAnalysisId())
                || value(view.getRetroactiveImpactAnalysisVersion()) < 1
                || !isHash(view.getRetroactiveImpactResultHash())
                || !hasText(view.getRetroactivePeriodRecalculationId())
                || value(view.getRetroactivePeriodRecalculationVersion()) < 1
                || !Objects.equals(view.getRetroactiveImpactAnalysisId(), view.getRetroactivePeriodAnalysisId())
                || !Objects.equals(view.getRetroactiveImpactAnalysisVersion(),
                        view.getRetroactivePeriodAnalysisVersion())
                || !Objects.equals(view.getRetroactiveImpactResultHash(),
                        view.getRetroactivePeriodAnalysisResultHash())
                || !hasText(view.getRetroactiveProductRecalculationId())
                || !hasText(view.getRetroactiveProductRecalculationVersion())
                || !isHash(view.getRetroactiveProductInputHash())
                || !isHash(view.getRetroactiveProductResultHash())
                || !hasText(view.getRetroactiveBillingBatchId())
                || !isHash(view.getRetroactiveBillingResultHash())) {
            throw validation("retroactiveEvidence", "追溯分析、Product或Billing检查点不完整或已经漂移");
        }
        validateRecalculationPeriods(input, view);

        ResolutionFact resolution = null;
        if (view.getRetroactivePeriodRecalculationStatus()
                == MaintenanceRetroactivePeriodRecalculationStatus.REVIEW_REQUIRED) {
            resolution = requireBillingResolution(input, view);
        } else if (view.getRetroactiveBillingReviewCount() != 0
                || view.getRetroactivePeriodResolutionStatus() != null) {
            throw validation("retroactiveResolution", "无关闭期间的重算结果携带了非法处理结论");
        }
        return new RetroactiveEvidence(
                view.getRetroactiveImpactAnalysisId(), value(view.getRetroactiveImpactAnalysisVersion()),
                view.getRetroactiveImpactResultHash(), view.getRetroactivePeriodRecalculationId(),
                value(view.getRetroactivePeriodRecalculationVersion()),
                view.getRetroactiveProductRecalculationId(), view.getRetroactiveProductRecalculationVersion(),
                view.getRetroactiveProductInputHash(), view.getRetroactiveProductResultHash(),
                view.getRetroactiveBillingBatchId(), view.getRetroactiveBillingResultHash(),
                view.getRetroactiveBillingStatus(),
                resolution == null ? null : resolution.billingResolutionId(),
                resolution == null ? null : resolution.resultHash(),
                resolution == null ? null : resolution.targetAccountingPeriod().toString(),
                resolution == null ? 0 : resolution.resolvedLineCount());
    }

    private void validateRecalculationPeriods(
            MaintenanceEffectApplicationInput input,
            MaintenanceView view) {
        List<MaintenanceRetroactivePeriodAdjustmentView> periods = periodAdjustmentViewRepository
                .findByTenantIdAndMaintenanceIdAndPeriodRecalculationIdOrderByPeriodStartAscPeriodIdAsc(
                        input.tenantId(), input.maintenanceId(), view.getRetroactivePeriodRecalculationId());
        if (periods.size() != view.getRetroactivePeriodCount()
                || periods.stream().anyMatch(period -> !isHash(period.getProductResultHash())
                        || !isHash(period.getBillingResultHash())
                        || !hasText(period.getBillingStatus()))) {
            throw validation("retroactiveEvidence", "追溯逐期间检查点数量或摘要不完整");
        }
    }

    private ResolutionFact requireBillingResolution(
            MaintenanceEffectApplicationInput input,
            MaintenanceView view) {
        if (view.getRetroactivePeriodResolutionStatus()
                        != MaintenanceRetroactivePeriodResolutionStatus.COMPLETED
                || !hasText(view.getRetroactiveBillingResolutionId())
                || !Objects.equals(view.getRetroactiveBillingResultHash(),
                        view.getRetroactivePeriodResolutionSourceBatchHash())
                || !isHash(view.getRetroactivePeriodResolutionResultHash())
                || view.getRetroactivePeriodResolutionResolvedLineCount()
                        != view.getRetroactiveBillingReviewCount()) {
            throw validation("retroactiveResolution", "关闭会计期间处理结论尚未完成或与当前批次不一致");
        }
        ResolutionFact fact = billingResolutionPort.get( view.getRetroactiveBillingBatchId());
        if (fact == null || !Objects.equals("COMPLETED", fact.status())
                || !Objects.equals(input.tenantId(), fact.tenantId())
                || !Objects.equals(input.maintenanceId(), fact.maintenanceId())
                || !Objects.equals(view.getPolicyId(), fact.policyId())
                || !Objects.equals(view.getRetroactiveBillingBatchId(), fact.billingBatchId())
                || !Objects.equals(view.getRetroactiveBillingResultHash(), fact.sourceBatchResultHash())
                || !Objects.equals(view.getRetroactiveBillingResolutionId(), fact.billingResolutionId())
                || !Objects.equals(view.getRetroactivePeriodResolutionResultHash(), fact.resultHash())
                || fact.resolvedLineCount() != view.getRetroactiveBillingReviewCount()) {
            throw validation("retroactiveResolution", "Billing关闭期间处理权威结论与案件投影不一致");
        }
        validateResolutionLines(input, view, fact);
        return fact;
    }

    private void validateResolutionLines(
            MaintenanceEffectApplicationInput input,
            MaintenanceView view,
            ResolutionFact fact) {
        List<MaintenanceRetroactivePeriodAdjustmentView> reviewPeriods = periodAdjustmentViewRepository
                .findByTenantIdAndMaintenanceIdAndPeriodRecalculationIdOrderByPeriodStartAscPeriodIdAsc(
                        input.tenantId(), input.maintenanceId(), view.getRetroactivePeriodRecalculationId()).stream()
                .filter(period -> "CLOSED_PERIOD_REVIEW".equals(period.getBillingStatus()))
                .toList();
        if (reviewPeriods.size() != fact.resolvedLineCount()) {
            throw validation("retroactiveResolution", "关闭期间处理行数与当前期间投影不一致");
        }
        reviewPeriods.forEach(period -> fact.lines().stream()
                .filter(line -> Objects.equals(period.getPeriodId(), line.periodId()))
                .filter(line -> Objects.equals(period.getBillingResultHash(), line.sourceLineResultHash()))
                .findFirst()
                .orElseThrow(() -> validation(
                        "retroactiveResolution", "关闭期间处理明细摘要与当前期间投影不一致")));
    }

    private void validateExecutionCapabilities(
            MaintenanceEffectApplicationInput input,
            List<MaintenanceFieldChangeView> fields,
            java.time.LocalDate businessDate) {
        var catalog = policyFieldCatalogPort.getCatalog(
                new PolicyFieldCatalogRequest(input.tenantId(), null, null, businessDate));
        fields.forEach(field -> {
            var descriptor = catalog.requireField(field.getFieldCode());
            if (!descriptor.capability().executionSupported()) {
                throw validation("fieldCode", "字段尚未开放真实执行: " + field.getFieldCode());
            }
        });
    }

    private MaintenancePolicyApplicationEvidence toEvidence(
            ApplicationRequest request,
            ApplicationFact fact) {
        if (fact == null || !Objects.equals(request.requestId(), fact.requestId())
                || request.expectedPolicyVersion() != fact.expectedPolicyVersion()
                || fact.appliedSnapshot() == null || request.stateAction() != fact.stateAction()
                || !Objects.equals(request.retroactiveEvidence(), fact.retroactiveEvidence())) {
            throw validation("policyReceipt", "Policy 回执与请求事实不一致");
        }
        if (fact.stateAction().changesStatus()
                && (fact.statusBefore() == null || fact.statusBefore().isBlank()
                        || fact.statusAfter() == null || fact.statusAfter().isBlank())) {
            throw validation("policyReceipt", "状态类 Policy 回执缺少变更前后状态");
        }
        MaintenanceSnapshotReference snapshot = new MaintenanceSnapshotReference(
                fact.appliedSnapshot().storageKey(), fact.appliedSnapshot().contentHash(),
                fact.appliedSnapshot().policyVersion(), fact.appliedSnapshot().capturedAt());
        List<MaintenanceAppliedFieldEvidence> appliedFields = fact.appliedFields().stream()
                .map(field -> new MaintenanceAppliedFieldEvidence(
                        field.itemCode(), field.objectId(), field.fieldCode(),
                        requireDataType(field.dataType()), field.canonicalValue()))
                .toList();
        return new MaintenancePolicyApplicationEvidence(
                fact.requestId(), fact.endorsementNo(), fact.expectedPolicyVersion(),
                fact.actualPolicyVersion(), fact.applicationHash(), snapshot, appliedFields, fact.appliedAt(),
                fact.stateAction(), fact.statusBefore(), fact.statusAfter());
    }

    private void validateFrozenRequest(MaintenanceWorkflowTaskView task, ApplicationRequest request) {
        if (!Objects.equals(task.getEffectRequestId(), request.requestId())
                || !Objects.equals(task.getEffectRequestHash(), request.requestPayloadHash())
                || !Objects.equals(task.getEffectExpectedPolicyVersion(), request.expectedPolicyVersion())
                || !Objects.equals(task.getEffectTimeType(), EffectiveTimeType.fromCode(request.effectiveTimeType()))
                || !Objects.equals(task.getEffectRequestedEffectiveAt(), request.effectiveAt())
                || !Objects.equals(task.getEffectProposedSnapshotHash(), request.proposedSnapshotHash())) {
            throw validation("effectEvidence", "既有生效请求事实与重试载荷不一致");
        }
    }

    private LocalDateTime effectiveAt(MaintenanceWorkflowTaskView task) {
        return task.getEffectRequestedEffectiveAt() != null
                ? task.getEffectRequestedEffectiveAt() : LocalDateTime.now();
    }

    private java.time.LocalDate effectiveDate(LocalDateTime effectiveAt) {
        return effectiveAt.toLocalDate();
    }

    private long requireVersion(Long version) {
        if (version == null || version < 0) {
            throw validation("proposedPolicyVersion", "拟变更快照缺少有效 Policy 版本");
        }
        return version;
    }

    private int value(Integer number) {
        return number == null ? 0 : number;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isHash(String value) {
        return value != null && value.matches("[a-fA-F0-9]{64}");
    }

    private String requireHash(String value) {
        if (value == null || !value.matches("[a-fA-F0-9]{64}")) {
            throw validation("proposedSnapshotHash", "拟变更快照缺少合法 SHA-256 摘要");
        }
        return value.toLowerCase();
    }

    private PolicyFieldDataType requireDataType(String code) {
        for (PolicyFieldDataType dataType : PolicyFieldDataType.values()) {
            if (dataType.getCode().equals(code)) {
                return dataType;
            }
        }
        throw validation("dataType", "Policy 回执包含未知字段类型: " + code);
    }

    private String changeSummary(
            String maintenanceId,
            List<MaintenanceWorkflowTaskView> effectTasks,
            List<FieldChange> changes,
            PolicyMaintenanceAction stateAction,
            MaintenanceAmountEffect amountEffect,
            MaintenanceServiceRestrictionEffect serviceRestrictionEffect) {
        String items = effectTasks.stream().map(MaintenanceWorkflowTaskView::getItemCode)
                .distinct().sorted().collect(Collectors.joining(","));
        String fields = changes.stream().map(FieldChange::fieldCode).distinct().sorted()
                .collect(Collectors.joining(","));
        // 金额型案件无字段、无状态动作：摘要若不含差额，批单摘要会退化成只剩案件号，事后无从对账。
        String amount = amountEffect == null ? "" : ";amount=" + amountEffect.direction().getCode()
                + ":" + amountEffect.amount().toPlainString() + ":" + amountEffect.currency();
        // 服务受限型案件同样无字段、无状态动作：摘要必须带上「施加还是解除 + 哪一类受限」，
        // 否则同一保单的挂失与解除挂失在批单摘要里逐字相同，事后无法分辨先后。
        String restriction = serviceRestrictionEffect == null ? "" : ";restriction="
                + serviceRestrictionEffect.type().getCode() + ":" + serviceRestrictionEffect.action().getCode();
        String summary = "maintenance=" + maintenanceId + ";items=" + items
                + ";fields=" + fields + ";action=" + stateAction.name() + amount + restriction;
        if (summary.length() > 512) {
            throw validation("changeSummary", "案件字段过多，无法形成稳定批单摘要");
        }
        return summary;
    }

    private PolicyMaintenanceAction stateAction(List<MaintenanceWorkflowTaskView> effectTasks) {
        Set<PolicyMaintenanceAction> actions = effectTasks.stream()
                .map(MaintenanceWorkflowTaskView::getItemCode)
                .map(this::stateAction)
                .filter(PolicyMaintenanceAction::changesStatus)
                .collect(java.util.stream.Collectors.toSet());
        if (actions.size() > 1) {
            throw validation("stateAction", "同一案件不能包含多个合同状态动作");
        }
        return actions.isEmpty() ? PolicyMaintenanceAction.NONE : actions.iterator().next();
    }

    private PolicyMaintenanceAction stateAction(String itemCode) {
        return MaintenanceItemCode.of(itemCode).legacyMaintenanceType().policyMaintenanceAction();
    }

    private TerminationReason terminationReason(
            List<MaintenanceWorkflowTaskView> effectTasks,
            PolicyMaintenanceAction action) {
        if (action != PolicyMaintenanceAction.TERMINATE) {
            return null;
        }
        boolean surrender = effectTasks.stream().map(MaintenanceWorkflowTaskView::getItemCode)
                .anyMatch(itemCode -> "SURRENDER".equals(itemCode) || "POLICY_SURRENDER".equals(itemCode));
        return surrender ? TerminationReason.WITHDRAWAL : TerminationReason.CONTRACT_TERMINATION;
    }

    private String stateReason(String maintenanceId, PolicyMaintenanceAction action) {
        return action.changesStatus() ? "保全案件 " + maintenanceId + " 执行 " + action.name() : null;
    }

    private String failureReason(RuntimeException exception) {
        String message = exception.getMessage();
        String reason = message == null || message.isBlank()
                ? exception.getClass().getSimpleName() : message;
        return reason.length() <= 500 ? reason : reason.substring(0, 500);
    }

    private Throwable rootCause(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /** 异常统一收敛为运行时异常，供失败/补偿命令承载原因。 */
    private RuntimeException runtimeCause(Throwable exception) {
        return exception instanceof RuntimeException runtimeException
                ? runtimeException : new IllegalStateException(exception);
    }

    private CompletableFuture<Void> send(Object command) {
        return commandGateway.send(command).thenApply(ignored -> null);
    }

    private MaintenanceValidationException validation(String field, String message) {
        return new MaintenanceValidationException("MaintenanceEffectApplicationService", field, message);
    }

    private record EffectContext(
            MaintenanceView caseView,
            List<MaintenanceWorkflowTaskView> effectTasks,
            MaintenanceSnapshotView snapshot,
            List<MaintenanceFieldChangeView> fields,
            RetroactiveEvidence retroactiveEvidence,
            MaintenanceAmountEffect amountEffect,
            MaintenanceFieldConflictOperationResult refreshedConflicts) {

        private EffectContext withRefreshedConflicts(MaintenanceFieldConflictOperationResult result) {
            return new EffectContext(
                    caseView, effectTasks, snapshot, fields, retroactiveEvidence, amountEffect, result);
        }

        private long expectedPolicyVersion() {
            Long version = refreshedConflicts == null
                    ? snapshotPolicyVersion()
                    : refreshedConflicts.policyVersion();
            if (version == null || version < 0) {
                throw new MaintenanceValidationException(
                        "MaintenanceEffectApplicationService", "proposedPolicyVersion",
                        "拟变更快照缺少有效 Policy 版本");
            }
            return version;
        }

        private String proposedSnapshotHash() {
            String hash = refreshedConflicts == null
                    ? snapshotContentHash()
                    : refreshedConflicts.proposedSnapshotHash();
            if (hash == null || !hash.matches("[a-fA-F0-9]{64}")) {
                throw new MaintenanceValidationException(
                        "MaintenanceEffectApplicationService", "proposedSnapshotHash",
                        "拟变更快照缺少合法 SHA-256 摘要");
            }
            return hash.toLowerCase();
        }

        private Long snapshotPolicyVersion() {
            if (snapshot.getProposedPolicyVersion() != null || !fields.isEmpty()) {
                return snapshot.getProposedPolicyVersion();
            }
            return snapshot.getBeforePolicyVersion();
        }

        private String snapshotContentHash() {
            if (snapshot.getProposedContentHash() != null || !fields.isEmpty()) {
                return snapshot.getProposedContentHash();
            }
            return snapshot.getBeforeContentHash();
        }

        private List<FieldChange> applicationFieldChanges() {
            if (refreshedConflicts != null) {
                return refreshedConflicts.fieldChanges().stream()
                        .filter(change -> !change.currentValue().equals(change.proposedValue()))
                        .map(change -> new FieldChange(
                                change.itemCode(), change.objectId(), change.fieldCode(),
                                change.proposedValue().dataType().getCode(),
                                change.proposedValue().canonicalValue()))
                        .toList();
            }
            return fields.stream()
                    .filter(field -> !Objects.equals(field.getCurrentValue(), field.getProposedValue()))
                    .map(field -> new FieldChange(
                            field.getItemCode(), field.getObjectId(), field.getFieldCode(),
                            field.getDataType().getCode(), field.getProposedValue()))
                    .toList();
        }

        private List<String> taskIds() {
            return effectTasks.stream().map(MaintenanceWorkflowTaskView::getTaskId).sorted().toList();
        }

        private int retryCount() {
            return effectTasks.stream().mapToInt(MaintenanceWorkflowTaskView::getRetryCount).max().orElse(0);
        }
    }
}
