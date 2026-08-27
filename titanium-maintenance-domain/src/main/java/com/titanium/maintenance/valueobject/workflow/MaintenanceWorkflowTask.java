package com.titanium.maintenance.valueobject.workflow;

import com.titanium.maintenance.common.enums.config.MaintenanceStepMode;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceBillingPostingStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceFundSettlementType;
import com.titanium.maintenance.common.enums.workflow.MaintenancePremiumQuoteStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceReviewDecision;
import com.titanium.maintenance.common.enums.workflow.MaintenanceReviewMode;
import com.titanium.maintenance.common.enums.workflow.MaintenanceUnderwritingConclusion;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowAction;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowConditionDecision;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowTaskStatus;
import com.titanium.maintenance.common.exception.MaintenanceConflictException;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 案件内由冻结步骤定义实例化的不可变流程任务。 */
public record MaintenanceWorkflowTask(
        String taskId,
        String itemCode,
        int itemOrder,
        int sequence,
        MaintenanceStepType stepType,
        MaintenanceStepMode mode,
        String conditionRuleCode,
        MaintenanceWorkflowTaskStatus status,
        MaintenanceWorkflowAssignment assignment,
        int retryCount,
        MaintenanceWorkflowFailure failure,
        MaintenanceWorkflowConditionEvidence conditionEvidence,
        MaintenanceWorkflowReviewEvidence reviewEvidence,
        MaintenanceWorkflowOperation lastOperation,
        MaintenanceUnderwritingEvidence underwritingEvidence,
        MaintenancePremiumQuoteEvidence premiumQuoteEvidence,
        MaintenanceBillingPostingEvidence billingPostingEvidence,
        MaintenanceFundSettlementEvidence fundSettlementEvidence,
        MaintenanceEffectEvidence effectEvidence) {

    public MaintenanceWorkflowTask(
            String taskId,
            String itemCode,
            int itemOrder,
            int sequence,
            MaintenanceStepType stepType,
            MaintenanceStepMode mode,
            String conditionRuleCode,
            MaintenanceWorkflowTaskStatus status) {
        this(taskId, itemCode, itemOrder, sequence, stepType, mode, conditionRuleCode,
                status, null, 0, null, null, null, null, null, null, null, null, null);
    }

    /** 兼容 M4-04 之前事件中的任务结构。 */
    public MaintenanceWorkflowTask(
            String taskId,
            String itemCode,
            int itemOrder,
            int sequence,
            MaintenanceStepType stepType,
            MaintenanceStepMode mode,
            String conditionRuleCode,
            MaintenanceWorkflowTaskStatus status,
            MaintenanceWorkflowAssignment assignment,
            int retryCount,
            MaintenanceWorkflowFailure failure,
            MaintenanceWorkflowConditionEvidence conditionEvidence,
            MaintenanceWorkflowReviewEvidence reviewEvidence,
            MaintenanceWorkflowOperation lastOperation) {
        this(taskId, itemCode, itemOrder, sequence, stepType, mode, conditionRuleCode,
                status, assignment, retryCount, failure, conditionEvidence,
                reviewEvidence, lastOperation, null, null, null, null, null);
    }

    /** 兼容 M4-05 之前不含报价证据的任务事件。 */
    public MaintenanceWorkflowTask(
            String taskId,
            String itemCode,
            int itemOrder,
            int sequence,
            MaintenanceStepType stepType,
            MaintenanceStepMode mode,
            String conditionRuleCode,
            MaintenanceWorkflowTaskStatus status,
            MaintenanceWorkflowAssignment assignment,
            int retryCount,
            MaintenanceWorkflowFailure failure,
            MaintenanceWorkflowConditionEvidence conditionEvidence,
            MaintenanceWorkflowReviewEvidence reviewEvidence,
            MaintenanceWorkflowOperation lastOperation,
            MaintenanceUnderwritingEvidence underwritingEvidence) {
        this(taskId, itemCode, itemOrder, sequence, stepType, mode, conditionRuleCode,
                status, assignment, retryCount, failure, conditionEvidence,
                reviewEvidence, lastOperation, underwritingEvidence, null, null, null, null);
    }

    /** 兼容 M4-06 之前不含 Billing 与资金证据的任务事件。 */
    public MaintenanceWorkflowTask(
            String taskId,
            String itemCode,
            int itemOrder,
            int sequence,
            MaintenanceStepType stepType,
            MaintenanceStepMode mode,
            String conditionRuleCode,
            MaintenanceWorkflowTaskStatus status,
            MaintenanceWorkflowAssignment assignment,
            int retryCount,
            MaintenanceWorkflowFailure failure,
            MaintenanceWorkflowConditionEvidence conditionEvidence,
            MaintenanceWorkflowReviewEvidence reviewEvidence,
            MaintenanceWorkflowOperation lastOperation,
            MaintenanceUnderwritingEvidence underwritingEvidence,
            MaintenancePremiumQuoteEvidence premiumQuoteEvidence) {
        this(taskId, itemCode, itemOrder, sequence, stepType, mode, conditionRuleCode,
                status, assignment, retryCount, failure, conditionEvidence, reviewEvidence,
                lastOperation, underwritingEvidence, premiumQuoteEvidence, null, null, null);
    }

    /** 兼容 M5-01 之前不含生效请求与 Policy 回执证据的任务事件。 */
    public MaintenanceWorkflowTask(
            String taskId,
            String itemCode,
            int itemOrder,
            int sequence,
            MaintenanceStepType stepType,
            MaintenanceStepMode mode,
            String conditionRuleCode,
            MaintenanceWorkflowTaskStatus status,
            MaintenanceWorkflowAssignment assignment,
            int retryCount,
            MaintenanceWorkflowFailure failure,
            MaintenanceWorkflowConditionEvidence conditionEvidence,
            MaintenanceWorkflowReviewEvidence reviewEvidence,
            MaintenanceWorkflowOperation lastOperation,
            MaintenanceUnderwritingEvidence underwritingEvidence,
            MaintenancePremiumQuoteEvidence premiumQuoteEvidence,
            MaintenanceBillingPostingEvidence billingPostingEvidence,
            MaintenanceFundSettlementEvidence fundSettlementEvidence) {
        this(taskId, itemCode, itemOrder, sequence, stepType, mode, conditionRuleCode,
                status, assignment, retryCount, failure, conditionEvidence, reviewEvidence,
                lastOperation, underwritingEvidence, premiumQuoteEvidence,
                billingPostingEvidence, fundSettlementEvidence, null);
    }

    public MaintenanceWorkflowTask {
        taskId = requireText("taskId", taskId);
        itemCode = requireText("itemCode", itemCode);
        if (itemOrder < 0 || sequence <= 0) {
            throw new MaintenanceValidationException(
                    "MaintenanceWorkflowTask", "任务顺序必须合法");
        }
        if (stepType == null || mode == null || status == null) {
            throw new MaintenanceValidationException(
                    "MaintenanceWorkflowTask", "步骤类型、模式和状态不能为空");
        }
        conditionRuleCode = normalize(conditionRuleCode);
        if (mode == MaintenanceStepMode.CONDITIONAL && conditionRuleCode == null) {
            throw new MaintenanceValidationException(
                    "MaintenanceWorkflowTask", "conditionRuleCode", "条件任务必须冻结条件规则");
        }
        if (mode != MaintenanceStepMode.CONDITIONAL && conditionRuleCode != null) {
            throw new MaintenanceValidationException(
                    "MaintenanceWorkflowTask", "conditionRuleCode", "非条件任务不能携带条件规则");
        }
        if (mode == MaintenanceStepMode.SKIPPED && status != MaintenanceWorkflowTaskStatus.SKIPPED) {
            throw new MaintenanceValidationException(
                    "MaintenanceWorkflowTask", "status", "配置跳过任务必须初始化为已跳过");
        }
        if (retryCount < 0) {
            throw new MaintenanceValidationException(
                    "MaintenanceWorkflowTask", "retryCount", "重试次数不能为负数");
        }
        if (conditionEvidence != null && mode != MaintenanceStepMode.CONDITIONAL) {
            throw new MaintenanceValidationException(
                    "MaintenanceWorkflowTask", "conditionEvidence", "非条件任务不能携带条件判定证据");
        }
        if (reviewEvidence != null && stepType != MaintenanceStepType.REVIEW) {
            throw new MaintenanceValidationException(
                    "MaintenanceWorkflowTask", "reviewEvidence", "只有审核任务可以携带审核证据");
        }
        if (underwritingEvidence != null && stepType != MaintenanceStepType.UNDERWRITING) {
            throw new MaintenanceValidationException(
                    "MaintenanceWorkflowTask", "underwritingEvidence", "只有核保任务可以携带核保证据");
        }
        if (premiumQuoteEvidence != null && stepType != MaintenanceStepType.FEE_SETTLEMENT) {
            throw new MaintenanceValidationException(
                    "MaintenanceWorkflowTask", "premiumQuoteEvidence", "只有收退费任务可以携带报价证据");
        }
        if ((billingPostingEvidence != null || fundSettlementEvidence != null)
                && stepType != MaintenanceStepType.FEE_SETTLEMENT) {
            throw new MaintenanceValidationException(
                    "MaintenanceWorkflowTask", "settlementEvidence", "只有收退费任务可以携带结算证据");
        }
        if (billingPostingEvidence != null
                && (premiumQuoteEvidence == null
                        || premiumQuoteEvidence.status() != MaintenancePremiumQuoteStatus.QUOTED)) {
            throw new MaintenanceValidationException(
                    "MaintenanceWorkflowTask", "billingPostingEvidence", "Billing 入账必须关联有效报价");
        }
        if (fundSettlementEvidence != null && billingPostingEvidence == null) {
            throw new MaintenanceValidationException(
                    "MaintenanceWorkflowTask", "fundSettlementEvidence", "资金结果必须关联 Billing 入账");
        }
        if (effectEvidence != null && stepType != MaintenanceStepType.EFFECT) {
            throw new MaintenanceValidationException(
                    "MaintenanceWorkflowTask", "effectEvidence", "只有生效任务可以携带 Policy 应用证据");
        }
        if (effectEvidence != null && effectEvidence.isApplied()
                && status != MaintenanceWorkflowTaskStatus.COMPLETED) {
            throw new MaintenanceValidationException(
                    "MaintenanceWorkflowTask", "effectEvidence", "Policy 已应用的生效任务必须完成");
        }
        if (status == MaintenanceWorkflowTaskStatus.WAITING_CONDITION
                && mode != MaintenanceStepMode.CONDITIONAL) {
            throw new MaintenanceValidationException(
                    "MaintenanceWorkflowTask", "status", "只有条件任务可以等待条件判定");
        }
    }

    /** 领取可处理任务；领取不改变任务状态。 */
    public MaintenanceWorkflowTask claim(MaintenanceWorkflowOperation operation) {
        requireAction(operation, MaintenanceWorkflowAction.CLAIM);
        requireStatus(MaintenanceWorkflowTaskStatus.READY, "领取");
        if (assignment != null) {
            throw invalidTransition("任务已被领取");
        }
        return copy(status, new MaintenanceWorkflowAssignment(
                operation.operatedBy(), operation.operatedAt()), retryCount, failure,
                conditionEvidence, operation);
    }

    /** 已领取任务开始处理。 */
    public MaintenanceWorkflowTask start(MaintenanceWorkflowOperation operation) {
        requireAction(operation, MaintenanceWorkflowAction.START);
        requireStatus(MaintenanceWorkflowTaskStatus.READY, "开始");
        requireAssignee(operation);
        return copy(MaintenanceWorkflowTaskStatus.IN_PROGRESS, assignment, retryCount,
                null, conditionEvidence, operation);
    }

    /** 完成信息录入或业务校验任务，其他门禁必须使用后续专用命令。 */
    public MaintenanceWorkflowTask complete(MaintenanceWorkflowOperation operation) {
        requireAction(operation, MaintenanceWorkflowAction.COMPLETE);
        requireStatus(MaintenanceWorkflowTaskStatus.IN_PROGRESS, "完成");
        requireAssignee(operation);
        if (stepType != MaintenanceStepType.DATA_ENTRY
                && stepType != MaintenanceStepType.VALIDATION) {
            throw invalidTransition("当前步骤必须通过专用业务命令完成");
        }
        if (stepType == MaintenanceStepType.VALIDATION
                && (operation.evidenceVersion() == null
                || operation.evidenceHash() == null
                || operation.resultCode() == null)) {
            throw new MaintenanceValidationException(
                    "CompleteMaintenanceWorkflowTaskCommand", "validationEvidence",
                    "业务校验任务必须携带版本化校验证据");
        }
        return copy(MaintenanceWorkflowTaskStatus.COMPLETED, assignment, retryCount,
                null, conditionEvidence, operation);
    }

    /** 记录处理中任务失败。 */
    public MaintenanceWorkflowTask fail(MaintenanceWorkflowOperation operation) {
        requireAction(operation, MaintenanceWorkflowAction.FAIL);
        requireStatus(MaintenanceWorkflowTaskStatus.IN_PROGRESS, "失败");
        requireAssignee(operation);
        MaintenanceWorkflowFailure newFailure = new MaintenanceWorkflowFailure(
                operation.resultCode(), operation.reason());
        return copy(MaintenanceWorkflowTaskStatus.FAILED, assignment, retryCount,
                newFailure, conditionEvidence, operation);
    }

    /** 失败任务回到可领取状态，并保留累计重试次数。 */
    public MaintenanceWorkflowTask retry(MaintenanceWorkflowOperation operation) {
        requireAction(operation, MaintenanceWorkflowAction.RETRY);
        requireStatus(MaintenanceWorkflowTaskStatus.FAILED, "重试");
        if (operation.reason() == null) {
            throw new MaintenanceValidationException(
                    "RetryMaintenanceWorkflowTaskCommand", "reason", "重试必须说明原因");
        }
        return copy(MaintenanceWorkflowTaskStatus.READY, null, retryCount + 1,
                null, conditionEvidence, operation);
    }

    /** 记录条件规则结论；执行则进入可处理，跳过则形成终态事实。 */
    public MaintenanceWorkflowTask decideCondition(
            MaintenanceWorkflowConditionDecision decision,
            MaintenanceWorkflowOperation operation) {
        requireAction(operation, MaintenanceWorkflowAction.DECIDE_CONDITION);
        requireStatus(MaintenanceWorkflowTaskStatus.WAITING_CONDITION, "条件判定");
        if (decision == null
                || operation.evidenceVersion() == null
                || operation.evidenceHash() == null
                || operation.reason() == null
                || !decision.getCode().equals(operation.resultCode())) {
            throw new MaintenanceValidationException(
                    "DecideMaintenanceWorkflowConditionCommand", "conditionEvidence",
                    "条件判定必须携带一致的规则版本、输入摘要、结论和原因");
        }
        MaintenanceWorkflowConditionEvidence evidence = new MaintenanceWorkflowConditionEvidence(
                operation.evidenceVersion(), operation.evidenceHash(), decision,
                operation.reason(), operation.operatedAt(), operation.operatedBy());
        MaintenanceWorkflowTaskStatus target = decision == MaintenanceWorkflowConditionDecision.EXECUTE
                ? MaintenanceWorkflowTaskStatus.READY
                : MaintenanceWorkflowTaskStatus.SKIPPED;
        return copy(target, null, retryCount, null, evidence, operation);
    }

    /** 以专用审核证据完成或拒绝审核任务。 */
    public MaintenanceWorkflowTask decideReview(
            MaintenanceWorkflowReviewEvidence evidence,
            MaintenanceWorkflowOperation operation) {
        requireAction(operation, MaintenanceWorkflowAction.DECIDE_REVIEW);
        if (stepType != MaintenanceStepType.REVIEW || evidence == null) {
            throw invalidTransition("当前任务不是可审核步骤");
        }
        if (!evidence.policyVersion().equals(operation.evidenceVersion())
                || !evidence.contentHash().equals(operation.evidenceHash())
                || !evidence.decision().getCode().equals(operation.resultCode())
                || !evidence.comment().equals(operation.reason())
                || !evidence.decidedBy().equals(operation.operatedBy())) {
            throw new MaintenanceValidationException(
                    "DecideMaintenanceReviewCommand", "reviewEvidence", "审核证据与操作载荷不一致");
        }
        if (evidence.mode() == MaintenanceReviewMode.MANUAL) {
            requireStatus(MaintenanceWorkflowTaskStatus.IN_PROGRESS, "人工审核");
            requireAssignee(operation);
        } else {
            requireStatus(MaintenanceWorkflowTaskStatus.READY, "自动审核");
            if (assignment != null || evidence.decision() != MaintenanceReviewDecision.APPROVE) {
                throw invalidTransition("自动审核不能覆盖已领取任务或形成拒绝结论");
            }
        }
        MaintenanceWorkflowTaskStatus target = evidence.decision() == MaintenanceReviewDecision.APPROVE
                ? MaintenanceWorkflowTaskStatus.COMPLETED
                : MaintenanceWorkflowTaskStatus.REJECTED;
        return new MaintenanceWorkflowTask(
                taskId, itemCode, itemOrder, sequence, stepType, mode, conditionRuleCode,
                target, assignment, retryCount, null, conditionEvidence, evidence, operation,
                underwritingEvidence, premiumQuoteEvidence);
    }

    /** 以 Underwriting 权威证据完成、拒绝或挂起核保任务。 */
    public MaintenanceWorkflowTask decideUnderwriting(
            MaintenanceUnderwritingEvidence evidence,
            MaintenanceWorkflowOperation operation) {
        requireAction(operation, MaintenanceWorkflowAction.DECIDE_UNDERWRITING);
        if (stepType != MaintenanceStepType.UNDERWRITING || evidence == null) {
            throw invalidTransition("当前任务不是可核保步骤");
        }
        if (!evidence.ruleVersion().equals(operation.evidenceVersion())
                || !evidence.contentHash().equals(operation.evidenceHash())
                || !evidence.conclusion().getCode().equals(operation.resultCode())
                || !evidence.summary().equals(operation.reason())) {
            throw new MaintenanceValidationException(
                    "DecideMaintenanceUnderwritingCommand", "underwritingEvidence", "核保证据与操作载荷不一致");
        }
        if (evidence.conclusion() == MaintenanceUnderwritingConclusion.NOT_REQUIRED) {
            if (mode != MaintenanceStepMode.SKIPPED || status != MaintenanceWorkflowTaskStatus.SKIPPED) {
                throw invalidTransition("只有配置跳过任务可以记录无需核保证据");
            }
            return new MaintenanceWorkflowTask(
                    taskId, itemCode, itemOrder, sequence, stepType, mode, conditionRuleCode,
                    status, assignment, retryCount, failure, conditionEvidence, reviewEvidence,
                    operation, evidence, premiumQuoteEvidence);
        }
        if (mode == MaintenanceStepMode.SKIPPED) {
            throw invalidTransition("配置跳过与权威风险结论冲突");
        }
        if (status != MaintenanceWorkflowTaskStatus.READY
                && status != MaintenanceWorkflowTaskStatus.IN_PROGRESS
                && status != MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL) {
            throw invalidTransition("当前状态不允许记录核保结论: " + status);
        }
        if (status == MaintenanceWorkflowTaskStatus.IN_PROGRESS) {
            requireAssignee(operation);
        }
        MaintenanceWorkflowTaskStatus target = switch (evidence.conclusion()) {
            case APPROVED, CONDITIONAL_APPROVED -> MaintenanceWorkflowTaskStatus.COMPLETED;
            case MANUAL_REVIEW -> MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL;
            case REJECTED -> MaintenanceWorkflowTaskStatus.REJECTED;
            case NOT_REQUIRED -> throw invalidTransition("无需核保结论状态不匹配");
        };
        MaintenanceWorkflowAssignment targetAssignment = target == MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL
                ? null
                : assignment;
        return new MaintenanceWorkflowTask(
                taskId, itemCode, itemOrder, sequence, stepType, mode, conditionRuleCode,
                target, targetAssignment, retryCount, null, conditionEvidence, reviewEvidence,
                operation, evidence, premiumQuoteEvidence);
    }

    /** 记录 Product 报价检查点；报价本身不完成费用步骤，也不激活后继任务。 */
    public MaintenanceWorkflowTask recordPremiumQuote(
            MaintenancePremiumQuoteEvidence evidence,
            MaintenanceWorkflowOperation operation) {
        requireAction(operation, MaintenanceWorkflowAction.RECORD_PREMIUM_QUOTE);
        if (stepType != MaintenanceStepType.FEE_SETTLEMENT || evidence == null) {
            throw invalidTransition("当前任务不是可报价的收退费步骤");
        }
        if (!evidence.evidenceVersion().equals(operation.evidenceVersion())
                || !evidence.contentHash().equals(operation.evidenceHash())
                || !evidence.status().getCode().equals(operation.resultCode())
                || !evidence.detailSummary().equals(operation.reason())) {
            throw new MaintenanceValidationException(
                    "RecordMaintenancePremiumQuoteCommand", "premiumQuoteEvidence", "报价证据与操作载荷不一致");
        }
        if (evidence.status() == MaintenancePremiumQuoteStatus.NOT_REQUIRED) {
            boolean configuredNone = mode == MaintenanceStepMode.SKIPPED
                    && status == MaintenanceWorkflowTaskStatus.SKIPPED;
            boolean optionalSkipped = mode == MaintenanceStepMode.CONDITIONAL
                    && status == MaintenanceWorkflowTaskStatus.SKIPPED
                    && conditionEvidence != null
                    && conditionEvidence.decision() == MaintenanceWorkflowConditionDecision.SKIP;
            if (!configuredNone && !optionalSkipped) {
                throw invalidTransition("当前费用步骤不能记录无需报价结论");
            }
            return new MaintenanceWorkflowTask(
                    taskId, itemCode, itemOrder, sequence, stepType, mode, conditionRuleCode,
                    status, assignment, retryCount, failure, conditionEvidence, reviewEvidence,
                    operation, underwritingEvidence, evidence);
        }
        if (mode == MaintenanceStepMode.SKIPPED
                || (status != MaintenanceWorkflowTaskStatus.READY
                        && status != MaintenanceWorkflowTaskStatus.IN_PROGRESS
                        && status != MaintenanceWorkflowTaskStatus.QUOTED)) {
            throw invalidTransition("当前费用步骤状态不允许记录 Product 报价: " + status);
        }
        if (status == MaintenanceWorkflowTaskStatus.IN_PROGRESS) {
            requireAssignee(operation);
        }
        return new MaintenanceWorkflowTask(
                taskId, itemCode, itemOrder, sequence, stepType, mode, conditionRuleCode,
                MaintenanceWorkflowTaskStatus.QUOTED, assignment, retryCount, null,
                conditionEvidence, reviewEvidence, operation, underwritingEvidence, evidence);
    }

    /** 记录 Billing 入账与资金检查点；只有资金成功或无需资金时才完成费用任务。 */
    public MaintenanceWorkflowTask settlePremium(
            MaintenanceBillingPostingEvidence posting,
            MaintenanceFundSettlementEvidence funds,
            MaintenanceWorkflowOperation operation) {
        requireAction(operation, MaintenanceWorkflowAction.SETTLE_PREMIUM);
        if (stepType != MaintenanceStepType.FEE_SETTLEMENT || posting == null || funds == null) {
            throw invalidTransition("当前任务不是可结算的收退费步骤");
        }
        if (premiumQuoteEvidence == null
                || premiumQuoteEvidence.status() != MaintenancePremiumQuoteStatus.QUOTED) {
            throw invalidTransition("费用任务必须先取得 Product 权威报价");
        }
        if (!funds.evidenceVersion(posting).equals(operation.evidenceVersion())
                || !funds.gateContentHash(posting).equals(operation.evidenceHash())
                || !resultCode(posting, funds).equals(operation.resultCode())
                || !funds.detailSummary().equals(operation.reason())) {
            throw new MaintenanceValidationException(
                    "RecordMaintenancePremiumSettlementCommand", "settlementEvidence",
                    "Billing 与资金证据和操作载荷不一致");
        }
        validatePostingAgainstQuote(posting);
        validateFundAgainstPosting(posting, funds);
        if (status != MaintenanceWorkflowTaskStatus.QUOTED
                && status != MaintenanceWorkflowTaskStatus.READY
                && status != MaintenanceWorkflowTaskStatus.IN_PROGRESS
                && status != MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL) {
            throw invalidTransition("当前费用步骤状态不允许记录结算结果: " + status);
        }
        if (status == MaintenanceWorkflowTaskStatus.IN_PROGRESS) {
            requireAssignee(operation);
        }

        MaintenanceWorkflowTaskStatus targetStatus;
        MaintenanceWorkflowFailure targetFailure = null;
        if (posting.status() == MaintenanceBillingPostingStatus.REVERSED) {
            targetStatus = MaintenanceWorkflowTaskStatus.FAILED;
            targetFailure = new MaintenanceWorkflowFailure(
                    "BILLING_POSTING_REVERSED", "Billing 入账已冲正，禁止进入生效步骤");
        } else if (funds.status().completed()) {
            targetStatus = MaintenanceWorkflowTaskStatus.COMPLETED;
        } else if (funds.status().failed()) {
            targetStatus = MaintenanceWorkflowTaskStatus.FAILED;
            targetFailure = new MaintenanceWorkflowFailure(
                    funds.failureCode(), funds.failureMessage());
        } else {
            targetStatus = MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL;
        }
        MaintenanceWorkflowAssignment targetAssignment =
                targetStatus == MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL ? null : assignment;
        return new MaintenanceWorkflowTask(
                taskId, itemCode, itemOrder, sequence, stepType, mode, conditionRuleCode,
                targetStatus, targetAssignment, retryCount, targetFailure, conditionEvidence,
                reviewEvidence, operation, underwritingEvidence, premiumQuoteEvidence,
                posting, funds);
    }

    /** Billing 或 Payment 不可用时形成可重试失败，不伪造外部成功证据。 */
    public MaintenanceWorkflowTask failPremiumSettlement(MaintenanceWorkflowOperation operation) {
        requireAction(operation, MaintenanceWorkflowAction.FAIL_PREMIUM_SETTLEMENT);
        if (stepType != MaintenanceStepType.FEE_SETTLEMENT
                || premiumQuoteEvidence == null
                || premiumQuoteEvidence.status() != MaintenancePremiumQuoteStatus.QUOTED) {
            throw invalidTransition("当前任务不是已报价的收退费步骤");
        }
        if (status != MaintenanceWorkflowTaskStatus.QUOTED
                && status != MaintenanceWorkflowTaskStatus.READY
                && status != MaintenanceWorkflowTaskStatus.IN_PROGRESS
                && status != MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL) {
            throw invalidTransition("当前费用步骤状态不允许记录外部失败: " + status);
        }
        if (status == MaintenanceWorkflowTaskStatus.IN_PROGRESS) {
            requireAssignee(operation);
        }
        if (operation.resultCode() == null || operation.reason() == null) {
            throw new MaintenanceValidationException(
                    "FailMaintenancePremiumSettlementCommand", "failure", "失败码和原因不能为空");
        }
        return new MaintenanceWorkflowTask(
                taskId, itemCode, itemOrder, sequence, stepType, mode, conditionRuleCode,
                MaintenanceWorkflowTaskStatus.FAILED, assignment, retryCount,
                new MaintenanceWorkflowFailure(operation.resultCode(), operation.reason()),
                conditionEvidence, reviewEvidence, operation, underwritingEvidence,
                premiumQuoteEvidence, billingPostingEvidence, fundSettlementEvidence);
    }

    /** 冻结 Policy 请求证据并等待权威回执。 */
    public MaintenanceWorkflowTask requestEffect(
            MaintenanceEffectRequestEvidence evidence,
            MaintenanceWorkflowOperation operation) {
        requireAction(operation, MaintenanceWorkflowAction.REQUEST_EFFECT);
        if (stepType != MaintenanceStepType.EFFECT || evidence == null) {
            throw invalidTransition("当前任务不是可发起的生效步骤");
        }
        requireStatus(MaintenanceWorkflowTaskStatus.READY, "发起生效");
        if (!evidence.evidenceVersion().equals(operation.evidenceVersion())
                || !evidence.requestPayloadHash().equals(operation.evidenceHash())
                || !"EFFECTING".equals(operation.resultCode())) {
            throw new MaintenanceValidationException(
                    "RequestMaintenanceEffectCommand", "effectRequestEvidence", "生效请求证据与操作载荷不一致");
        }
        return new MaintenanceWorkflowTask(
                taskId, itemCode, itemOrder, sequence, stepType, mode, conditionRuleCode,
                MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL, null, retryCount, null,
                conditionEvidence, reviewEvidence, operation, underwritingEvidence,
                premiumQuoteEvidence, billingPostingEvidence, fundSettlementEvidence,
                MaintenanceEffectEvidence.requested(evidence));
    }

    /** 记录 Policy 权威回执并完成生效任务。 */
    public MaintenanceWorkflowTask recordPolicyApplication(
            MaintenancePolicyApplicationEvidence evidence,
            MaintenanceWorkflowOperation operation) {
        requireAction(operation, MaintenanceWorkflowAction.RECORD_POLICY_APPLICATION);
        if (stepType != MaintenanceStepType.EFFECT || effectEvidence == null || evidence == null) {
            throw invalidTransition("当前生效任务没有可勾稽的请求证据");
        }
        requireStatus(MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL, "记录 Policy 回执");
        if (!evidence.evidenceVersion().equals(operation.evidenceVersion())
                || !evidence.applicationHash().equals(operation.evidenceHash())
                || !"APPLIED".equals(operation.resultCode())
                || !evidence.endorsementNo().equals(operation.reason())) {
            throw new MaintenanceValidationException(
                    "RecordMaintenancePolicyApplicationCommand", "policyApplicationEvidence",
                    "Policy 回执与操作载荷不一致");
        }
        return new MaintenanceWorkflowTask(
                taskId, itemCode, itemOrder, sequence, stepType, mode, conditionRuleCode,
                MaintenanceWorkflowTaskStatus.COMPLETED, null, retryCount, null,
                conditionEvidence, reviewEvidence, operation, underwritingEvidence,
                premiumQuoteEvidence, billingPostingEvidence, fundSettlementEvidence,
                effectEvidence.applied(evidence));
    }

    /** Policy 调用或回执校验失败时保留请求证据并进入可重试失败。 */
    public MaintenanceWorkflowTask failEffect(MaintenanceWorkflowOperation operation) {
        requireAction(operation, MaintenanceWorkflowAction.FAIL_EFFECT);
        if (stepType != MaintenanceStepType.EFFECT || effectEvidence == null) {
            throw invalidTransition("当前生效任务尚未发起 Policy 请求");
        }
        requireStatus(MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL, "记录生效失败");
        if (operation.resultCode() == null || operation.reason() == null) {
            throw new MaintenanceValidationException(
                    "FailMaintenanceEffectCommand", "failure", "失败码和原因不能为空");
        }
        return new MaintenanceWorkflowTask(
                taskId, itemCode, itemOrder, sequence, stepType, mode, conditionRuleCode,
                MaintenanceWorkflowTaskStatus.FAILED, null, retryCount,
                new MaintenanceWorkflowFailure(operation.resultCode(), operation.reason()),
                conditionEvidence, reviewEvidence, operation, underwritingEvidence,
                premiumQuoteEvidence, billingPostingEvidence, fundSettlementEvidence,
                effectEvidence);
    }

    /** 项目撤销后将尚未形成终态的任务显式跳过，并保留全部历史证据。 */
    public MaintenanceWorkflowTask withdraw(MaintenanceWorkflowOperation operation) {
        requireAction(operation, MaintenanceWorkflowAction.WITHDRAW_ITEM);
        if (status == MaintenanceWorkflowTaskStatus.COMPLETED
                || status == MaintenanceWorkflowTaskStatus.SKIPPED) {
            throw invalidTransition("已完成或已跳过任务不能再次撤销");
        }
        if (!MaintenanceWorkflowTaskStatus.SKIPPED.getCode().equals(operation.resultCode())
                || operation.reason() == null) {
            throw new MaintenanceValidationException(
                    "MaintenanceWorkflowTask", "operation", "项目撤销任务操作必须携带跳过结论和原因");
        }
        if (effectEvidence != null && effectEvidence.isApplied()) {
            throw invalidTransition("Policy 已应用的生效任务不能撤销");
        }
        return new MaintenanceWorkflowTask(
                taskId, itemCode, itemOrder, sequence, stepType, mode, conditionRuleCode,
                MaintenanceWorkflowTaskStatus.SKIPPED, null, retryCount, failure,
                conditionEvidence, reviewEvidence, operation, underwritingEvidence,
                premiumQuoteEvidence, billingPostingEvidence, fundSettlementEvidence, effectEvidence);
    }

    /** 前置任务形成终态后激活同项目下一任务。 */
    public MaintenanceWorkflowTask activate(MaintenanceWorkflowOperation cause) {
        requireStatus(MaintenanceWorkflowTaskStatus.PENDING, "激活");
        MaintenanceWorkflowTaskStatus target = mode == MaintenanceStepMode.CONDITIONAL
                ? MaintenanceWorkflowTaskStatus.WAITING_CONDITION
                : MaintenanceWorkflowTaskStatus.READY;
        return copy(target, null, retryCount, null, conditionEvidence, cause);
    }

    private MaintenanceWorkflowTask copy(
            MaintenanceWorkflowTaskStatus targetStatus,
            MaintenanceWorkflowAssignment targetAssignment,
            int targetRetryCount,
            MaintenanceWorkflowFailure targetFailure,
            MaintenanceWorkflowConditionEvidence targetConditionEvidence,
            MaintenanceWorkflowOperation targetOperation) {
        return new MaintenanceWorkflowTask(
                taskId, itemCode, itemOrder, sequence, stepType, mode, conditionRuleCode,
                targetStatus, targetAssignment, targetRetryCount, targetFailure,
                targetConditionEvidence, reviewEvidence, targetOperation, underwritingEvidence,
                premiumQuoteEvidence, billingPostingEvidence, fundSettlementEvidence, effectEvidence);
    }

    private void validatePostingAgainstQuote(MaintenanceBillingPostingEvidence posting) {
        if (!premiumQuoteEvidence.quoteId().equals(posting.adjustmentId())
                || !premiumQuoteEvidence.resultHash().equals(posting.resultHash())
                || premiumQuoteEvidence.direction() != posting.direction()
                || premiumQuoteEvidence.amount().compareTo(posting.amount()) != 0
                || !premiumQuoteEvidence.currency().equals(posting.currency())) {
            throw new MaintenanceValidationException(
                    "RecordMaintenancePremiumSettlementCommand", "billingPostingEvidence",
                    "Billing 入账与 Product 报价不一致");
        }
    }

    private void validateFundAgainstPosting(
            MaintenanceBillingPostingEvidence posting,
            MaintenanceFundSettlementEvidence funds) {
        MaintenanceFundSettlementType expectedType = switch (posting.direction()) {
            case NONE -> MaintenanceFundSettlementType.NOT_REQUIRED;
            case DEBIT -> MaintenanceFundSettlementType.COLLECTION;
            case CREDIT -> MaintenanceFundSettlementType.REFUND;
        };
        if (funds.type() != expectedType
                || !posting.postingId().equals(funds.sourcePostingId())
                || posting.amount().compareTo(funds.amount()) != 0
                || !posting.currency().equals(funds.currency())) {
            throw new MaintenanceValidationException(
                    "RecordMaintenancePremiumSettlementCommand", "fundSettlementEvidence",
                    "资金结果与 Billing 入账不一致");
        }
    }

    private String resultCode(
            MaintenanceBillingPostingEvidence posting,
            MaintenanceFundSettlementEvidence funds) {
        return posting.status() == MaintenanceBillingPostingStatus.REVERSED
                ? MaintenanceBillingPostingStatus.REVERSED.getCode()
                : funds.status().getCode();
    }

    private void requireAction(
            MaintenanceWorkflowOperation operation,
            MaintenanceWorkflowAction expected) {
        if (operation == null || operation.action() != expected) {
            throw new MaintenanceValidationException(
                    "MaintenanceWorkflowTask", "operation", "任务操作类型不匹配");
        }
    }

    private void requireStatus(MaintenanceWorkflowTaskStatus expected, String action) {
        if (status != expected) {
            throw invalidTransition("当前状态不允许" + action + ": " + status);
        }
    }

    private void requireAssignee(MaintenanceWorkflowOperation operation) {
        if (assignment == null || !assignment.assignee().equals(operation.operatedBy())) {
            throw invalidTransition("只有当前领取人可以操作任务");
        }
    }

    private MaintenanceValidationException invalidTransition(String message) {
        return new MaintenanceConflictException(
                "MaintenanceWorkflowTask", "status", message);
    }

    private static String requireText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new MaintenanceValidationException(
                    "MaintenanceWorkflowTask", fieldName, "字段不能为空");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
