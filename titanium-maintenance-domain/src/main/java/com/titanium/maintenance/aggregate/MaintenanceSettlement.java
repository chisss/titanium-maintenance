package com.titanium.maintenance.aggregate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateLifecycle;

import com.titanium.maintenance.command.CalculateMaintenancePremiumCommand;
import com.titanium.maintenance.command.RecordMaintenanceFinancialSettlementCommand;
import com.titanium.maintenance.command.RecordMaintenancePremiumAdjustmentCommand;
import com.titanium.maintenance.command.RecordMaintenancePremiumPostingCommand;
import com.titanium.maintenance.command.RecordMaintenanceSurrenderValueCommand;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.MaintenancePremiumSettlementStatus;
import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.common.enums.PaymentRefundStatus;
import com.titanium.maintenance.common.exception.MaintenanceNotFoundException;
import com.titanium.maintenance.common.exception.MaintenanceStatusException;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.event.MaintenanceFinancialSettlementRecordedEvent;
import com.titanium.maintenance.event.MaintenancePremiumAdjustmentRecordedEvent;
import com.titanium.maintenance.event.MaintenancePremiumCalculatedEvent;
import com.titanium.maintenance.event.MaintenancePremiumPostingRecordedEvent;
import com.titanium.maintenance.event.MaintenanceSurrenderValueRecordedEvent;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.MaintenanceSettlementRefs;
import com.titanium.maintenance.valueobject.SurrenderPolicySnapshot;
import com.titanium.metadata.enums.CurrencyEnum;
import com.titanium.metadata.enums.maintenance.MaintenanceType;
import com.titanium.metadata.valueobject.Money;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Maintenance 聚合的「费用结算（Settlement）」成员实体（DSN-01c 自聚合根下沉）。
 * <p>
 * <b>职责边界</b>：承载保费计算 → Product 差额登记 → 退保价值记录 → Billing 入账 → 资金结算
 * 五段结算事实链的状态与业务规则。事实载体均为不可变值对象（{@link MaintenanceSettlementRefs}、
 * {@link SurrenderPolicySnapshot}），本实体持有其最新版本，命令处理器校验后 apply 事件、
 * 由本实体的 {@code @EventSourcingHandler} 接管状态重建。
 * </p>
 * <p>
 * <b>成员不互引（🔴 勿破坏）</b>：本实体只持有自身主题的状态，不引用聚合内其它成员；
 * 快照序列化（Axon JacksonSerializer）会把成员内联进聚合 JSON，成员互引会导致同一对象被
 * 序列化两次、反序列化出相互独立的副本 ⇒ 状态不同步。跨主题判定（如是否必须先登记余额事实，
 * 依赖案件保全类型）留在聚合根。
 * </p>
 * <p>
 * <b>镜像上下文（由聚合根同步，🔴 新增变更点时须同批维护）</b>：{@code status} 在案件状态变更的
 * 聚合根源码五处（状态变更事件 / 执行完成 / 两处审核拒绝 / 生效状态转为已生效）经
 * {@link #markStatus(MaintenanceStatus)} 同步——结算登记可变更性判定（{@link #requireSettlementMutable}）
 * 依赖案件终态；{@code tenantId} / {@code maintenanceId} / {@code maintenanceType} 在创建时注入后不变。
 * </p>
 */
@Getter
@NoArgsConstructor
public class MaintenanceSettlement {

    /**
     * 结算金币种（🔴 DSN-07/m25-09 多币种接入点）
     * <p>
     * {@link MaintenancePremiumCalculatedEvent} 载荷不携带币种（人工试算过渡事实），其金额按本默认
     * 归一；调整事件路径（{@link MaintenancePremiumAdjustmentRecordedEvent}）取事件自带的
     * {@code currency}。多币种接入时须先扩计算事件载荷，不得在此处硬切。
     * </p>
     */
    private static final String DEFAULT_CURRENCY = CurrencyEnum.CNY.getCode();

    /** 租户ID（创建时注入，命令归属校验用） */
    private String                                tenantId;

    /** 聚合标识（创建时注入，事件发布与异常构造用） */
    private MaintenanceId                         maintenanceId;

    /** 保全类型（创建时注入，退保价值判定用；创建后不变） */
    private MaintenanceType                       maintenanceType;

    /** 案件状态镜像（聚合根在状态变更各点同步，结算可变更性判定用） */
    private MaintenanceStatus                     status;

    /** 保费总额（人工计价或差额结果；创建时归零。金额+币种一体，DSN-07） */
    private Money                                 totalAmount;

    /** 退费金额（差额结果；创建时归零。金额+币种一体，DSN-07） */
    private Money                                 refundAmount;

    /** 保费结算状态（创建时为 NOT_STARTED） */
    private MaintenancePremiumSettlementStatus    premiumSettlementStatus;

    /** 结算引用（Product 差额 / Billing 入账 / 退款三组事实的标识与哈希） */
    private MaintenanceSettlementRefs             settlementRefs;

    /** 佣金调整数量（资金结算事实携带） */
    private Integer                               commissionAdjustmentCount;

    /** 余额方向（Product 差额结果） */
    private MaintenanceBalanceDirection           balanceDirection;

    /** 余额（Product 差额结果；金额+币种一体，DSN-07 自 balanceAmount/balanceCurrency 二合一） */
    private Money                                 balance;

    /** 退保政策快照（退保价值策略证据） */
    private SurrenderPolicySnapshot               surrenderPolicy;

    MaintenanceSettlement(String tenantId, MaintenanceId maintenanceId, MaintenanceType maintenanceType,
            MaintenanceStatus status) {
        this.tenantId = tenantId;
        this.maintenanceId = maintenanceId;
        this.maintenanceType = maintenanceType;
        this.status = status;
        this.totalAmount = Money.zero(DEFAULT_CURRENCY);
        this.refundAmount = Money.zero(DEFAULT_CURRENCY);
        this.premiumSettlementStatus = MaintenancePremiumSettlementStatus.NOT_STARTED;
        this.commissionAdjustmentCount = 0;
        this.balance = Money.zero(DEFAULT_CURRENCY);
    }

    /** 案件状态变更时由聚合根同步（结算登记可变更性判定依赖案件终态）。 */
    void markStatus(MaintenanceStatus next) {
        this.status = next;
    }

    // 处理计算保费命令
    @CommandHandler
    public void handle(CalculateMaintenancePremiumCommand command) {
        requireSameTenant(command.tenantId());
        if (settlementRefs().premiumAdjustmentId() != null) {
            throw new MaintenanceStatusException(maintenanceId.id(), premiumSettlementStatus.name(),
                    "CALCULATE_MAINTENANCE_PREMIUM", "已进入结构化生命周期计价后不能再写入人工金额");
        }
        AggregateLifecycle.apply(
                new MaintenancePremiumCalculatedEvent(command.id(), command.totalAmount(), command.refundAmount(),
                        command.calculationDetails(), LocalDateTime.now(), command.updatedBy(), tenantId));
    }

    /** 记录 Product 差额检查点；同一事实重放为幂等，不允许一个案件绑定不同差额。 */
    @CommandHandler
    public void handle(RecordMaintenancePremiumAdjustmentCommand command) {
        requireSameTenant(command.tenantId());
        if (settlementRefs().premiumAdjustmentId() != null) {
            if (sameAdjustment(command)) {
                return;
            }
            throw new MaintenanceStatusException(maintenanceId.id(), premiumSettlementStatus.name(),
                    "RECORD_PREMIUM_ADJUSTMENT", "同一保全案件不能绑定不同的费用差额事实");
        }
        requireSettlementMutable("RECORD_PREMIUM_ADJUSTMENT");
        validateAdjustment(command);
        AggregateLifecycle.apply(new MaintenancePremiumAdjustmentRecordedEvent(command.id(),
                command.originalCalculationId(), command.replacementCalculationId(), command.adjustmentId(),
                command.adjustmentResultHash(), command.direction(), command.amount(), command.currency(),
                LocalDateTime.now(), command.updatedBy(), tenantId));
    }

    /** 记录退保价值策略证据；只允许保单终止案件绑定已记录的 Product 差额。 */
    @CommandHandler
    public void handle(RecordMaintenanceSurrenderValueCommand command) {
        requireSameTenant(command.tenantId());
        if (surrenderPolicy != null) {
            if (sameSurrenderValue(command)) {
                return;
            }
            throw new MaintenanceStatusException(maintenanceId.id(), premiumSettlementStatus.name(),
                    "RECORD_SURRENDER_VALUE", "同一退保案件不能绑定不同的现金价值策略事实");
        }
        if (maintenanceType != MaintenanceType.POLICY_TERMINATION) {
            throw new MaintenanceValidationException("RecordMaintenanceSurrenderValueCommand", "maintenanceType",
                    "只有保单终止案件可以记录退保价值");
        }
        if (!Objects.equals(settlementRefs().premiumAdjustmentId(), command.adjustmentId())) {
            throw new MaintenanceValidationException("RecordMaintenanceSurrenderValueCommand", "adjustmentId",
                    "退保价值来源与 Product 差额不一致");
        }
        validateSurrenderValue(command);
        AggregateLifecycle.apply(new MaintenanceSurrenderValueRecordedEvent(command.id(), command.adjustmentId(),
                command.policyCode(), command.policyVersion(), command.policyContentHash(), command.policyYear(),
                command.coolingOffDays(), command.refundType(), command.withinCoolingOff(), command.cashValueRate(),
                command.retainedCustomerAmount(), command.internalCostRetentionRate(), LocalDateTime.now(),
                command.updatedBy(), tenantId, command.factors()));
    }

    /** 记录 Billing 入账检查点；POSTED 仅表示余额事实登记成功，不表示资金已结算。 */
    @CommandHandler
    public void handle(RecordMaintenancePremiumPostingCommand command) {
        requireSameTenant(command.tenantId());
        if (settlementRefs().billingPostingId() != null) {
            if (samePosting(command)) {
                return;
            }
            throw new MaintenanceStatusException(maintenanceId.id(), premiumSettlementStatus.name(),
                    "RECORD_PREMIUM_POSTING", "同一保全案件不能绑定不同的 Billing 入账事实");
        }
        requireSettlementMutable("RECORD_PREMIUM_POSTING");
        if (premiumSettlementStatus != MaintenancePremiumSettlementStatus.ADJUSTMENT_CONFIRMED
                || balanceDirection == MaintenanceBalanceDirection.NONE) {
            throw new MaintenanceStatusException(maintenanceId.id(), premiumSettlementStatus.name(),
                    "RECORD_PREMIUM_POSTING", "当前差额不需要或尚不能登记 Billing 余额事实");
        }
        if (!Objects.equals(settlementRefs().premiumAdjustmentId(), command.adjustmentId())
                || !Objects.equals(settlementRefs().premiumAdjustmentResultHash(), command.adjustmentResultHash())) {
            throw new MaintenanceValidationException("RecordMaintenancePremiumPostingCommand", "adjustmentId",
                    "Billing 入账来源与 Product 差额不一致");
        }
        if (command.postingId() == null || command.postingId().isBlank() || !"POSTED".equals(command.postingStatus())) {
            throw new MaintenanceValidationException("RecordMaintenancePremiumPostingCommand", "postingId",
                    "Billing 入账事实无效");
        }
        AggregateLifecycle.apply(new MaintenancePremiumPostingRecordedEvent(command.id(), command.adjustmentId(),
                command.adjustmentResultHash(), command.postingId(), command.postingStatus(), LocalDateTime.now(),
                command.updatedBy(), tenantId));
    }

    /**
     * 记录独立资金结算检查点。CREDIT 可从处理中或失败状态恢复，DEBIT 始终只保留余额已登记语义。
     */
    @CommandHandler
    public void handle(RecordMaintenanceFinancialSettlementCommand command) {
        requireSameTenant(command.tenantId());
        validateFinancialSettlement(command);
        MaintenancePremiumSettlementStatus targetStatus =
                financialSettlementStatus(PaymentRefundStatus.fromCode(command.refundStatus()));
        if (sameFinancialSettlement(command, targetStatus)) {
            return;
        }
        if (premiumSettlementStatus == MaintenancePremiumSettlementStatus.SETTLED) {
            throw new MaintenanceStatusException(maintenanceId.id(), premiumSettlementStatus.name(),
                    "RECORD_FINANCIAL_SETTLEMENT", "已完成资金结算的保全不能回退结算事实");
        }
        AggregateLifecycle.apply(new MaintenanceFinancialSettlementRecordedEvent(command.id(), command.postingId(),
                command.refundInstructionId(), command.refundOrderId(), command.refundStatus(),
                command.commissionAdjustmentCount(), targetStatus, LocalDateTime.now(), command.updatedBy(), tenantId));
    }

    // ==================== 事件溯源处理器（业务状态，审计字段由聚合根同事件处理器维护） ====================

    @EventSourcingHandler
    public void on(MaintenancePremiumCalculatedEvent event) {
        // 载荷不携带币种（人工试算过渡事实）⇒ 按结算金币种归一；存量该事件计数 0（m25-09 实测），
        // 故严格转换（空值即 fail-fast），不做空值归一
        this.totalAmount = Money.of(event.totalAmount(), DEFAULT_CURRENCY);
        this.refundAmount = Money.of(event.refundAmount(), DEFAULT_CURRENCY);
    }

    @EventSourcingHandler
    public void on(MaintenancePremiumAdjustmentRecordedEvent event) {
        this.settlementRefs = settlementRefs().withAdjustment(event.originalCalculationId(),
                event.replacementCalculationId(), event.adjustmentId(), event.adjustmentResultHash());
        this.balanceDirection = event.direction();
        this.balance = Money.of(event.amount(), event.currency());
        this.totalAmount = event.direction() == MaintenanceBalanceDirection.DEBIT
                ? Money.of(event.amount(), event.currency())
                : Money.zero(event.currency());
        this.refundAmount = event.direction() == MaintenanceBalanceDirection.CREDIT
                ? Money.of(event.amount(), event.currency())
                : Money.zero(event.currency());
        this.premiumSettlementStatus = event.direction() == MaintenanceBalanceDirection.NONE
                ? MaintenancePremiumSettlementStatus.NOT_REQUIRED
                : MaintenancePremiumSettlementStatus.ADJUSTMENT_CONFIRMED;
    }

    @EventSourcingHandler
    public void on(MaintenanceSurrenderValueRecordedEvent event) {
        this.surrenderPolicy = SurrenderPolicySnapshot.of(event.policyCode(), event.policyVersion(),
                event.policyContentHash(), event.policyYear(), event.coolingOffDays(), event.refundType(),
                event.withinCoolingOff(), event.cashValueRate(), event.retainedCustomerAmount(),
                event.internalCostRetentionRate());
        // 🔴 event.factors()（g05-06）刻意**不进聚合状态**：它是下游展示/对账用的明细，
        //    本聚合的任何业务判定（含 sameSurrenderValue 幂等比较）都不依赖它 —— 存了也只是死字段。
        //    读侧投影直接从事件取用，无需回查聚合。若将来出现「按明细判定」的行为，再补状态字段。
    }

    @EventSourcingHandler
    public void on(MaintenancePremiumPostingRecordedEvent event) {
        this.settlementRefs = settlementRefs().withPosting(event.postingId());
        this.premiumSettlementStatus = MaintenancePremiumSettlementStatus.POSTED;
    }

    @EventSourcingHandler
    public void on(MaintenanceFinancialSettlementRecordedEvent event) {
        this.settlementRefs = settlementRefs().withRefund(event.refundInstructionId(), event.refundOrderId(),
                PaymentRefundStatus.fromCode(event.refundStatus()));
        this.commissionAdjustmentCount = event.commissionAdjustmentCount();
        this.premiumSettlementStatus = event.premiumSettlementStatus();
    }

    // ==================== 访问器 ====================

    /** 结算引用值对象（未经历结算记录事件时为空对象；分量判空即「未记录」）。 */
    public MaintenanceSettlementRefs getSettlementRefs() {
        return settlementRefs();
    }

    /** 内部读取入口：空归一到 {@link MaintenanceSettlementRefs#empty()}，回放合并与判定无需逐处判空。 */
    private MaintenanceSettlementRefs settlementRefs() {
        return settlementRefs == null ? MaintenanceSettlementRefs.empty() : settlementRefs;
    }

    /** 退保政策快照值对象（未记录时为空对象；有效性以 {@code policyContentHash} 非空为准）。 */
    public SurrenderPolicySnapshot getSurrenderPolicy() {
        return surrenderPolicy();
    }

    /** 内部读取入口：空归一到 {@link SurrenderPolicySnapshot#empty()}，幂等判定无需先判字段非空。 */
    private SurrenderPolicySnapshot surrenderPolicy() {
        return surrenderPolicy == null ? SurrenderPolicySnapshot.empty() : surrenderPolicy;
    }

    // ==================== 私有规则 ====================

    private void requireSettlementMutable(String target) {
        if (status == MaintenanceStatus.COMPLETED || status == MaintenanceStatus.REJECTED) {
            throw new MaintenanceStatusException(maintenanceId.id(), status.name(), target, "已完成或已拒绝的保全不能登记费用差额");
        }
    }

    private void validateAdjustment(RecordMaintenancePremiumAdjustmentCommand command) {
        if (command.originalCalculationId() == null || command.originalCalculationId().isBlank()
                || command.replacementCalculationId() == null || command.replacementCalculationId().isBlank()
                || command.adjustmentId() == null || command.adjustmentId().isBlank()
                || command.adjustmentResultHash() == null || command.adjustmentResultHash().isBlank()
                || command.direction() == null || command.amount() == null || command.currency() == null
                || command.currency().isBlank()) {
            throw new MaintenanceValidationException("RecordMaintenancePremiumAdjustmentCommand", "Product 差额事实字段不完整");
        }
        if (command.amount().signum() < 0
                || (command.direction() == MaintenanceBalanceDirection.NONE && command.amount().signum() != 0)
                || (command.direction() != MaintenanceBalanceDirection.NONE && command.amount().signum() <= 0)) {
            throw new MaintenanceValidationException("RecordMaintenancePremiumAdjustmentCommand", "amount",
                    "余额方向与差额金额不一致");
        }
    }

    private boolean sameAdjustment(RecordMaintenancePremiumAdjustmentCommand command) {
        MaintenanceSettlementRefs refs = settlementRefs();
        return Objects.equals(refs.originalCalculationId(), command.originalCalculationId())
                && Objects.equals(refs.replacementCalculationId(), command.replacementCalculationId())
                && Objects.equals(refs.premiumAdjustmentId(), command.adjustmentId())
                && Objects.equals(refs.premiumAdjustmentResultHash(), command.adjustmentResultHash())
                && balanceDirection == command.direction() && balance.value().compareTo(command.amount()) == 0
                && Objects.equals(balance.currency(), command.currency());
    }

    private boolean samePosting(RecordMaintenancePremiumPostingCommand command) {
        MaintenanceSettlementRefs refs = settlementRefs();
        return Objects.equals(refs.premiumAdjustmentId(), command.adjustmentId())
                && Objects.equals(refs.premiumAdjustmentResultHash(), command.adjustmentResultHash())
                && Objects.equals(refs.billingPostingId(), command.postingId())
                && "POSTED".equals(command.postingStatus());
    }

    private void validateSurrenderValue(RecordMaintenanceSurrenderValueCommand command) {
        if (!hasText(command.policyCode()) || !hasText(command.policyVersion()) || !hasText(command.policyContentHash())
                || command.policyContentHash().length() != 64 || command.policyYear() == null
                || command.policyYear() < 1 || command.coolingOffDays() == null || command.coolingOffDays() < 0
                || !hasText(command.refundType()) || command.withinCoolingOff() == null
                || invalidRate(command.cashValueRate()) || invalidRate(command.internalCostRetentionRate())
                || command.retainedCustomerAmount() == null || command.retainedCustomerAmount().signum() < 0) {
            throw new MaintenanceValidationException("RecordMaintenanceSurrenderValueCommand", "Product 退保价值策略事实不完整");
        }
    }

    private boolean sameSurrenderValue(RecordMaintenanceSurrenderValueCommand command) {
        return Objects.equals(settlementRefs().premiumAdjustmentId(), command.adjustmentId())
                && surrenderPolicy().sameAs(SurrenderPolicySnapshot.of(command.policyCode(), command.policyVersion(),
                        command.policyContentHash(), command.policyYear(), command.coolingOffDays(),
                        command.refundType(), command.withinCoolingOff(), command.cashValueRate(),
                        command.retainedCustomerAmount(), command.internalCostRetentionRate()));
    }

    private boolean invalidRate(BigDecimal value) {
        return value == null || value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0;
    }

    private void validateFinancialSettlement(RecordMaintenanceFinancialSettlementCommand command) {
        if (settlementRefs().billingPostingId() == null
                || !Objects.equals(settlementRefs().billingPostingId(), command.postingId())) {
            throw new MaintenanceValidationException("RecordMaintenanceFinancialSettlementCommand", "postingId",
                    "资金结算来源与 Billing 入账不一致");
        }
        if (command.commissionAdjustmentCount() == null || command.commissionAdjustmentCount() < 0) {
            throw new MaintenanceValidationException("RecordMaintenanceFinancialSettlementCommand",
                    "commissionAdjustmentCount", "佣金调整数量不能为负数");
        }
        boolean hasInstruction = hasText(command.refundInstructionId());
        boolean hasOrder = hasText(command.refundOrderId());
        boolean hasRefundStatus = hasText(command.refundStatus());
        if (hasOrder && !hasInstruction) {
            throw new MaintenanceValidationException("RecordMaintenanceFinancialSettlementCommand", "refundOrderId",
                    "退款订单必须关联退款指令");
        }
        if ((hasInstruction || hasOrder) && !hasRefundStatus) {
            throw new MaintenanceValidationException("RecordMaintenanceFinancialSettlementCommand", "refundStatus",
                    "退款事实必须包含退款状态");
        }
        if (balanceDirection == MaintenanceBalanceDirection.CREDIT && !hasRefundStatus) {
            throw new MaintenanceValidationException("RecordMaintenanceFinancialSettlementCommand", "refundStatus",
                    "退费资金事实必须包含退款状态");
        }
        if (balanceDirection == MaintenanceBalanceDirection.DEBIT
                && (hasInstruction || hasOrder
                        || PaymentRefundStatus.fromCode(command.refundStatus()) != PaymentRefundStatus.NOT_REQUIRED)) {
            throw new MaintenanceValidationException("RecordMaintenanceFinancialSettlementCommand", "refundStatus",
                    "追加应收不得包含退款事实");
        }
        if (balanceDirection == MaintenanceBalanceDirection.NONE) {
            throw new MaintenanceValidationException("RecordMaintenanceFinancialSettlementCommand", "refundStatus",
                    "无余额影响时不得记录资金结算");
        }
    }

    private MaintenancePremiumSettlementStatus financialSettlementStatus(PaymentRefundStatus refundStatus) {
        if (balanceDirection == MaintenanceBalanceDirection.DEBIT) {
            return MaintenancePremiumSettlementStatus.POSTED;
        }
        if (refundStatus == PaymentRefundStatus.SUCCEEDED) {
            return MaintenancePremiumSettlementStatus.SETTLED;
        }
        if (refundStatus == PaymentRefundStatus.FAILED || refundStatus == PaymentRefundStatus.CANCELLED) {
            return MaintenancePremiumSettlementStatus.SETTLEMENT_FAILED;
        }
        return MaintenancePremiumSettlementStatus.SETTLEMENT_PENDING;
    }

    private boolean sameFinancialSettlement(RecordMaintenanceFinancialSettlementCommand command,
                                            MaintenancePremiumSettlementStatus targetStatus) {
        MaintenanceSettlementRefs refs = settlementRefs();
        return Objects.equals(refs.refundInstructionId(), command.refundInstructionId())
                && Objects.equals(refs.refundOrderId(), command.refundOrderId())
                // 两侧同经 fromCode：未知码（如 PROCESSING）均归一 null ⇒ 「已记录—重放同一未知码」仍判等价，
                // 与原字符串比较语义一致（PaymentRefundStatus 契约明示的未知码处置）。
                && Objects.equals(refs.refundStatus(), PaymentRefundStatus.fromCode(command.refundStatus()))
                && Objects.equals(commissionAdjustmentCount, command.commissionAdjustmentCount())
                && premiumSettlementStatus == targetStatus;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** 是否已登记余额事实（聚合根执行保全前置判定调用）。 */
    boolean hasRecordedBalanceFact() {
        return switch (premiumSettlementStatus) {
            case POSTED, SETTLEMENT_PENDING, SETTLEMENT_FAILED, SETTLED, NOT_REQUIRED -> true;
            default -> false;
        };
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
