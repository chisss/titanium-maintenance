package com.titanium.maintenance.application.orchestration;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import com.titanium.maintenance.application.model.premium.MaintenancePremiumSettlementInput;
import com.titanium.maintenance.application.model.premium.MaintenancePremiumSettlementResult;
import com.titanium.maintenance.application.model.settlement.MaintenanceReversalSettlementInput;
import com.titanium.maintenance.application.model.settlement.MaintenanceSurrenderSettlementInput;
import com.titanium.maintenance.application.model.settlement.MaintenanceSurrenderSettlementResult;
import com.titanium.maintenance.command.RecordMaintenanceFinancialSettlementCommand;
import com.titanium.maintenance.command.RecordMaintenancePremiumAdjustmentCommand;
import com.titanium.maintenance.command.RecordMaintenancePremiumPostingCommand;
import com.titanium.maintenance.command.RecordMaintenanceSurrenderValueCommand;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.MaintenancePremiumSettlementStatus;
import com.titanium.maintenance.common.enums.PaymentRefundStatus;
import com.titanium.maintenance.common.exception.MaintenanceNotFoundException;
import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.common.exception.MaintenanceSettlementConflictException;
import com.titanium.maintenance.port.billing.BillingPremiumLifecyclePort;
import com.titanium.maintenance.port.policy.PolicyFinancialPort;
import com.titanium.maintenance.port.policy.PolicyServicePort;
import com.titanium.maintenance.port.product.ProductPremiumLifecyclePort;
import com.titanium.maintenance.port.product.ProductSurrenderValuePort;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.metadata.enums.maintenance.MaintenanceType;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.DomainException;

import lombok.RequiredArgsConstructor;

/**
 * 保全生命周期费用应用编排器。
 *
 * <p>按“Product 替代计算 -> Product 差额检查点 -> Billing 余额检查点 -> 资金结算检查点”推进。每个远程步骤均使用
 * 由 maintenanceId 派生的稳定幂等键，跨服务失败后可从已保存检查点继续。</p>
 */
@Service
@RequiredArgsConstructor
public class MaintenancePremiumSettlementOrchestrator {

    private static final String PURPOSE_MAINTENANCE = "MAINTENANCE";
    private static final String LIFECYCLE_ENDORSEMENT = "ENDORSEMENT";
    private static final String LIFECYCLE_SURRENDER = "SURRENDER";

    private final CommandGateway commandGateway;
    private final MaintenanceViewRepository maintenanceViewRepository;
    private final PolicyServicePort policyServicePort;
    private final PolicyFinancialPort policyFinancialPort;
    private final ProductPremiumLifecyclePort productPremiumLifecyclePort;
    private final ProductSurrenderValuePort productSurrenderValuePort;
    private final BillingPremiumLifecyclePort billingPremiumLifecyclePort;

    public MaintenancePremiumSettlementResult settle(
            String maintenanceId, String tenantId, MaintenancePremiumSettlementInput input) {
        validateInput(input);
        MaintenanceView view = maintenanceViewRepository.findByMaintenanceIdAndTenantId(maintenanceId, tenantId)
                .orElseThrow(MaintenanceNotFoundException::new);
        if (view.getMaintenanceType() == MaintenanceType.POLICY_TERMINATION) {
            throw new MaintenanceSettlementConflictException("退保案件必须使用现金价值结算入口",
                    MaintenanceErrorCode.MAINTENANCE_SURRENDER_ENDPOINT_REQUIRED);
        }
        MaintenancePremiumSettlementStatus currentStatus = view.getPremiumSettlementStatus() == null
                ? MaintenancePremiumSettlementStatus.NOT_STARTED
                : view.getPremiumSettlementStatus();
        if (isTerminal(currentStatus)) {
            requireSameOriginalCalculation(view, input.originalCalculationId());
            return fromView(view);
        }
        if (currentStatus == MaintenancePremiumSettlementStatus.NOT_STARTED) {
            requirePolicyProduct(view, tenantId, input.productId());
        }

        ProductPremiumLifecyclePort.AdjustmentFact adjustment = currentStatus
                == MaintenancePremiumSettlementStatus.NOT_STARTED
                        ? createAndRecordAdjustment(view, tenantId, input)
                        : adjustmentFromView(view, input.originalCalculationId());

        return postAndSettle(view, tenantId, input.updatedBy(), adjustment);
    }

    /** 退保专用入口：由 Product 决定现金价值，调用方不提交金额或替代投保参数。 */
    public MaintenanceSurrenderSettlementResult settleSurrender(
            String maintenanceId, String tenantId, MaintenanceSurrenderSettlementInput input) {
        validateSurrenderInput(input);
        MaintenanceView view = maintenanceViewRepository.findByMaintenanceIdAndTenantId(maintenanceId, tenantId)
                .orElseThrow(MaintenanceNotFoundException::new);
        if (view.getMaintenanceType() != MaintenanceType.POLICY_TERMINATION) {
            throw new MaintenanceSettlementConflictException("只有保单终止案件可以执行退保价值结算",
                    MaintenanceErrorCode.MAINTENANCE_SURRENDER_TYPE_INVALID);
        }
        MaintenancePremiumSettlementStatus currentStatus = view.getPremiumSettlementStatus() == null
                ? MaintenancePremiumSettlementStatus.NOT_STARTED
                : view.getPremiumSettlementStatus();
        if (isTerminal(currentStatus)) {
            requireSameOriginalCalculation(view, input.originalCalculationId());
            validateSurrenderCheckpoint(view);
            return fromSurrenderView(view);
        }

        ProductSurrenderValuePort.SurrenderFact surrenderFact;
        ProductPremiumLifecyclePort.AdjustmentFact adjustment;
        if (currentStatus == MaintenancePremiumSettlementStatus.NOT_STARTED) {
            PolicyFinancialPort.PolicyFinancialData policy = requireSurrenderFinancialData(view, tenantId);
            surrenderFact = productSurrenderValuePort.calculate(new ProductSurrenderValuePort.SurrenderRequest(
                    tenantId, maintenanceId, view.getPolicyId(), policy.issuanceBizNo(), input.originalCalculationId(),
                    policy.effectiveDate(), input.surrenderDate(), input.policyYear(), input.businessTime(),
                    input.reason(), policy.annualPremium(), policy.coverageEndDate(), policy.premiumPaidUp(),
                    policy.loanOutstandingPrincipal(), policy.loanOutstandingInterest(),
                    policy.arrearsAmount(), policy.dividendBalance(), policy.accountValue()));
            validateSurrenderFact(view, input, surrenderFact);
            adjustment = toAdjustmentFact(surrenderFact);
            recordAdjustment(view, input.updatedBy(), adjustment);
            recordSurrenderValue(view, input.updatedBy(), surrenderFact);
        } else {
            requireSameOriginalCalculation(view, input.originalCalculationId());
            validateSurrenderCheckpoint(view);
            adjustment = adjustmentFromView(view, input.originalCalculationId());
            surrenderFact = surrenderFromView(view);
        }

        MaintenancePremiumSettlementResult settlement = postAndSettle(
                view, tenantId, input.updatedBy(), adjustment);
        return toSurrenderResult(settlement, surrenderFact);
    }

    /** 将既有生命周期差额反向登记；若原退费已成功，反向事实表现为客户追加应收。 */
    public MaintenancePremiumSettlementResult settleReversal(
            String maintenanceId, String tenantId, MaintenanceReversalSettlementInput input) {
        validateReversalInput(input);
        MaintenanceView view = maintenanceViewRepository.findByMaintenanceIdAndTenantId(maintenanceId, tenantId)
                .orElseThrow(MaintenanceNotFoundException::new);
        if (view.getMaintenanceType() != MaintenanceType.POLICY_REVERSAL) {
            throw new MaintenanceSettlementConflictException("只有保单费用冲正案件可以执行冲正结算",
                    MaintenanceErrorCode.MAINTENANCE_REVERSAL_TYPE_INVALID);
        }
        MaintenancePremiumSettlementStatus currentStatus = view.getPremiumSettlementStatus() == null
                ? MaintenancePremiumSettlementStatus.NOT_STARTED : view.getPremiumSettlementStatus();
        if (isTerminal(currentStatus)) {
            return fromView(view);
        }
        ProductPremiumLifecyclePort.AdjustmentFact adjustment;
        if (currentStatus == MaintenancePremiumSettlementStatus.NOT_STARTED) {
            adjustment = productPremiumLifecyclePort.createReversal(new ProductPremiumLifecyclePort.ReversalRequest(
                    tenantId, maintenanceId + ":reversal", input.sourceAdjustmentId(), input.businessTime(),
                    input.reason()));
            validateReversalFact(adjustment);
            recordAdjustment(view, input.updatedBy(), adjustment);
        } else {
            adjustment = adjustmentFromView(view, null);
        }
        return postAndSettle(view, tenantId, input.updatedBy(), adjustment);
    }

    private MaintenancePremiumSettlementResult postAndSettle(
            MaintenanceView view,
            String tenantId,
            String updatedBy,
            ProductPremiumLifecyclePort.AdjustmentFact adjustment) {
        if (adjustment.direction() == MaintenanceBalanceDirection.NONE) {
            return fromAdjustment(
                    view.getMaintenanceId(), adjustment, null, MaintenancePremiumSettlementStatus.NOT_REQUIRED);
        }

        BillingPremiumLifecyclePort.PostingFact posting = billingPremiumLifecyclePort.post(
                new BillingPremiumLifecyclePort.PostingRequest(
                        tenantId, adjustment.adjustmentId(), adjustment.resultHash(), view.getPolicyId(),
                        view.getCustomerId(), updatedBy));
        validatePosting(adjustment, posting);
        commandGateway.sendAndWait(new RecordMaintenancePremiumPostingCommand(
                MaintenanceId.of(view.getMaintenanceId()), adjustment.adjustmentId(), adjustment.resultHash(),
                posting.postingId(), posting.status(), updatedBy, tenantId));
        validateFinancialFacts(adjustment, posting);
        MaintenancePremiumSettlementStatus targetStatus = financialSettlementStatus(
                adjustment.direction(), posting.refundStatus());
        commandGateway.sendAndWait(new RecordMaintenanceFinancialSettlementCommand(
                MaintenanceId.of(view.getMaintenanceId()), posting.postingId(), posting.refundInstructionId(),
                posting.refundOrderId(), posting.refundStatus(), posting.commissionAdjustmentCount(), updatedBy, tenantId));
        return fromAdjustment(view.getMaintenanceId(), adjustment, posting, targetStatus);
    }

    private ProductPremiumLifecyclePort.AdjustmentFact createAndRecordAdjustment(
            MaintenanceView view, String tenantId, MaintenancePremiumSettlementInput input) {
        String maintenanceId = view.getMaintenanceId();
        ProductPremiumLifecyclePort.CalculationFact replacement = productPremiumLifecyclePort.calculateReplacement(
                new ProductPremiumLifecyclePort.CalculationRequest(
                        tenantId, input.productId(), maintenanceId + ":replacement", view.getPolicyId(),
                        input.productVersion(), input.businessTime(), input.currency(), input.sumInsured(),
                        input.age(), input.gender(), input.paymentTermYears(), input.coverageTermYears(),
                        input.paymentPeriods(), input.requestSnapshot(), toPortAdjustments(input),
                        input.channelId(), input.policyYear()));
        validateReplacement(view, input, replacement);

        ProductPremiumLifecyclePort.AdjustmentFact adjustment = productPremiumLifecyclePort.createAdjustment(
                new ProductPremiumLifecyclePort.AdjustmentRequest(
                        tenantId, maintenanceId + ":adjustment", view.getPolicyId(), lifecycleType(view),
                        input.originalCalculationId(), replacement.calculationId(), input.businessTime(),
                        input.reason()));
        validateAdjustment(input, replacement, adjustment);
        recordAdjustment(view, input.updatedBy(), adjustment);
        return adjustment;
    }

    private void recordAdjustment(
            MaintenanceView view,
            String updatedBy,
            ProductPremiumLifecyclePort.AdjustmentFact adjustment) {
        commandGateway.sendAndWait(new RecordMaintenancePremiumAdjustmentCommand(
                MaintenanceId.of(view.getMaintenanceId()), adjustment.originalCalculationId(),
                adjustment.replacementCalculationId(), adjustment.adjustmentId(), adjustment.resultHash(),
                adjustment.direction(), adjustment.customerAmount(), adjustment.currency(), updatedBy, view.getTenantId()));
    }

    private void recordSurrenderValue(
            MaintenanceView view,
            String updatedBy,
            ProductSurrenderValuePort.SurrenderFact fact) {
        commandGateway.sendAndWait(new RecordMaintenanceSurrenderValueCommand(
                MaintenanceId.of(view.getMaintenanceId()), fact.adjustmentId(), fact.policyCode(),
                fact.policyVersion(), fact.policyContentHash(), fact.policyYear(), fact.coolingOffDays(),
                fact.refundType(), fact.withinCoolingOff(), fact.cashValueRate(), fact.retainedCustomerAmount(),
                fact.internalCostRetentionRate(), updatedBy, view.getTenantId(), fact.factors()));
    }

    private ProductPremiumLifecyclePort.AdjustmentFact toAdjustmentFact(
            ProductSurrenderValuePort.SurrenderFact fact) {
        return new ProductPremiumLifecyclePort.AdjustmentFact(
                fact.adjustmentId(), fact.surrenderRequestId() + ":adjustment", fact.originalCalculationId(),
                fact.replacementCalculationId(), fact.adjustmentResultHash(), fact.direction(), fact.amount(),
                fact.currency());
    }

    /**
     * 取退保所需的保单财务事实（g04-02 取数契约，写侧强一致）。
     * <p>
     * 🔴 <b>单次取数</b>：退保所需的全部保单事实——出单流水号、生效日、年保费、保障止期、缴费是否
     * 期满、<b>贷款未偿本息</b>——必须来自<b>同一次</b>快照。分两次取数会读到两个保单版本，退费金额
     * 与它引用的出单流水号可能对不上（g04-05 先例）。
     * </p>
     * <p>
     * 🔴 只对<b>本方法真正消费</b>的两项失败关闭（出单流水号 / 生效日）。快照里的现金价值、欠费、
     * 红利账户价值等要素是给贷款放款与减额缴清消费的，退保不读它们，故不因它们不可得而拒绝退保。
     * </p>
     * <p>
     * 🔴 <b>贷款未偿本息自 g05-03 起由退费消费</b>（先行抵扣），但**不在此失败关闭**：保单域响应
     * 契约已强制两项非空（无未结贷款为 0，与「取不到」严格区分），本域只搬运；能否抵扣由产品域在
     * <b>使用点</b>判定，缺任一即 {@code SURRENDER_LOAN_FACTS_MISSING} 拒算。本域若自行预判，
     * 就是第二份「贷款事实是否够用」的判据。
     * </p>
     */
    private PolicyFinancialPort.PolicyFinancialData requireSurrenderFinancialData(
            MaintenanceView view, String tenantId) {
        PolicyFinancialPort.PolicyFinancialData snapshot = policyFinancialPort.getFinancialSnapshot(
                view.getPolicyId(), tenantId);
        if (snapshot == null || isBlank(snapshot.issuanceBizNo()) || snapshot.effectiveDate() == null) {
            throw invalidRemoteFact("Policy 未返回可用的退保财务日期快照");
        }
        return snapshot;
    }

    private void validateSurrenderFact(
            MaintenanceView view,
            MaintenanceSurrenderSettlementInput input,
            ProductSurrenderValuePort.SurrenderFact fact) {
        boolean zeroRefund = fact != null && fact.refundAmount() != null && fact.refundAmount().signum() == 0;
        boolean invalidDirection = fact == null || fact.direction() == null
                || (zeroRefund && fact.direction() != MaintenanceBalanceDirection.NONE)
                || (!zeroRefund && fact.direction() != MaintenanceBalanceDirection.CREDIT);
        if (fact == null || !Objects.equals(fact.surrenderRequestId(), view.getMaintenanceId())
                || !Objects.equals(fact.originalCalculationId(), input.originalCalculationId())
                || isBlank(fact.replacementCalculationId()) || isBlank(fact.adjustmentId())
                || isBlank(fact.adjustmentResultHash()) || fact.adjustmentResultHash().length() != 64
                || isBlank(fact.policyCode()) || isBlank(fact.policyVersion())
                || isBlank(fact.policyContentHash()) || fact.policyContentHash().length() != 64
                || !Objects.equals(fact.policyYear(), input.policyYear())
                || fact.coolingOffDays() == null || fact.coolingOffDays() < 0
                || isBlank(fact.refundType()) || fact.withinCoolingOff() == null
                || invalidRate(fact.cashValueRate()) || invalidRate(fact.internalCostRetentionRate())
                || fact.refundAmount() == null || fact.refundAmount().signum() < 0
                || fact.retainedCustomerAmount() == null || fact.retainedCustomerAmount().signum() < 0
                || fact.amount() == null || fact.amount().compareTo(fact.refundAmount()) != 0
                || invalidDirection || isBlank(fact.currency())) {
            throw invalidRemoteFact("Product 退保价值事实不完整或无法勾稽");
        }
    }

    private void validateSurrenderCheckpoint(MaintenanceView view) {
        if (isBlank(view.getSurrenderPolicyCode()) || isBlank(view.getSurrenderPolicyVersion())
                || isBlank(view.getSurrenderPolicyContentHash())
                || view.getSurrenderPolicyContentHash().length() != 64
                || view.getSurrenderPolicyYear() == null || view.getSurrenderPolicyYear() < 1
                || view.getCoolingOffDays() == null || view.getCoolingOffDays() < 0
                || isBlank(view.getSurrenderRefundType()) || view.getWithinCoolingOff() == null
                || invalidRate(view.getCashValueRate()) || invalidRate(view.getInternalCostRetentionRate())
                || view.getRetainedCustomerAmount() == null || view.getRetainedCustomerAmount().signum() < 0) {
            throw new MaintenanceSettlementConflictException("退保价值检查点不完整",
                    MaintenanceErrorCode.MAINTENANCE_SURRENDER_CHECKPOINT_INVALID);
        }
    }

    private ProductSurrenderValuePort.SurrenderFact surrenderFromView(MaintenanceView view) {
        return new ProductSurrenderValuePort.SurrenderFact(
                view.getMaintenanceId(), view.getSurrenderPolicyCode(), view.getSurrenderPolicyVersion(),
                view.getSurrenderPolicyContentHash(), view.getSurrenderPolicyYear(), view.getCoolingOffDays(),
                view.getSurrenderRefundType(), view.getWithinCoolingOff(), view.getCashValueRate(),
                view.getBalanceAmount(), view.getRetainedCustomerAmount(), view.getInternalCostRetentionRate(),
                view.getOriginalCalculationId(), view.getReplacementCalculationId(), view.getPremiumAdjustmentId(),
                view.getPremiumAdjustmentResultHash(), view.getBalanceDirection(), view.getBalanceAmount(),
                view.getBalanceCurrency());
    }

    private List<ProductPremiumLifecyclePort.UnderwritingAdjustment> toPortAdjustments(
            MaintenancePremiumSettlementInput input) {
        if (input.underwritingAdjustments() == null) {
            return List.of();
        }
        return input.underwritingAdjustments().stream()
                .map(item -> new ProductPremiumLifecyclePort.UnderwritingAdjustment(
                        item.adjustmentCode(), item.type(), item.value(), item.reason(), item.ruleVersion()))
                .toList();
    }

    private ProductPremiumLifecyclePort.AdjustmentFact adjustmentFromView(
            MaintenanceView view, String originalCalculationId) {
        if (originalCalculationId != null) {
            requireSameOriginalCalculation(view, originalCalculationId);
        }
        validateAdjustmentCheckpoint(view);
        return new ProductPremiumLifecyclePort.AdjustmentFact(
                view.getPremiumAdjustmentId(), view.getMaintenanceId() + ":adjustment",
                view.getOriginalCalculationId(), view.getReplacementCalculationId(),
                view.getPremiumAdjustmentResultHash(), view.getBalanceDirection(), view.getBalanceAmount(),
                view.getBalanceCurrency());
    }

    private void validateAdjustmentCheckpoint(MaintenanceView view) {
        boolean invalidAmount = view.getBalanceAmount() == null
                || view.getBalanceAmount().signum() < 0
                || (view.getBalanceDirection() == MaintenanceBalanceDirection.NONE
                        && view.getBalanceAmount().signum() != 0)
                || (view.getBalanceDirection() != null
                        && view.getBalanceDirection() != MaintenanceBalanceDirection.NONE
                        && view.getBalanceAmount().signum() <= 0);
        if (isBlank(view.getReplacementCalculationId()) || isBlank(view.getPremiumAdjustmentId())
                || isBlank(view.getPremiumAdjustmentResultHash()) || view.getBalanceDirection() == null
                || invalidAmount || isBlank(view.getBalanceCurrency())) {
            throw new MaintenanceSettlementConflictException("保全费用差额检查点不完整",
                    MaintenanceErrorCode.MAINTENANCE_PREMIUM_CHECKPOINT_INVALID);
        }
    }

    private void validateReplacement(
            MaintenanceView view,
            MaintenancePremiumSettlementInput input,
            ProductPremiumLifecyclePort.CalculationFact replacement) {
        if (replacement == null
                || !Objects.equals(replacement.calculationRequestId(), view.getMaintenanceId() + ":replacement")
                || !Objects.equals(replacement.bizNo(), view.getPolicyId())
                || !Objects.equals(replacement.productId(), input.productId())
                || !Objects.equals(replacement.productVersion(), input.productVersion())
                || !Objects.equals(replacement.currency(), input.currency())
                || !PURPOSE_MAINTENANCE.equals(replacement.purpose())
                || isBlank(replacement.calculationId())
                || isBlank(replacement.resultHash())) {
            throw invalidRemoteFact("Product 替代计算回显与保全请求不一致");
        }
    }

    private void validateAdjustment(
            MaintenancePremiumSettlementInput input,
            ProductPremiumLifecyclePort.CalculationFact replacement,
            ProductPremiumLifecyclePort.AdjustmentFact adjustment) {
        if (adjustment == null
                || !Objects.equals(adjustment.originalCalculationId(), input.originalCalculationId())
                || !Objects.equals(adjustment.replacementCalculationId(), replacement.calculationId())
                || !Objects.equals(adjustment.currency(), input.currency())
                || adjustment.direction() == null
                || adjustment.customerAmount() == null
                || adjustment.customerAmount().signum() < 0
                || isBlank(adjustment.adjustmentId())
                || isBlank(adjustment.resultHash())) {
            throw invalidRemoteFact("Product 生命周期差额事实不完整或无法勾稽");
        }
    }

    private void validatePosting(
            ProductPremiumLifecyclePort.AdjustmentFact adjustment,
            BillingPremiumLifecyclePort.PostingFact posting) {
        if (posting == null
                || !Objects.equals(posting.adjustmentId(), adjustment.adjustmentId())
                || !Objects.equals(posting.resultHash(), adjustment.resultHash())
                || posting.direction() != adjustment.direction()
                || !sameAmount(posting.amount(), adjustment.customerAmount())
                || !Objects.equals(posting.currency(), adjustment.currency())
                || !"POSTED".equals(posting.status())
                || isBlank(posting.postingId())) {
            throw invalidRemoteFact("Billing 生命周期入账事实与 Product 差额不一致");
        }
    }

    private void validateFinancialFacts(
            ProductPremiumLifecyclePort.AdjustmentFact adjustment,
            BillingPremiumLifecyclePort.PostingFact posting) {
        boolean hasInstruction = !isBlank(posting.refundInstructionId());
        boolean hasOrder = !isBlank(posting.refundOrderId());
        // 🔴 追回金额与佣金条数同规：缺失或为负即失败关闭，绝不以 0 兜底——0 是「无追回」这一业务
        // 事实，兜底会把「billing 没告诉我们」伪装成「不需要追回」，直接少收一笔钱。
        if (posting.commissionAdjustmentCount() == null || posting.commissionAdjustmentCount() < 0
                || posting.commissionClawbackAmount() == null || posting.commissionClawbackAmount().signum() < 0
                || (hasOrder && !hasInstruction)) {
            throw invalidRemoteFact("Billing 资金结算事实不完整");
        }
        if (adjustment.direction() == MaintenanceBalanceDirection.CREDIT && isBlank(posting.refundStatus())) {
            throw invalidRemoteFact("Billing 退费资金事实缺少退款状态");
        }
        if (adjustment.direction() == MaintenanceBalanceDirection.DEBIT
                && (hasInstruction || hasOrder || !"NOT_REQUIRED".equals(posting.refundStatus()))) {
            throw invalidRemoteFact("Billing 追加应收事实不应包含退款信息");
        }
    }

    private void validateInput(MaintenancePremiumSettlementInput input) {
        if (input == null || isBlank(input.originalCalculationId()) || isBlank(input.productId())
                || isBlank(input.productVersion()) || input.businessTime() == null || isBlank(input.currency())
                || input.sumInsured() == null || input.sumInsured().signum() <= 0 || input.age() == null
                || isBlank(input.gender()) || input.paymentTermYears() == null || input.paymentTermYears() < 1
                || input.coverageTermYears() == null || input.coverageTermYears() < 1
                || input.paymentPeriods() == null || input.paymentPeriods() < 1
                || isBlank(input.reason()) || isBlank(input.updatedBy())) {
            throw new DomainException(MaintenanceErrorCode.MAINTENANCE_PREMIUM_INPUT_INVALID, "保全替代计算参数不完整");
        }
    }

    private void validateSurrenderInput(MaintenanceSurrenderSettlementInput input) {
        if (input == null || isBlank(input.originalCalculationId()) || input.surrenderDate() == null
                || input.policyYear() == null || input.policyYear() < 1 || input.businessTime() == null
                || isBlank(input.reason()) || isBlank(input.updatedBy())) {
            throw new DomainException(MaintenanceErrorCode.MAINTENANCE_SURRENDER_INPUT_INVALID, "退保价值结算参数不完整");
        }
    }

    private void validateReversalInput(MaintenanceReversalSettlementInput input) {
        if (input == null || isBlank(input.sourceAdjustmentId()) || input.businessTime() == null
                || isBlank(input.reason()) || isBlank(input.updatedBy())) {
            throw new DomainException(MaintenanceErrorCode.MAINTENANCE_REVERSAL_INPUT_INVALID, "保全冲正参数不完整");
        }
    }

    private void validateReversalFact(ProductPremiumLifecyclePort.AdjustmentFact fact) {
        if (fact == null || isBlank(fact.adjustmentId()) || isBlank(fact.adjustmentRequestId())
                || isBlank(fact.originalCalculationId()) || isBlank(fact.replacementCalculationId())
                || isBlank(fact.resultHash()) || fact.direction() == null || fact.customerAmount() == null
                || fact.customerAmount().signum() <= 0 || isBlank(fact.currency())) {
            throw invalidRemoteFact("Product 冲正差额事实不完整");
        }
    }

    private void requireSameOriginalCalculation(MaintenanceView view, String originalCalculationId) {
        if (!Objects.equals(view.getOriginalCalculationId(), originalCalculationId)) {
            throw new MaintenanceSettlementConflictException("保全案件已绑定其他原确认计算",
                    MaintenanceErrorCode.MAINTENANCE_PREMIUM_SETTLEMENT_CONFLICT);
        }
    }

    private void requirePolicyProduct(MaintenanceView view, String tenantId, String requestedProductId) {
        String policyProductId = policyServicePort.getPolicyProductId(view.getPolicyId(), tenantId);
        if (isBlank(policyProductId)) {
            throw invalidRemoteFact("Policy 未返回可用的保单产品事实");
        }
        if (!Objects.equals(policyProductId, requestedProductId)) {
            throw new MaintenanceSettlementConflictException("保全计价产品与保单产品不一致",
                    MaintenanceErrorCode.MAINTENANCE_PREMIUM_PRODUCT_MISMATCH);
        }
    }

    private String lifecycleType(MaintenanceView view) {
        return view.getMaintenanceType() == MaintenanceType.POLICY_TERMINATION
                ? LIFECYCLE_SURRENDER
                : LIFECYCLE_ENDORSEMENT;
    }

    private MaintenancePremiumSettlementResult fromView(MaintenanceView view) {
        return new MaintenancePremiumSettlementResult(
                view.getMaintenanceId(), view.getPremiumSettlementStatus().name(), view.getOriginalCalculationId(),
                view.getReplacementCalculationId(), view.getPremiumAdjustmentId(),
                view.getPremiumAdjustmentResultHash(), view.getBillingPostingId(),
                view.getBillingPostingId() == null ? null : "POSTED",
                view.getBalanceDirection() == null ? null : view.getBalanceDirection().name(),
                view.getBalanceAmount(), view.getBalanceCurrency(), view.getRefundInstructionId(),
                view.getRefundOrderId(), view.getRefundStatus(), view.getCommissionAdjustmentCount(),
                // 追回金额**不落库**（本任务不新建链路），读回路径无此事实 ⇒ 如实返回 null，
                // 不得以 0 兜底（0 是「无追回」的业务事实）。历史口径请查 billing 的佣金调整流水。
                null);
    }

    private MaintenancePremiumSettlementResult fromAdjustment(
            String maintenanceId,
            ProductPremiumLifecyclePort.AdjustmentFact adjustment,
            BillingPremiumLifecyclePort.PostingFact posting,
            MaintenancePremiumSettlementStatus status) {
        return new MaintenancePremiumSettlementResult(
                maintenanceId, status.name(), adjustment.originalCalculationId(),
                adjustment.replacementCalculationId(), adjustment.adjustmentId(), adjustment.resultHash(),
                posting == null ? null : posting.postingId(), posting == null ? null : posting.status(),
                adjustment.direction().name(), adjustment.customerAmount(), adjustment.currency(),
                posting == null ? null : posting.refundInstructionId(),
                posting == null ? null : posting.refundOrderId(), posting == null ? null : posting.refundStatus(),
                posting == null ? 0 : posting.commissionAdjustmentCount(),
                posting == null ? BigDecimal.ZERO : posting.commissionClawbackAmount());
    }

    private MaintenanceSurrenderSettlementResult fromSurrenderView(MaintenanceView view) {
        return new MaintenanceSurrenderSettlementResult(
                fromView(view), view.getSurrenderPolicyCode(), view.getSurrenderPolicyVersion(),
                view.getSurrenderPolicyContentHash(), view.getSurrenderPolicyYear(), view.getCoolingOffDays(),
                view.getSurrenderRefundType(), view.getWithinCoolingOff(), view.getCashValueRate(),
                view.getRetainedCustomerAmount(), view.getInternalCostRetentionRate());
    }

    private MaintenanceSurrenderSettlementResult toSurrenderResult(
            MaintenancePremiumSettlementResult settlement,
            ProductSurrenderValuePort.SurrenderFact fact) {
        return new MaintenanceSurrenderSettlementResult(
                settlement, fact.policyCode(), fact.policyVersion(), fact.policyContentHash(), fact.policyYear(),
                fact.coolingOffDays(), fact.refundType(), fact.withinCoolingOff(), fact.cashValueRate(),
                fact.retainedCustomerAmount(), fact.internalCostRetentionRate());
    }

    private MaintenancePremiumSettlementStatus financialSettlementStatus(
            MaintenanceBalanceDirection direction, String refundStatus) {
        if (direction == MaintenanceBalanceDirection.DEBIT) {
            return MaintenancePremiumSettlementStatus.POSTED;
        }
        PaymentRefundStatus status = PaymentRefundStatus.fromCode(refundStatus);
        if (status == PaymentRefundStatus.SUCCEEDED) {
            return MaintenancePremiumSettlementStatus.SETTLED;
        }
        if (status == PaymentRefundStatus.FAILED || status == PaymentRefundStatus.CANCELLED) {
            return MaintenancePremiumSettlementStatus.SETTLEMENT_FAILED;
        }
        return MaintenancePremiumSettlementStatus.SETTLEMENT_PENDING;
    }

    private boolean isTerminal(MaintenancePremiumSettlementStatus status) {
        return status == MaintenancePremiumSettlementStatus.SETTLED
                || status == MaintenancePremiumSettlementStatus.NOT_REQUIRED;
    }

    private MaintenanceRemoteCallException invalidRemoteFact(String message) {
        return new MaintenanceRemoteCallException(
                message, MaintenanceErrorCode.MAINTENANCE_PREMIUM_REMOTE_FACT_INVALID);
    }

    private boolean sameAmount(BigDecimal left, BigDecimal right) {
        return left != null && right != null && left.compareTo(right) == 0;
    }

    private boolean invalidRate(BigDecimal value) {
        return value == null || value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
