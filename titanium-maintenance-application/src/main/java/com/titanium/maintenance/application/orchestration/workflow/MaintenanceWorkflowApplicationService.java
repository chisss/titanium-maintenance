package com.titanium.maintenance.application.orchestration.workflow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import com.titanium.maintenance.application.command.MaintenanceAutomaticReviewInput;
import com.titanium.maintenance.application.command.MaintenanceManualReviewInput;
import com.titanium.maintenance.application.command.MaintenancePremiumQuoteInput;
import com.titanium.maintenance.application.command.MaintenanceUnderwritingAssessmentInput;
import com.titanium.maintenance.application.command.MaintenanceWorkflowTaskOperationInput;
import com.titanium.maintenance.application.model.MaintenanceAutomaticReviewResult;
import com.titanium.maintenance.application.model.MaintenancePremiumQuoteResult;
import com.titanium.maintenance.application.model.MaintenanceUnderwritingAssessmentResult;
import com.titanium.maintenance.command.ClaimMaintenanceWorkflowTaskCommand;
import com.titanium.maintenance.command.CompleteMaintenanceWorkflowTaskCommand;
import com.titanium.maintenance.command.DecideMaintenanceReviewCommand;
import com.titanium.maintenance.command.DecideMaintenanceUnderwritingCommand;
import com.titanium.maintenance.command.DecideMaintenanceWorkflowConditionCommand;
import com.titanium.maintenance.command.FailMaintenanceWorkflowTaskCommand;
import com.titanium.maintenance.command.RecordMaintenancePremiumQuoteCommand;
import com.titanium.maintenance.command.RetryMaintenanceWorkflowTaskCommand;
import com.titanium.maintenance.command.StartMaintenanceWorkflowTaskCommand;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.change.MaintenanceFieldConflictStatus;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceFeeMode;
import com.titanium.maintenance.common.enums.config.MaintenanceStepMode;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceAutomaticReviewOutcome;
import com.titanium.maintenance.common.enums.workflow.MaintenancePremiumQuoteStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceReviewDecision;
import com.titanium.maintenance.common.enums.workflow.MaintenanceReviewMode;
import com.titanium.maintenance.common.enums.workflow.MaintenanceUnderwritingConclusion;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowConditionDecision;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowTaskStatus;
import com.titanium.maintenance.common.exception.MaintenanceNotFoundException;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.configuration.MaintenanceItemConfiguration;
import com.titanium.maintenance.port.MaintenanceUnderwritingPort;
import com.titanium.maintenance.port.MaintenanceUnderwritingPort.AssessmentFact;
import com.titanium.maintenance.port.MaintenanceUnderwritingPort.AssessmentRequest;
import com.titanium.maintenance.port.MaintenanceUnderwritingPort.RiskFieldChange;
import com.titanium.maintenance.port.PolicyServicePort;
import com.titanium.maintenance.port.PolicyServicePort.PolicyFinancialSnapshot;
import com.titanium.maintenance.port.ProductMaintenancePremiumQuotePort;
import com.titanium.maintenance.port.ProductMaintenancePremiumQuotePort.QuoteFact;
import com.titanium.maintenance.port.ProductMaintenancePremiumQuotePort.QuoteRequest;
import com.titanium.maintenance.port.ProductMaintenancePremiumQuotePort.SnapshotReference;
import com.titanium.maintenance.port.ProductMaintenancePremiumQuotePort.UnderwritingAdjustment;
import com.titanium.maintenance.port.ProductSurrenderValuePort;
import com.titanium.maintenance.port.ProductSurrenderValuePort.SurrenderFact;
import com.titanium.maintenance.port.ProductSurrenderValuePort.SurrenderRequest;
import com.titanium.maintenance.port.TenantTimeZonePort;
import com.titanium.maintenance.query.repository.MaintenanceCaseItemViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceFieldChangeViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceSnapshotViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceWorkflowTaskViewRepository;
import com.titanium.maintenance.query.view.MaintenanceCaseItemView;
import com.titanium.maintenance.query.view.MaintenanceFieldChangeView;
import com.titanium.maintenance.query.view.MaintenanceSnapshotView;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.query.view.MaintenanceWorkflowTaskView;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.item.MaintenanceItemCode;
import com.titanium.maintenance.valueobject.workflow.MaintenancePremiumQuoteEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceUnderwritingEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceWorkflowReviewEvidence;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

import lombok.RequiredArgsConstructor;

/** 独立案件任务写编排；先按租户确认操作上下文，再发送聚合命令。 */
@Service
@RequiredArgsConstructor
public class MaintenanceWorkflowApplicationService {

    private final CommandGateway commandGateway;
    private final MaintenanceViewRepository maintenanceViewRepository;
    private final MaintenanceWorkflowTaskViewRepository workflowTaskViewRepository;
    private final MaintenanceCaseItemViewRepository caseItemViewRepository;
    private final MaintenanceFieldChangeViewRepository fieldChangeViewRepository;
    private final MaintenanceItemConfigurationRepository configurationRepository;
    private final MaintenanceReviewPolicyEvaluator reviewPolicyEvaluator;
    private final MaintenanceUnderwritingPort underwritingPort;
    private final MaintenanceSnapshotViewRepository snapshotViewRepository;
    private final ProductMaintenancePremiumQuotePort premiumQuotePort;
    private final PolicyServicePort policyServicePort;
    private final ProductSurrenderValuePort surrenderValuePort;
    private final TenantTimeZonePort tenantTimeZonePort;

    public CompletableFuture<Void> claim(MaintenanceWorkflowTaskOperationInput input) {
        requireTask(input);
        return send(new ClaimMaintenanceWorkflowTaskCommand(
                id(input), input.taskId(), input.operationId(), input.operatorId()));
    }

    public CompletableFuture<Void> start(MaintenanceWorkflowTaskOperationInput input) {
        requireTask(input);
        return send(new StartMaintenanceWorkflowTaskCommand(
                id(input), input.taskId(), input.operationId(), input.operatorId()));
    }

    public CompletableFuture<Void> complete(MaintenanceWorkflowTaskOperationInput input) {
        MaintenanceWorkflowTaskView task = requireTask(input);
        if (task.getStepType() == MaintenanceStepType.VALIDATION
                && input.source() != MaintenanceChannel.API) {
            throw new MaintenanceValidationException(
                    "MaintenanceWorkflowApplicationService", "source",
                    "业务校验任务只能由取得权威证据的系统API完成");
        }
        return send(new CompleteMaintenanceWorkflowTaskCommand(
                id(input), input.taskId(), input.operationId(), input.evidenceVersion(),
                input.evidenceHash(), input.resultCode(), input.reason(), input.operatorId()));
    }

    public CompletableFuture<Void> fail(MaintenanceWorkflowTaskOperationInput input) {
        requireTask(input);
        return send(new FailMaintenanceWorkflowTaskCommand(
                id(input), input.taskId(), input.operationId(),
                input.resultCode(), input.reason(), input.operatorId()));
    }

    public CompletableFuture<Void> retry(MaintenanceWorkflowTaskOperationInput input) {
        requireTask(input);
        return send(new RetryMaintenanceWorkflowTaskCommand(
                id(input), input.taskId(), input.operationId(), input.reason(), input.operatorId()));
    }

    public CompletableFuture<Void> decideCondition(MaintenanceWorkflowTaskOperationInput input) {
        requireTask(input);
        if (input.source() != MaintenanceChannel.API) {
            throw new MaintenanceValidationException(
                    "MaintenanceWorkflowApplicationService", "source",
                    "条件结论只能由权威规则调用方提交");
        }
        return send(new DecideMaintenanceWorkflowConditionCommand(
                id(input), input.taskId(), input.operationId(), input.evidenceVersion(),
                input.evidenceHash(), input.conditionDecision(), input.reason(), input.operatorId()));
    }

    /** 人工审核必须由当前领取人决定，并与建案人保持职责分离。 */
    public CompletableFuture<Void> decideReview(MaintenanceManualReviewInput input) {
        ReviewContext context = requireReviewContext(
                input.maintenanceId(), input.taskId(), input.tenantId());
        if (input.source() != MaintenanceChannel.MANUAL) {
            throw validation("source", "人工审核只能从后台人工路由发起");
        }
        if (input.decision() == null || input.policyVersion() == null
                || input.policyVersion().isBlank() || input.comment() == null
                || input.comment().isBlank()) {
            throw validation("reviewEvidence", "人工审核必须包含结论、策略版本和意见");
        }
        requireReviewerSeparation(context.caseView(), input.operatorId());
        MaintenanceItemConfiguration configuration = requireFrozenConfiguration(context);
        String policyCode = configuration.getDefinition().controls().approvalPolicyCode();
        if (policyCode == null) {
            throw validation("approvalPolicyCode", "冻结保全项未配置审核策略");
        }
        MaintenanceWorkflowReviewEvidence evidence = new MaintenanceWorkflowReviewEvidence(
                MaintenanceReviewMode.MANUAL, input.decision(), policyCode,
                input.policyVersion(), List.of(), input.comment(), LocalDateTime.now(),
                input.operatorId());
        return send(new DecideMaintenanceReviewCommand(
                MaintenanceId.of(input.maintenanceId()), input.taskId(), input.operationId(),
                evidence, input.operatorId()));
    }

    /** 自动审核只有在七类门禁全部通过时写入通过事实，否则原任务留给人工接管。 */
    public CompletableFuture<MaintenanceAutomaticReviewResult> automaticReview(
            MaintenanceAutomaticReviewInput input) {
        ReviewContext context = requireReviewContext(
                input.maintenanceId(), input.taskId(), input.tenantId());
        MaintenanceItemConfiguration configuration = requireFrozenConfiguration(context);
        MaintenanceReviewPolicyEvaluator.Evaluation evaluation = reviewPolicyEvaluator.evaluate(
                context.caseView(), context.taskView(), context.itemView(), configuration, input);
        if (!evaluation.approved()) {
            return CompletableFuture.completedFuture(new MaintenanceAutomaticReviewResult(
                    MaintenanceAutomaticReviewOutcome.MANUAL_REQUIRED,
                    evaluation.policyCode(), evaluation.policyVersion(), evaluation.reasons()));
        }
        MaintenanceWorkflowReviewEvidence evidence = new MaintenanceWorkflowReviewEvidence(
                MaintenanceReviewMode.AUTOMATIC, MaintenanceReviewDecision.APPROVE,
                evaluation.policyCode(), evaluation.policyVersion(), evaluation.gates(),
                evaluation.approvalComment(), LocalDateTime.now(), input.operatorId());
        return send(new DecideMaintenanceReviewCommand(
                MaintenanceId.of(input.maintenanceId()), input.taskId(), input.operationId(),
                evidence, input.operatorId()))
                .thenApply(ignored -> new MaintenanceAutomaticReviewResult(
                        MaintenanceAutomaticReviewOutcome.APPROVED,
                        evaluation.policyCode(), evaluation.policyVersion(), List.of()));
    }

    /** 从案件冻结事实组装保全风险输入，并仅写入 Underwriting 权威返回的结论。 */
    public CompletableFuture<MaintenanceUnderwritingAssessmentResult> assessUnderwriting(
            MaintenanceUnderwritingAssessmentInput input) {
        UnderwritingContext context = requireUnderwritingContext(
                input.maintenanceId(), input.taskId(), input.tenantId());
        MaintenanceItemConfiguration configuration = requireFrozenConfiguration(
                context.caseView(), context.itemView());
        boolean configurationRequires = configurationRequiresUnderwriting(
                configuration, context.taskView());
        List<MaintenanceFieldChangeView> fieldViews = fieldChangeViewRepository
                .findByTenantIdAndMaintenanceIdAndItemCodeOrderByFieldCodeAscObjectIdAsc(
                        input.tenantId(), input.maintenanceId(), context.itemView().getItemCode());
        if (fieldViews.stream().anyMatch(
                field -> field.getConflictStatus() == MaintenanceFieldConflictStatus.DETECTED)) {
            throw validation("fieldChanges", "存在未解决字段冲突，不能提交核保");
        }
        List<RiskFieldChange> riskFieldChanges = fieldViews.stream()
                .filter(field -> !Objects.equals(field.getBaseValue(), field.getProposedValue()))
                .map(field -> new RiskFieldChange(
                        field.getObjectId(), field.getFieldCode(), field.getDataType().getCode(),
                        field.getBaseValue(), field.getProposedValue(), field.getChangeTypeCode()))
                .toList();
        String idempotencyKey = input.maintenanceId() + ":" + input.taskId();
        AssessmentRequest request = new AssessmentRequest(
                input.tenantId(), input.maintenanceId(), context.caseView().getPolicyId(),
                context.caseView().getPolicyBaselineVersion(), context.caseView().getProductId(),
                context.caseView().getProductVersion(), context.caseView().getPlanVersion(),
                context.itemView().getItemCode(), context.itemView().getConfigurationVersion(),
                context.itemView().getConfigurationContentHash(), configurationRequires,
                riskFieldChanges, idempotencyKey, input.operatorId());
        AssessmentFact fact = underwritingPort.assess(request);
        validateUnderwritingFact(request, fact);
        MaintenanceUnderwritingEvidence evidence = new MaintenanceUnderwritingEvidence(
                fact.underwritingCaseId(), fact.payloadHash(), fact.ruleVersion(), fact.modelVersion(),
                fact.conclusion(), fact.additionalConditions(), fact.summary(), fact.completedAt());
        DecideMaintenanceUnderwritingCommand command = new DecideMaintenanceUnderwritingCommand(
                MaintenanceId.of(input.maintenanceId()), input.taskId(), input.operationId(),
                evidence, input.operatorId());
        return send(command).thenApply(ignored -> new MaintenanceUnderwritingAssessmentResult(
                fact.underwritingCaseId(), fact.conclusion(), fact.ruleVersion(), fact.modelVersion(),
                fact.additionalConditions(), fact.summary(), fact.completedAt()));
    }

    /** 根据冻结费用模式记录无需报价，或调用 Product 取得不可变版本化报价。 */
    public CompletableFuture<MaintenancePremiumQuoteResult> quotePremium(
            MaintenancePremiumQuoteInput input) {
        PremiumQuoteContext context = requirePremiumQuoteContext(input);
        MaintenanceFeeMode feeMode = context.configuration().getDefinition().feeMode();
        validateFeeTaskMode(context.taskView(), feeMode);
        if (feeMode == MaintenanceFeeMode.NONE) {
            return recordNotRequired(input, context, "冻结配置为无费用，无需 Product 报价");
        }
        if (feeMode == MaintenanceFeeMode.OPTIONAL
                && context.taskView().getStatus() == MaintenanceWorkflowTaskStatus.SKIPPED) {
            if (context.taskView().getConditionDecision() != MaintenanceWorkflowConditionDecision.SKIP) {
                throw validation("conditionEvidence", "条件费用步骤缺少权威跳过结论");
            }
            return recordNotRequired(input, context, "条件费用规则判定为跳过，无需 Product 报价");
        }
        if (isPolicyTermination(context.itemView())) {
            return quoteSurrender(input, context);
        }

        MaintenanceSnapshotView snapshots = snapshotViewRepository
                .findByMaintenanceIdAndTenantId(input.maintenanceId(), input.tenantId())
                .orElseThrow(MaintenanceNotFoundException::new);
        QuoteRequest request = quoteRequest(input, context, snapshots);
        QuoteFact fact = premiumQuotePort.quote(request);
        validateQuoteFact(request, fact);
        MaintenancePremiumQuoteEvidence evidence = new MaintenancePremiumQuoteEvidence(
                MaintenancePremiumQuoteStatus.QUOTED, fact.quoteId(), fact.quoteVersion(),
                fact.payloadHash(), fact.originalCalculationId(), fact.originalResultHash(),
                fact.replacementCalculationId(), fact.replacementResultHash(),
                fact.pricingPlanVersion(), fact.pricingPlanContentHash(), fact.resultHash(),
                fact.detailSummary(), fact.direction(), fact.amount(), fact.currency(),
                fact.quotedAt(), fact.validUntil());
        if (evidence.expiredAt(LocalDateTime.now())) {
            throw validation("validUntil", "Product 报价已过期，必须使用新操作号重新报价");
        }
        return recordQuote(input, evidence);
    }

    /** 退保费用由 Product 现金价值策略计算，调用方不能通过通用保费参数覆盖结果。 */
    private CompletableFuture<MaintenancePremiumQuoteResult> quoteSurrender(
            MaintenancePremiumQuoteInput input,
            PremiumQuoteContext context) {
        LocalDateTime effectiveAt = pricingBusinessTime(context.caseView());
        PolicyFinancialSnapshot policy = requirePolicyFinancialSnapshot(input, context, effectiveAt);
        SurrenderRequest request = new SurrenderRequest(
                input.tenantId(), input.maintenanceId(), context.caseView().getPolicyId(),
                policy.issuanceBizNo(), requireText("originalCalculationId", input.originalCalculationId()),
                policy.effectiveDate(), effectiveAt.toLocalDate(), requireInteger("policyYear", input.policyYear()),
                effectiveAt, input.reason());
        SurrenderFact fact = surrenderValuePort.calculate(request);
        validateSurrenderFact(request, policy, fact);
        MaintenancePremiumQuoteEvidence evidence = MaintenancePremiumQuoteEvidence.fromSurrender(
                fact, surrenderQuotedAt(context.taskView(), fact));
        return recordQuote(input, evidence);
    }

    private LocalDateTime surrenderQuotedAt(
            MaintenanceWorkflowTaskView task,
            SurrenderFact fact) {
        if (task.getPremiumQuoteStatus() == MaintenancePremiumQuoteStatus.QUOTED
                && task.getPremiumQuotedAt() != null
                && Objects.equals(task.getPremiumQuoteId(), fact.adjustmentId())
                && Objects.equals(task.getPremiumQuoteRequestHash(), fact.requestHash())
                && Objects.equals(task.getPremiumQuoteResultHash(), fact.adjustmentResultHash())) {
            return task.getPremiumQuotedAt();
        }
        return LocalDateTime.now();
    }

    private PolicyFinancialSnapshot requirePolicyFinancialSnapshot(
            MaintenancePremiumQuoteInput input,
            PremiumQuoteContext context,
            LocalDateTime effectiveAt) {
        PolicyFinancialSnapshot policy = policyServicePort.getPolicyFinancialSnapshot(
                context.caseView().getPolicyId(), input.tenantId());
        if (policy == null || blank(policy.productId()) || blank(policy.issuanceBizNo())
                || policy.effectiveDate() == null || policy.premium() == null || policy.premium().signum() < 0
                || blank(policy.currency()) || !Objects.equals(context.caseView().getProductId(), policy.productId())
                || effectiveAt.toLocalDate().isBefore(policy.effectiveDate())) {
            throw validation("policyFinancialSnapshot", "Policy 未返回与案件勾稽的退保财务快照");
        }
        if (!blank(input.currency()) && !policy.currency().equalsIgnoreCase(input.currency())) {
            throw validation("currency", "请求币种与 Policy 财务快照不一致");
        }
        return policy;
    }

    private void validateSurrenderFact(
            SurrenderRequest request,
            PolicyFinancialSnapshot policy,
            SurrenderFact fact) {
        boolean zeroRefund = fact != null && fact.refundAmount() != null
                && fact.refundAmount().signum() == 0;
        boolean invalidDirection = fact == null || fact.direction() == null
                || (zeroRefund && fact.direction() != MaintenanceBalanceDirection.NONE)
                || (!zeroRefund && fact.direction() != MaintenanceBalanceDirection.CREDIT);
        if (fact == null || !Objects.equals(request.surrenderRequestId(), fact.surrenderRequestId())
                || !Objects.equals(request.originalCalculationId(), fact.originalCalculationId())
                || !Objects.equals(request.policyYear(), fact.policyYear())
                || !policy.currency().equalsIgnoreCase(fact.currency())
                || blank(fact.policyCode()) || blank(fact.policyVersion()) || !hash(fact.policyContentHash())
                || fact.coolingOffDays() == null || fact.coolingOffDays() < 0 || blank(fact.refundType())
                || fact.withinCoolingOff() == null || fact.cashValueRate() == null
                || fact.cashValueRate().signum() < 0 || fact.refundAmount() == null
                || fact.refundAmount().signum() < 0 || fact.retainedCustomerAmount() == null
                || fact.retainedCustomerAmount().signum() < 0 || fact.internalCostRetentionRate() == null
                || fact.internalCostRetentionRate().signum() < 0 || blank(fact.replacementCalculationId())
                || !hash(fact.originalResultHash()) || !hash(fact.replacementResultHash())
                || blank(fact.adjustmentId()) || !hash(fact.requestHash())
                || !hash(fact.adjustmentResultHash()) || blank(fact.pricingPlanVersion())
                || !hash(fact.pricingPlanContentHash()) || fact.amount() == null
                || fact.amount().compareTo(fact.refundAmount()) != 0 || invalidDirection) {
            throw validation("surrenderFact", "Product 退保价值事实不完整或无法与案件勾稽");
        }
    }

    private boolean isPolicyTermination(MaintenanceCaseItemView itemView) {
        return MaintenanceItemCode.of(itemView.getItemCode()).legacyMaintenanceType()
                == MaintenanceType.POLICY_TERMINATION;
    }

    private CompletableFuture<MaintenancePremiumQuoteResult> recordQuote(
            MaintenancePremiumQuoteInput input,
            MaintenancePremiumQuoteEvidence evidence) {
        RecordMaintenancePremiumQuoteCommand command = new RecordMaintenancePremiumQuoteCommand(
                MaintenanceId.of(input.maintenanceId()), input.taskId(), input.operationId(),
                evidence, input.operatorId());
        return send(command).thenApply(ignored -> result(evidence));
    }

    private CompletableFuture<MaintenancePremiumQuoteResult> recordNotRequired(
            MaintenancePremiumQuoteInput input,
            PremiumQuoteContext context,
            String reason) {
        LocalDateTime decidedAt = context.taskView().getConditionDecidedAt() == null
                ? context.caseView().getCreateTime()
                : context.taskView().getConditionDecidedAt();
        MaintenancePremiumQuoteEvidence evidence = MaintenancePremiumQuoteEvidence.notRequired(reason, decidedAt);
        return send(new RecordMaintenancePremiumQuoteCommand(
                MaintenanceId.of(input.maintenanceId()), input.taskId(), input.operationId(),
                evidence, input.operatorId())).thenApply(ignored -> result(evidence));
    }

    private PremiumQuoteContext requirePremiumQuoteContext(MaintenancePremiumQuoteInput input) {
        if (input == null || blank(input.maintenanceId()) || blank(input.taskId())
                || blank(input.operationId()) || blank(input.reason())
                || blank(input.operatorId()) || blank(input.tenantId()) || input.source() == null) {
            throw validation("quoteInput", "报价操作上下文不完整");
        }
        MaintenanceView caseView = requireCase(input.maintenanceId(), input.tenantId());
        MaintenanceWorkflowTaskView taskView = workflowTaskViewRepository
                .findByTenantIdAndMaintenanceIdAndTaskId(
                        input.tenantId(), input.maintenanceId(), input.taskId())
                .orElseThrow(MaintenanceNotFoundException::new);
        if (taskView.getStepType() != MaintenanceStepType.FEE_SETTLEMENT) {
            throw validation("taskId", "目标任务不是收退费步骤");
        }
        MaintenanceCaseItemView itemView = caseItemViewRepository
                .findByTenantIdAndMaintenanceIdAndItemCode(
                        input.tenantId(), input.maintenanceId(), taskView.getItemCode())
                .orElseThrow(MaintenanceNotFoundException::new);
        return new PremiumQuoteContext(
                caseView, taskView, itemView, requireFrozenConfiguration(caseView, itemView));
    }

    private QuoteRequest quoteRequest(
            MaintenancePremiumQuoteInput input,
            PremiumQuoteContext context,
            MaintenanceSnapshotView snapshots) {
        MaintenanceView caseView = context.caseView();
        if (snapshots.getBeforeContentHash() == null || snapshots.getProposedContentHash() == null) {
            throw validation("snapshots", "Product 报价前必须形成 before/proposed 快照引用");
        }
        return new QuoteRequest(
                input.tenantId(), caseView.getProductId(), input.maintenanceId(), caseView.getPolicyId(),
                requireBaselineVersion(caseView), context.taskView().getItemCode(), caseView.getProductVersion(),
                caseView.getPlanVersion(), input.lifecycleType(), beforeSnapshot(snapshots),
                proposedSnapshot(snapshots), input.originalCalculationId(),
                pricingBusinessTime(caseView), input.currency(), input.sumInsured(),
                requireInteger("age", input.age()), input.gender(),
                requireInteger("paymentTermYears", input.paymentTermYears()),
                requireInteger("coverageTermYears", input.coverageTermYears()),
                requireInteger("paymentPeriods", input.paymentPeriods()), input.pricingFactors(),
                input.underwritingAdjustments().stream()
                        .map(item -> new UnderwritingAdjustment(
                                item.adjustmentCode(), item.type(), item.value(),
                                item.reason(), item.ruleVersion()))
                        .toList(),
                input.channelId(), requireInteger("policyYear", input.policyYear()), input.reason(),
                quoteIdempotencyKey(input));
    }

    private void validateFeeTaskMode(MaintenanceWorkflowTaskView task, MaintenanceFeeMode feeMode) {
        MaintenanceStepMode expected = switch (feeMode) {
            case NONE -> MaintenanceStepMode.SKIPPED;
            case REQUIRED -> MaintenanceStepMode.REQUIRED;
            case OPTIONAL -> MaintenanceStepMode.CONDITIONAL;
        };
        if (task.getMode() != expected) {
            throw validation("workflowConfiguration", "费用任务模式与冻结 feeMode 不一致");
        }
    }

    private void validateQuoteFact(QuoteRequest request, QuoteFact fact) {
        if (fact == null
                || !Objects.equals(request.tenantId(), fact.tenantId())
                || !Objects.equals(request.maintenanceId(), fact.maintenanceId())
                || !Objects.equals(request.policyId(), fact.policyId())
                || request.policyBaselineVersion() != fact.policyBaselineVersion()
                || !Objects.equals(request.productId(), fact.productId())
                || !Objects.equals(request.productVersion(), fact.productVersion())
                || !Objects.equals(request.planVersion(), fact.planVersion())
                || !Objects.equals(request.itemCode(), fact.itemCode())
                || !Objects.equals(request.beforeSnapshot().contentHash(), fact.beforeSnapshotHash())
                || !Objects.equals(request.proposedSnapshot().contentHash(), fact.proposedSnapshotHash())
                || !Objects.equals(request.originalCalculationId(), fact.originalCalculationId())
                || !Objects.equals(request.idempotencyKey(), fact.idempotencyKey())
                || !Objects.equals(request.payloadHash(), fact.payloadHash())) {
            throw validation("quoteFact", "Product 报价结果未通过案件与请求回显勾稽");
        }
    }

    private SnapshotReference beforeSnapshot(MaintenanceSnapshotView view) {
        return new SnapshotReference(
                view.getBeforeStorageKey(), view.getBeforeContentHash(),
                requireVersion("beforePolicyVersion", view.getBeforePolicyVersion()),
                offsetTime("beforeCapturedAt", view.getBeforeCapturedAt()));
    }

    private SnapshotReference proposedSnapshot(MaintenanceSnapshotView view) {
        return new SnapshotReference(
                view.getProposedStorageKey(), view.getProposedContentHash(),
                requireVersion("proposedPolicyVersion", view.getProposedPolicyVersion()),
                offsetTime("proposedCapturedAt", view.getProposedCapturedAt()));
    }

    private long requireBaselineVersion(MaintenanceView view) {
        return requireVersion("policyBaselineVersion", view.getPolicyBaselineVersion());
    }

    private long requireVersion(String field, Long value) {
        if (value == null || value < 0) {
            throw validation(field, "版本不能为空且不能为负数");
        }
        return value;
    }

    private int requireInteger(String field, Integer value) {
        if (value == null) {
            throw validation(field, "字段不能为空");
        }
        return value;
    }

    private String requireText(String field, String value) {
        if (blank(value)) {
            throw validation(field, "字段不能为空");
        }
        return value.trim();
    }

    private boolean hash(String value) {
        return value != null && value.matches("[0-9a-fA-F]{64}");
    }

    private LocalDateTime businessTime(String value, String tenantId) {
        if (blank(value)) {
            throw validation("businessEffectiveAt", "案件缺少业务时点");
        }
        try {
            String zoneId = tenantTimeZonePort.resolveZoneId(tenantId);
            return OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.of(zoneId)).toLocalDateTime();
        } catch (DateTimeParseException exception) {
            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException ignored) {
                throw validation("businessEffectiveAt", "案件业务时点格式不合法");
            }
        } catch (DateTimeException | NullPointerException exception) {
            throw validation("tenantZoneId", "租户业务时区无效");
        }
    }

    private LocalDateTime pricingBusinessTime(MaintenanceView view) {
        if (view.getEffectiveTimeType() == EffectiveTimeType.RETROACTIVE) {
            if (view.getSpecificEffectiveDate() == null) {
                throw validation("specificEffectiveDate", "追溯报价缺少案件指定生效时间");
            }
            return view.getSpecificEffectiveDate();
        }
        return businessTime(view.getBusinessEffectiveAt(), view.getTenantId());
    }

    private OffsetDateTime offsetTime(String field, String value) {
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw validation(field, "快照采集时间格式不合法");
        }
    }

    private String quoteIdempotencyKey(MaintenancePremiumQuoteInput input) {
        String source = input.maintenanceId() + ':' + input.taskId() + ':' + input.operationId();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK缺少SHA-256算法", exception);
        }
    }

    private MaintenancePremiumQuoteResult result(MaintenancePremiumQuoteEvidence evidence) {
        return new MaintenancePremiumQuoteResult(
                evidence.status(), evidence.quoteId(), evidence.quoteVersion(),
                evidence.originalCalculationId(), evidence.replacementCalculationId(),
                evidence.pricingPlanVersion(), evidence.resultHash(), evidence.detailSummary(),
                evidence.direction(), evidence.amount(), evidence.currency(), evidence.quotedAt(),
                evidence.validUntil());
    }

    private MaintenanceWorkflowTaskView requireTask(MaintenanceWorkflowTaskOperationInput input) {
        requireCase(input.maintenanceId(), input.tenantId());
        return workflowTaskViewRepository
                .findByTenantIdAndMaintenanceIdAndTaskId(
                        input.tenantId(), input.maintenanceId(), input.taskId())
                .orElseThrow(MaintenanceNotFoundException::new);
    }

    private MaintenanceView requireCase(String maintenanceId, String tenantId) {
        return maintenanceViewRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        maintenanceId, tenantId)
                .orElseThrow(MaintenanceNotFoundException::new);
    }

    private ReviewContext requireReviewContext(String maintenanceId, String taskId, String tenantId) {
        MaintenanceView caseView = requireCase(maintenanceId, tenantId);
        MaintenanceWorkflowTaskView taskView = workflowTaskViewRepository
                .findByTenantIdAndMaintenanceIdAndTaskId(
                        tenantId, maintenanceId, taskId)
                .orElseThrow(MaintenanceNotFoundException::new);
        if (taskView.getStepType() != MaintenanceStepType.REVIEW) {
            throw validation("taskId", "目标任务不是审核步骤");
        }
        MaintenanceCaseItemView itemView = caseItemViewRepository
                .findByTenantIdAndMaintenanceIdAndItemCode(
                        tenantId, maintenanceId, taskView.getItemCode())
                .orElseThrow(MaintenanceNotFoundException::new);
        return new ReviewContext(caseView, taskView, itemView);
    }

    private MaintenanceItemConfiguration requireFrozenConfiguration(ReviewContext context) {
        return requireFrozenConfiguration(context.caseView(), context.itemView());
    }

    private MaintenanceItemConfiguration requireFrozenConfiguration(
            MaintenanceView caseView,
            MaintenanceCaseItemView itemView) {
        String configurationId = itemView.getConfigurationId();
        if (configurationId == null || configurationId.isBlank()) {
            throw validation("configurationId", "审核任务缺少冻结配置标识");
        }
        MaintenanceItemConfiguration configuration = configurationRepository
                .findById(caseView.getTenantId(), configurationId)
                .orElseThrow(MaintenanceNotFoundException::new)
                .configuration();
        if (!Objects.equals(itemView.getItemCode(), configuration.getDefinition().itemCode())
                || !Objects.equals(itemView.getConfigurationVersion(),
                        configuration.getDefinition().version())
                || !Objects.equals(itemView.getConfigurationContentHash(),
                        configuration.getContentHash())) {
            throw validation("configurationContentHash", "冻结配置与审核时读取的配置不一致");
        }
        return configuration;
    }

    private UnderwritingContext requireUnderwritingContext(
            String maintenanceId,
            String taskId,
            String tenantId) {
        MaintenanceView caseView = requireCase(maintenanceId, tenantId);
        MaintenanceWorkflowTaskView taskView = workflowTaskViewRepository
                .findByTenantIdAndMaintenanceIdAndTaskId(tenantId, maintenanceId, taskId)
                .orElseThrow(MaintenanceNotFoundException::new);
        if (taskView.getStepType() != MaintenanceStepType.UNDERWRITING) {
            throw validation("taskId", "目标任务不是核保步骤");
        }
        MaintenanceCaseItemView itemView = caseItemViewRepository
                .findByTenantIdAndMaintenanceIdAndItemCode(
                        tenantId, maintenanceId, taskView.getItemCode())
                .orElseThrow(MaintenanceNotFoundException::new);
        return new UnderwritingContext(caseView, taskView, itemView);
    }

    private boolean configurationRequiresUnderwriting(
            MaintenanceItemConfiguration configuration,
            MaintenanceWorkflowTaskView taskView) {
        MaintenanceStepMode configuredMode = configuration.getDefinition().steps().stream()
                .filter(step -> step.stepType() == MaintenanceStepType.UNDERWRITING)
                .filter(step -> step.sequence() == taskView.getSequence())
                .map(step -> step.mode())
                .findFirst()
                .orElseThrow(() -> validation("workflowConfiguration", "冻结配置缺少目标核保步骤"));
        if (configuredMode != taskView.getMode()) {
            throw validation("workflowConfiguration", "核保任务与冻结配置模式不一致");
        }
        return configuredMode != MaintenanceStepMode.SKIPPED;
    }

    private void validateUnderwritingFact(AssessmentRequest request, AssessmentFact fact) {
        if (fact == null
                || !Objects.equals(request.idempotencyKey(), fact.idempotencyKey())
                || !Objects.equals(request.payloadHash(), fact.payloadHash())) {
            throw validation("underwritingFact", "核保结果未通过请求回显勾稽");
        }
        if (!request.configurationRequiresUnderwriting()
                && (!request.riskFieldChanges().isEmpty()
                        || fact.conclusion() != MaintenanceUnderwritingConclusion.NOT_REQUIRED)) {
            throw validation("underwritingFact", "无需核保缺少配置与风险差异的共同证明");
        }
        if (request.configurationRequiresUnderwriting()
                && fact.conclusion() == MaintenanceUnderwritingConclusion.NOT_REQUIRED) {
            throw validation("underwritingFact", "权威无需核保结论与冻结步骤配置冲突");
        }
    }

    private void requireReviewerSeparation(MaintenanceView caseView, String reviewerId) {
        if (caseView.getCreatedBy() == null || caseView.getCreatedBy().isBlank()
                || reviewerId == null || reviewerId.isBlank()) {
            throw validation("operatorId", "无法证明建案人与审核人职责分离");
        }
        if (caseView.getCreatedBy().equals(reviewerId.trim())) {
            throw validation("operatorId", "建案人与审核人必须分离");
        }
    }

    private MaintenanceId id(MaintenanceWorkflowTaskOperationInput input) {
        return MaintenanceId.of(input.maintenanceId());
    }

    private CompletableFuture<Void> send(Object command) {
        return commandGateway.send(command).thenApply(ignored -> null);
    }

    private MaintenanceValidationException validation(String fieldName, String message) {
        return new MaintenanceValidationException(
                "MaintenanceWorkflowApplicationService", fieldName, message);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record ReviewContext(
            MaintenanceView caseView,
            MaintenanceWorkflowTaskView taskView,
            MaintenanceCaseItemView itemView) {
    }

    private record UnderwritingContext(
            MaintenanceView caseView,
            MaintenanceWorkflowTaskView taskView,
            MaintenanceCaseItemView itemView) {
    }

    private record PremiumQuoteContext(
            MaintenanceView caseView,
            MaintenanceWorkflowTaskView taskView,
            MaintenanceCaseItemView itemView,
            MaintenanceItemConfiguration configuration) {
    }
}
