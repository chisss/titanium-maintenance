package com.titanium.maintenance.aggregate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.maintenance.command.ExecuteMaintenanceCommand;
import com.titanium.maintenance.command.RecordMaintenanceFinancialSettlementCommand;
import com.titanium.maintenance.command.RecordMaintenancePremiumAdjustmentCommand;
import com.titanium.maintenance.command.RecordMaintenancePremiumPostingCommand;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.MaintenancePremiumSettlementStatus;
import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.common.exception.MaintenanceStatusException;
import com.titanium.maintenance.event.MaintenanceCreatedEvent;
import com.titanium.maintenance.event.MaintenanceFinancialSettlementRecordedEvent;
import com.titanium.maintenance.event.MaintenancePremiumAdjustmentRecordedEvent;
import com.titanium.maintenance.event.MaintenancePremiumPostingRecordedEvent;
import com.titanium.maintenance.valueobject.CustomerId;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.PolicyId;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

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
                    assertEquals(new BigDecimal("53.00"), aggregate.getTotalAmount());
                    assertEquals(BigDecimal.ZERO, aggregate.getRefundAmount());
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
                    assertNull(aggregate.getBillingPostingId());
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
                    assertEquals("posting-1", aggregate.getBillingPostingId());
                    assertEquals(new BigDecimal("53.00"), aggregate.getRefundAmount());
                    assertEquals(BigDecimal.ZERO, aggregate.getTotalAmount());
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
                    assertEquals("refund-instruction-1", aggregate.getRefundInstructionId());
                    assertEquals("refund-order-1", aggregate.getRefundOrderId());
                    assertEquals("PROCESSING", aggregate.getRefundStatus());
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
        return new MaintenancePremiumAdjustmentRecordedEvent(
                ID, "calc-original", "calc-replacement", "adjustment-1", "hash-1", direction,
                amount, "CNY", NOW, "admin", "1");
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
}
