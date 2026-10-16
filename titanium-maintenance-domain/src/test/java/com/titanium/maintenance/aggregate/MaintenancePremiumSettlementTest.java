package com.titanium.maintenance.aggregate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.maintenance.command.CalculateMaintenancePremiumCommand;
import com.titanium.maintenance.command.ExecuteMaintenanceCommand;
import com.titanium.maintenance.command.RecordMaintenanceFinancialSettlementCommand;
import com.titanium.maintenance.command.RecordMaintenancePremiumAdjustmentCommand;
import com.titanium.maintenance.command.RecordMaintenancePremiumPostingCommand;
import com.titanium.maintenance.command.RecordMaintenanceSurrenderValueCommand;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.MaintenancePremiumSettlementStatus;
import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.common.enums.PaymentRefundStatus;
import com.titanium.maintenance.common.exception.MaintenanceStatusException;
import com.titanium.maintenance.event.MaintenanceCreatedEvent;
import com.titanium.maintenance.event.MaintenanceFinancialSettlementRecordedEvent;
import com.titanium.maintenance.event.MaintenancePremiumAdjustmentRecordedEvent;
import com.titanium.maintenance.event.MaintenancePremiumPostingRecordedEvent;
import com.titanium.maintenance.event.MaintenanceSurrenderValueRecordedEvent;
import com.titanium.maintenance.valueobject.CustomerId;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.PolicyId;
import com.titanium.maintenance.valueobject.SurrenderPolicySnapshot;
import com.titanium.metadata.enums.maintenance.MaintenanceType;
import com.titanium.metadata.valueobject.Money;

class MaintenancePremiumSettlementTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 20, 12, 0);
    private static final MaintenanceId ID = MaintenanceId.of("maintenance-1");

    private FixtureConfiguration<Maintenance> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Maintenance.class);
    }

    @Test
    void shouldRecordDebitAdjustmentAsPendingSettlement() {
        fixture.given(createdEvent())
                .when(new RecordMaintenancePremiumAdjustmentCommand(
                        ID, "calc-original", "calc-replacement", "adjustment-1", "hash-1",
                        MaintenanceBalanceDirection.DEBIT, new BigDecimal("53.00"), "CNY", "admin", "1"))
                .expectSuccessfulHandlerExecution()
                .expectState(aggregate -> {
                    assertEquals(MaintenancePremiumSettlementStatus.ADJUSTMENT_CONFIRMED,
                            aggregate.getPremiumSettlementStatus());
                    assertEquals(Money.of(new BigDecimal("53.00"), "CNY"), aggregate.getTotalAmount());
                    assertEquals(Money.zero("CNY"), aggregate.getRefundAmount());
                    assertEquals(MaintenanceBalanceDirection.DEBIT, aggregate.getBalanceDirection());
                });
    }

    @Test
    void shouldSkipBillingForNoBalanceImpact() {
        fixture.given(createdEvent())
                .when(new RecordMaintenancePremiumAdjustmentCommand(
                        ID, "calc-original", "calc-replacement", "adjustment-1", "hash-1",
                        MaintenanceBalanceDirection.NONE, BigDecimal.ZERO, "CNY", "admin", "1"))
                .expectSuccessfulHandlerExecution()
                .expectState(aggregate -> {
                    assertEquals(MaintenancePremiumSettlementStatus.NOT_REQUIRED,
                            aggregate.getPremiumSettlementStatus());
                    assertNull(aggregate.getSettlementRefs().billingPostingId());
                });
    }

    @Test
    void shouldRecordBillingPostingWithoutClaimingFundsSettled() {
        fixture.given(createdEvent(), adjustmentEvent(MaintenanceBalanceDirection.CREDIT, new BigDecimal("53.00")))
                .when(new RecordMaintenancePremiumPostingCommand(
                        ID, "adjustment-1", "hash-1", "posting-1", "POSTED", "admin", "1"))
                .expectSuccessfulHandlerExecution()
                .expectState(aggregate -> {
                    assertEquals(MaintenancePremiumSettlementStatus.POSTED,
                            aggregate.getPremiumSettlementStatus());
                    assertEquals("posting-1", aggregate.getSettlementRefs().billingPostingId());
                    assertEquals(Money.of(new BigDecimal("53.00"), "CNY"), aggregate.getRefundAmount());
                    assertEquals(Money.zero("CNY"), aggregate.getTotalAmount());
                });
    }

    /**
     * 人工计价路径币种归一（DSN-07）：计算事件载荷不携带币种，金额按结算金默认币种（CNY）归一。
     * <p>
     * 与 {@link #shouldCarryAdjustmentEventCurrencyIntoBalance()} 构成币种来源两侧对照：
     * 一侧证明无币种载荷按默认归一，一侧证明调整事件自带币种被如实取用——默认币种若被改动
     * 或调整侧误用默认币种，各有一侧判红。
     * </p>
     */
    @Test
    void shouldNormalizeCalculatedPremiumToDefaultCurrency() {
        fixture.given(createdEvent())
                .when(new CalculateMaintenancePremiumCommand(
                        ID, new BigDecimal("53.00"), BigDecimal.ZERO, "人工试算", "admin", "1"))
                .expectSuccessfulHandlerExecution()
                .expectState(aggregate -> {
                    assertEquals(Money.of(new BigDecimal("53.00"), "CNY"), aggregate.getTotalAmount());
                    assertEquals(Money.zero("CNY"), aggregate.getRefundAmount());
                });
    }

    /**
     * 调整事件币种如实取用（DSN-07）：Product 差额事实自带币种（本例 USD），余额/总额/退费须随之，
     * 而非按默认币种归一。
     * <p>
     * 命令以同一差额事实重放（{@code sameAdjustment} 早返回，不发事件），断言点同时覆盖
     * 余额三元组的币种来源与幂等判等的币种口径。
     * </p>
     */
    @Test
    void shouldCarryAdjustmentEventCurrencyIntoBalance() {
        fixture.given(createdEvent(),
                        adjustmentEvent(MaintenanceBalanceDirection.DEBIT, new BigDecimal("53.00"), "USD"))
                .when(new RecordMaintenancePremiumAdjustmentCommand(
                        ID, "calc-original", "calc-replacement", "adjustment-1", "hash-1",
                        MaintenanceBalanceDirection.DEBIT, new BigDecimal("53.00"), "USD", "admin", "1"))
                .expectSuccessfulHandlerExecution()
                .expectNoEvents()
                .expectState(aggregate -> {
                    assertEquals(Money.of(new BigDecimal("53.00"), "USD"), aggregate.getBalance());
                    assertEquals(Money.of(new BigDecimal("53.00"), "USD"), aggregate.getTotalAmount());
                    assertEquals(Money.zero("USD"), aggregate.getRefundAmount());
                });
    }

    @Test
    void shouldTreatSamePostingAsIdempotent() {
        fixture.given(
                        createdEvent(),
                        adjustmentEvent(MaintenanceBalanceDirection.DEBIT, new BigDecimal("53.00")),
                        new MaintenancePremiumPostingRecordedEvent(
                                ID, "adjustment-1", "hash-1", "posting-1", "POSTED", NOW, "admin", "1"))
                .when(new RecordMaintenancePremiumPostingCommand(
                        ID, "adjustment-1", "hash-1", "posting-1", "POSTED", "admin", "1"))
                .expectSuccessfulHandlerExecution()
                .expectNoEvents();
    }

    @Test
    void shouldRecordPendingCreditSettlement() {
        fixture.given(createdEvent(), adjustmentEvent(MaintenanceBalanceDirection.CREDIT, new BigDecimal("53.00")),
                        postingEvent(MaintenanceBalanceDirection.CREDIT))
                .when(new RecordMaintenanceFinancialSettlementCommand(
                        ID, "posting-1", "refund-instruction-1", "refund-order-1", "PROCESSING", 2, "admin", "1"))
                .expectSuccessfulHandlerExecution()
                .expectState(aggregate -> {
                    assertEquals(MaintenancePremiumSettlementStatus.SETTLEMENT_PENDING,
                            aggregate.getPremiumSettlementStatus());
                    assertEquals("refund-instruction-1", aggregate.getSettlementRefs().refundInstructionId());
                    assertEquals("refund-order-1", aggregate.getSettlementRefs().refundOrderId());
                    assertNull(aggregate.getSettlementRefs().refundStatus(),
                            "未收录的外部退款状态码（PROCESSING）经枚举契约归一为 null（未收录按未知处理）");
                    assertEquals(2, aggregate.getCommissionAdjustmentCount());
                });
    }

    @Test
    void shouldRecoverFailedCreditSettlementToSettled() {
        fixture.given(createdEvent(), adjustmentEvent(MaintenanceBalanceDirection.CREDIT, new BigDecimal("53.00")),
                        postingEvent(MaintenanceBalanceDirection.CREDIT),
                        financialEvent("FAILED", MaintenancePremiumSettlementStatus.SETTLEMENT_FAILED))
                .when(new RecordMaintenanceFinancialSettlementCommand(
                        ID, "posting-1", "refund-instruction-1", "refund-order-1", "SUCCEEDED", 2, "admin", "1"))
                .expectSuccessfulHandlerExecution()
                .expectState(aggregate -> assertEquals(
                        MaintenancePremiumSettlementStatus.SETTLED, aggregate.getPremiumSettlementStatus()));
    }

    @Test
    void shouldRejectRefundOrderWithoutInstruction() {
        fixture.given(createdEvent(), adjustmentEvent(MaintenanceBalanceDirection.CREDIT, new BigDecimal("53.00")),
                        postingEvent(MaintenanceBalanceDirection.CREDIT))
                .when(new RecordMaintenanceFinancialSettlementCommand(
                        ID, "posting-1", null, "refund-order-1", "PROCESSING", 0, "admin", "1"))
                .expectException(com.titanium.maintenance.common.exception.MaintenanceValidationException.class);
    }

    @Test
    void shouldRejectRefundIdentifiersForDebitPosting() {
        fixture.given(createdEvent(), adjustmentEvent(MaintenanceBalanceDirection.DEBIT, new BigDecimal("53.00")),
                        postingEvent(MaintenanceBalanceDirection.DEBIT))
                .when(new RecordMaintenanceFinancialSettlementCommand(
                        ID, "posting-1", "refund-instruction-1", null, "PROCESSING", 0, "admin", "1"))
                .expectException(com.titanium.maintenance.common.exception.MaintenanceValidationException.class);
    }

    @Test
    void shouldRequireNotRequiredStatusForDebitPosting() {
        fixture.given(createdEvent(), adjustmentEvent(MaintenanceBalanceDirection.DEBIT, new BigDecimal("53.00")),
                        postingEvent(MaintenanceBalanceDirection.DEBIT))
                .when(new RecordMaintenanceFinancialSettlementCommand(
                        ID, "posting-1", null, null, null, 0, "admin", "1"))
                .expectException(com.titanium.maintenance.common.exception.MaintenanceValidationException.class);
    }

    @Test
    void shouldTreatSameFinancialSettlementAsIdempotent() {
        fixture.given(createdEvent(), adjustmentEvent(MaintenanceBalanceDirection.CREDIT, new BigDecimal("53.00")),
                        postingEvent(MaintenanceBalanceDirection.CREDIT),
                        financialEvent("PROCESSING", MaintenancePremiumSettlementStatus.SETTLEMENT_PENDING))
                .when(new RecordMaintenanceFinancialSettlementCommand(
                        ID, "posting-1", "refund-instruction-1", "refund-order-1", "PROCESSING", 2, "admin", "1"))
                .expectSuccessfulHandlerExecution()
                .expectNoEvents();
    }

    /**
     * 已收录的退款状态码（SUCCEEDED）读回应为枚举，且同一内容重放判等（幂等）。
     * <p>
     * 与 {@link #shouldRecordPendingCreditSettlement()}（未收录码归一 null）构成值域两侧对照：
     * 一侧证明枚举化后已知码可读回，一侧证明未知码按枚举契约归一为未知，而非静默保留原字符串。
     * </p>
     */
    @Test
    void shouldExposeRecordedRefundStatusAsEnumAndTreatReplayAsIdempotent() {
        fixture.given(createdEvent(), adjustmentEvent(MaintenanceBalanceDirection.CREDIT, new BigDecimal("53.00")),
                        postingEvent(MaintenanceBalanceDirection.CREDIT),
                        financialEvent("SUCCEEDED", MaintenancePremiumSettlementStatus.SETTLED))
                .when(new RecordMaintenanceFinancialSettlementCommand(
                        ID, "posting-1", "refund-instruction-1", "refund-order-1", "SUCCEEDED", 2, "admin", "1"))
                .expectSuccessfulHandlerExecution()
                .expectNoEvents()
                .expectState(aggregate -> {
                    assertEquals(PaymentRefundStatus.SUCCEEDED, aggregate.getSettlementRefs().refundStatus());
                    assertEquals(MaintenancePremiumSettlementStatus.SETTLED, aggregate.getPremiumSettlementStatus());
                });
    }

    /**
     * 退保价值记录后，聚合以 {@link SurrenderPolicySnapshot} 值对象读回全部 10 个分量（m25-02 值对象化的聚合接线）。
     */
    @Test
    void shouldExposeSurrenderPolicySnapshotOnTerminationCase() {
        fixture.given(createdEvent(MaintenanceType.POLICY_TERMINATION),
                        adjustmentEvent(MaintenanceBalanceDirection.CREDIT, new BigDecimal("53.00")))
                .when(surrenderValueCommand(Boolean.TRUE, new BigDecimal("0.75")))
                .expectSuccessfulHandlerExecution()
                .expectState(aggregate -> {
                    SurrenderPolicySnapshot snapshot = aggregate.getSurrenderPolicy();
                    assertEquals("policy-code-1", snapshot.policyCode());
                    assertEquals("v1", snapshot.policyVersion());
                    assertEquals(SURRENDER_POLICY_HASH, snapshot.policyContentHash());
                    assertEquals(1, snapshot.policyYear());
                    assertEquals(15, snapshot.coolingOffDays());
                    assertEquals("CASH_VALUE", snapshot.refundType());
                    assertTrue(snapshot.withinCoolingOff(), "命令侧 Boolean.TRUE 写入值对象 primitive boolean");
                    assertEquals(0, new BigDecimal("0.75").compareTo(snapshot.cashValueRate()));
                    assertEquals(0, new BigDecimal("1000.00").compareTo(snapshot.retainedCustomerAmount()));
                    assertEquals(0, new BigDecimal("0.05").compareTo(snapshot.internalCostRetentionRate()));
                });
    }

    /**
     * 同一退保策略事实重放判幂等——且金额比较对 scale 不敏感（事件 0.75 vs 命令 0.750 判等价）。
     * <p>
     * 与 {@link #shouldExposeSurrenderPolicySnapshotOnTerminationCase()} 构成对照：一侧证明首次记录
     * 写入快照，一侧证明同事实重放早返回不发事件（{@code sameSurrenderValue} → {@code sameAs} 链路）。
     * </p>
     */
    @Test
    void shouldTreatSameSurrenderPolicyReplayAsIdempotent() {
        fixture.given(createdEvent(MaintenanceType.POLICY_TERMINATION),
                        adjustmentEvent(MaintenanceBalanceDirection.CREDIT, new BigDecimal("53.00")),
                        surrenderValueEvent(Boolean.TRUE, new BigDecimal("0.75")))
                .when(surrenderValueCommand(Boolean.TRUE, new BigDecimal("0.750")))
                .expectSuccessfulHandlerExecution()
                .expectNoEvents()
                .expectState(aggregate -> assertEquals(0,
                        new BigDecimal("0.75").compareTo(aggregate.getSurrenderPolicy().cashValueRate())));
    }

    /**
     * 存量事件兼容：历史退保事件载荷的 {@code withinCoolingOff} 可能为 {@code null}（Boolean），
     * 回放经值对象 {@code of} 归一为 false——不抛 NPE、不丢快照其余分量，且与显式 false 的新命令判等价。
     */
    @Test
    void shouldNormalizeNullableWithinCoolingOffOnLegacyEventReplay() {
        fixture.given(createdEvent(MaintenanceType.POLICY_TERMINATION),
                        adjustmentEvent(MaintenanceBalanceDirection.CREDIT, new BigDecimal("53.00")),
                        surrenderValueEvent(null, new BigDecimal("0.75")))
                .when(surrenderValueCommand(Boolean.FALSE, new BigDecimal("0.75")))
                .expectSuccessfulHandlerExecution()
                .expectNoEvents()
                .expectState(aggregate -> {
                    assertFalse(aggregate.getSurrenderPolicy().withinCoolingOff(),
                            "存量事件 withinCoolingOff=null 按「未标记即非犹豫期」归一为 false");
                    assertEquals(SURRENDER_POLICY_HASH, aggregate.getSurrenderPolicy().policyContentHash(),
                            "快照其余分量不得因归一丢失");
                });
    }

    /**
     * 减额缴清不产生任何收付（现金价值转趸缴），故<b>不要求</b>先登记余额事实即可执行。
     * <p>
     * 与下一条 {@link #shouldStillRequireSettlementForCoverageAmountChange()} 构成对照组：
     * 两条用例只有 {@code maintenanceType} 一个变量不同，一侧放行、一侧拒绝，证明的是
     * 「判据按类型分流」，而不是「判据被整体删除」。
     * </p>
     */
    @Test
    void shouldExecuteReducedPaidUpWithoutRequiringBalanceFact() {
        fixture.given(createdEvent(MaintenanceType.REDUCED_PAID_UP))
                .when(new ExecuteMaintenanceCommand(ID, NOW, "减额缴清生效", "admin", "1"))
                .expectSuccessfulHandlerExecution()
                .expectState(aggregate -> assertEquals(MaintenanceStatus.COMPLETED, aggregate.getStatus()));
    }

    @Test
    void shouldStillRequireSettlementForCoverageAmountChange() {
        fixture.given(createdEvent(MaintenanceType.COVERAGE_AMOUNT_CHANGE))
                .when(new ExecuteMaintenanceCommand(ID, NOW, "保额批改生效", "admin", "1"))
                .expectException(MaintenanceStatusException.class);
    }

    private MaintenanceCreatedEvent createdEvent() {
        return createdEvent(MaintenanceType.COVERAGE_AMOUNT_CHANGE);
    }

    private MaintenanceCreatedEvent createdEvent(MaintenanceType type) {
        return new MaintenanceCreatedEvent(
                ID, PolicyId.of("policy-1"), CustomerId.of("customer-1"),
                type, EffectiveTimeType.IMMEDIATE, null,
                "保额批改", NOW, "admin", "1");
    }

    private MaintenancePremiumAdjustmentRecordedEvent adjustmentEvent(
            MaintenanceBalanceDirection direction, BigDecimal amount) {
        return adjustmentEvent(direction, amount, "CNY");
    }

    private MaintenancePremiumAdjustmentRecordedEvent adjustmentEvent(
            MaintenanceBalanceDirection direction, BigDecimal amount, String currency) {
        return new MaintenancePremiumAdjustmentRecordedEvent(
                ID, "calc-original", "calc-replacement", "adjustment-1", "hash-1", direction,
                amount, currency, NOW, "admin", "1");
    }

    private MaintenancePremiumPostingRecordedEvent postingEvent(MaintenanceBalanceDirection direction) {
        return new MaintenancePremiumPostingRecordedEvent(
                ID, "adjustment-1", "hash-1", "posting-1", "POSTED", NOW, "admin", "1");
    }

    private MaintenanceFinancialSettlementRecordedEvent financialEvent(
            String refundStatus, MaintenancePremiumSettlementStatus settlementStatus) {
        return new MaintenanceFinancialSettlementRecordedEvent(
                ID, "posting-1", "refund-instruction-1", "refund-order-1", refundStatus, 2,
                settlementStatus, NOW, "admin", "1");
    }

    /** 退保政策内容哈希（64 位，满足 validateSurrenderValue 的长度校验） */
    private static final String SURRENDER_POLICY_HASH = "0".repeat(64);

    private RecordMaintenanceSurrenderValueCommand surrenderValueCommand(Boolean withinCoolingOff,
            BigDecimal cashValueRate) {
        return new RecordMaintenanceSurrenderValueCommand(ID, "adjustment-1", "policy-code-1", "v1",
                SURRENDER_POLICY_HASH, 1, 15, "CASH_VALUE", withinCoolingOff, cashValueRate,
                new BigDecimal("1000.00"), new BigDecimal("0.05"), "admin", "1", List.of());
    }

    private MaintenanceSurrenderValueRecordedEvent surrenderValueEvent(Boolean withinCoolingOff,
            BigDecimal cashValueRate) {
        return new MaintenanceSurrenderValueRecordedEvent(ID, "adjustment-1", "policy-code-1", "v1",
                SURRENDER_POLICY_HASH, 1, 15, "CASH_VALUE", withinCoolingOff, cashValueRate,
                new BigDecimal("1000.00"), new BigDecimal("0.05"), NOW, "admin", "1", List.of());
    }
}
