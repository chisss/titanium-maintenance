package com.titanium.maintenance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.enums.RefundType;
import com.titanium.maintenance.service.impl.SurrenderRefundDomainServiceImpl;
import com.titanium.maintenance.valueobject.SurrenderDetail;
import com.titanium.maintenance.valueobject.SurrenderRefundResult;

/**
 * 退保退费计算领域服务测试（纯领域逻辑，new 直测，无需 Spring 容器）
 */
class SurrenderRefundDomainServiceTest {

    private final SurrenderRefundDomainService service = new SurrenderRefundDomainServiceImpl();

    @Test
    @DisplayName("犹豫期内退保：全额退还已缴保费")
    void shouldFullRefundWithinCoolingOff() {
        LocalDate effective = LocalDate.of(2026, 7, 1);
        // 犹豫期 15 天，第 10 天申请退保 → 期内
        SurrenderDetail detail = new SurrenderDetail(new BigDecimal("10000.00"), new BigDecimal("6000.00"), effective,
                15, effective.plusDays(10));

        SurrenderRefundResult result = service.calculateRefund(detail);

        assertTrue(result.withinCoolingOff());
        assertEquals(RefundType.FULL_REFUND, result.refundType());
        assertEquals(0, result.refundAmount().compareTo(new BigDecimal("10000.00")));
    }

    @Test
    @DisplayName("犹豫期外退保：退还保单现金价值")
    void shouldRefundCashValueOutsideCoolingOff() {
        LocalDate effective = LocalDate.of(2026, 7, 1);
        // 犹豫期 15 天，第 200 天申请退保 → 期外
        SurrenderDetail detail = new SurrenderDetail(new BigDecimal("10000.00"), new BigDecimal("6000.00"), effective,
                15, effective.plusDays(200));

        SurrenderRefundResult result = service.calculateRefund(detail);

        assertFalse(result.withinCoolingOff());
        assertEquals(RefundType.DEDUCT_CASH_VALUE, result.refundType());
        assertEquals(0, result.refundAmount().compareTo(new BigDecimal("6000.00")));
    }

    @Test
    @DisplayName("犹豫期边界：恰好最后一天视为期内，全额退")
    void shouldTreatLastCoolingOffDayAsWithin() {
        LocalDate effective = LocalDate.of(2026, 7, 1);
        SurrenderDetail detail = new SurrenderDetail(new BigDecimal("8000.00"), new BigDecimal("5000.00"), effective, 15,
                effective.plusDays(15));

        SurrenderRefundResult result = service.calculateRefund(detail);

        assertTrue(result.withinCoolingOff());
        assertEquals(RefundType.FULL_REFUND, result.refundType());
    }
}
