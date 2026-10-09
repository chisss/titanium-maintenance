package com.titanium.maintenance.valueobject;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;

/**
 * 保额上下限值对象测试（g04-01）
 * <p>
 * 正反成对：区间内（含边界值）放行、低于下限/高于上限拒绝且携 71 段
 * {@link MaintenanceErrorCode#MAINTENANCE_SUM_INSURED_OUT_OF_BOUND}；无上限（maximum null）与
 * 构造器不变量（最高保额不得低于最低保额、最低保额非负）。
 * </p>
 */
class SumInsuredBoundTest {

    @Test
    @DisplayName("正例：申请保额在 [最低, 最高] 区间内 → 放行")
    void shouldAllowWithinBound() {
        SumInsuredBound bound = new SumInsuredBound(new BigDecimal("100000"), new BigDecimal("500000"));
        assertDoesNotThrow(() -> bound.requireWithin(new BigDecimal("300000")));
    }

    @Test
    @DisplayName("正例：申请保额等于最低/最高边界值 → 放行")
    void shouldAllowBoundaryValues() {
        SumInsuredBound bound = new SumInsuredBound(new BigDecimal("100000"), new BigDecimal("500000"));
        assertDoesNotThrow(() -> bound.requireWithin(new BigDecimal("100000")));
        assertDoesNotThrow(() -> bound.requireWithin(new BigDecimal("500000")));
    }

    @Test
    @DisplayName("反例：申请保额低于最低保额 → 拒绝并携 71 段错误码")
    void shouldRejectBelowMinimum() {
        SumInsuredBound bound = new SumInsuredBound(new BigDecimal("100000"), new BigDecimal("500000"));
        MaintenanceValidationException ex = assertThrows(MaintenanceValidationException.class,
                () -> bound.requireWithin(new BigDecimal("99999")));
        assertEquals(MaintenanceErrorCode.MAINTENANCE_SUM_INSURED_OUT_OF_BOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("反例：申请保额高于最高保额 → 拒绝并携 71 段错误码")
    void shouldRejectAboveMaximum() {
        SumInsuredBound bound = new SumInsuredBound(new BigDecimal("100000"), new BigDecimal("500000"));
        MaintenanceValidationException ex = assertThrows(MaintenanceValidationException.class,
                () -> bound.requireWithin(new BigDecimal("500001")));
        assertEquals(MaintenanceErrorCode.MAINTENANCE_SUM_INSURED_OUT_OF_BOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("正例：maximum 为 null 表示无上限 → 任意不小于最低的保额放行")
    void shouldAllowUnboundedMaximum() {
        SumInsuredBound bound = new SumInsuredBound(new BigDecimal("100000"), null);
        assertDoesNotThrow(() -> bound.requireWithin(new BigDecimal("100000000")));
    }

    @Test
    @DisplayName("反例：无上限时低于最低保额仍拒绝")
    void shouldRejectBelowMinimumWhenUnbounded() {
        SumInsuredBound bound = new SumInsuredBound(new BigDecimal("100000"), null);
        assertThrows(MaintenanceValidationException.class, () -> bound.requireWithin(new BigDecimal("1")));
    }

    @Test
    @DisplayName("构造器不变量：最高保额低于最低保额 → 构造即拒绝")
    void shouldRejectInvertedBoundOnConstruction() {
        assertThrows(MaintenanceValidationException.class,
                () -> new SumInsuredBound(new BigDecimal("500000"), new BigDecimal("100000")));
    }

    @Test
    @DisplayName("构造器不变量：最低保额为负 → 构造即拒绝")
    void shouldRejectNegativeMinimumOnConstruction() {
        assertThrows(MaintenanceValidationException.class,
                () -> new SumInsuredBound(new BigDecimal("-1"), null));
    }

    @Test
    @DisplayName("正例：minimum 为 null 归一为 0 → 0 保额为边界值放行、负数拒绝")
    void shouldNormalizeNullMinimumToZero() {
        SumInsuredBound bound = new SumInsuredBound(null, new BigDecimal("500000"));
        assertDoesNotThrow(() -> bound.requireWithin(BigDecimal.ZERO), "0 等于最低值，属边界放行");
        MaintenanceValidationException ex = assertThrows(MaintenanceValidationException.class,
                () -> bound.requireWithin(new BigDecimal("-1")));
        assertEquals(MaintenanceErrorCode.MAINTENANCE_SUM_INSURED_OUT_OF_BOUND, ex.getErrorCode());
    }
}
