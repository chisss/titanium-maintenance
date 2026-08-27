package com.titanium.maintenance.valueobject.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactivePeriodRecalculationStatus;

class MaintenanceRetroactivePeriodRecalculationTest {

    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 8, 26, 10, 0);

    @Test
    void shouldKeepProductCheckpointWhenBillingFailsAndAllowRetry() {
        MaintenanceRetroactivePeriodRecalculation productCompleted = recalculation()
                .recordProduct(productEvidence(), STARTED_AT.plusMinutes(1));
        MaintenanceRetroactivePeriodRecalculation failed = productCompleted.fail(
                "BILLING_FAILED", "账务服务不可用", STARTED_AT.plusMinutes(2));

        assertEquals(MaintenanceRetroactivePeriodRecalculationStatus.FAILED, failed.status());
        assertNotNull(failed.productEvidence());
        assertEquals(null, failed.billingEvidence());

        MaintenanceRetroactivePeriodRecalculation retried = failed.completeBilling(
                billingEvidence(), STARTED_AT.plusMinutes(3));
        assertEquals(MaintenanceRetroactivePeriodRecalculationStatus.COMPLETED, retried.status());
        assertEquals("billing-batch-1", retried.billingEvidence().batchId());
    }

    @Test
    void shouldUseReviewRequiredAsExplicitTerminalState() {
        MaintenanceRetroactiveBillingAdjustmentEvidence review =
                new MaintenanceRetroactiveBillingAdjustmentEvidence(
                        "billing-batch-1", "REVIEW_REQUIRED", 0, 1,
                        hash('b'), hash('c'), STARTED_AT.plusMinutes(2), List.of(billingPeriod()));
        MaintenanceRetroactivePeriodRecalculation result = recalculation()
                .recordProduct(productEvidence(), STARTED_AT.plusMinutes(1))
                .completeBilling(review, STARTED_AT.plusMinutes(2));

        assertEquals(MaintenanceRetroactivePeriodRecalculationStatus.REVIEW_REQUIRED, result.status());
        assertEquals(true, result.terminal());
    }

    private MaintenanceRetroactivePeriodRecalculation recalculation() {
        return MaintenanceRetroactivePeriodRecalculation.start(
                "period-recalculation-1", 1, "operation-1", hash('q'),
                "analysis-1", 1, hash('a'), STARTED_AT);
    }

    private MaintenanceRetroactiveProductRecalculationEvidence productEvidence() {
        return new MaintenanceRetroactiveProductRecalculationEvidence(
                "product-recalculation-1", "PERIOD_V1", "calc-original", hash('o'),
                "calc-replacement", hash('n'), MaintenanceBalanceDirection.DEBIT,
                new BigDecimal("20.00"), "CNY", hash('i'), hash('r'), STARTED_AT.plusMinutes(1),
                List.of(productPeriod()));
    }

    private MaintenanceRetroactiveProductPeriodDifference productPeriod() {
        return new MaintenanceRetroactiveProductPeriodDifference(
                "BILLING:bill-1", "bill-1", LocalDateTime.of(2026, 7, 1, 0, 0),
                new BigDecimal("100.00"), new BigDecimal("120.00"),
                MaintenanceBalanceDirection.DEBIT, new BigDecimal("20.00"), "CNY",
                hash('s'), hash('p'));
    }

    private MaintenanceRetroactiveBillingAdjustmentEvidence billingEvidence() {
        return new MaintenanceRetroactiveBillingAdjustmentEvidence(
                "billing-batch-1", "POSTED", 1, 0, hash('b'), hash('c'),
                STARTED_AT.plusMinutes(2), List.of(billingPeriod()));
    }

    private MaintenanceRetroactiveBillingPeriodAdjustment billingPeriod() {
        return new MaintenanceRetroactiveBillingPeriodAdjustment(
                "BILLING:bill-1", "bill-1", "2026-07", LocalDateTime.of(2026, 7, 1, 0, 0),
                new BigDecimal("100.00"), new BigDecimal("120.00"),
                MaintenanceBalanceDirection.DEBIT, new BigDecimal("20.00"), "CNY", "POSTED",
                hash('s'), hash('p'), hash('z'));
    }

    private String hash(char value) {
        return String.valueOf((char) ('a' + Math.floorMod(value, 6))).repeat(64);
    }
}
