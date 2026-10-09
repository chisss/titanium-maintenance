package com.titanium.maintenance.valueobject;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;

/**
 * 部分领取额度值对象测试（g04-01）
 * <p>
 * 正反成对：领取额在可领取上限内（含等于上限）放行、超上限拒绝且携 71 段
 * {@link MaintenanceErrorCode#MAINTENANCE_WITHDRAWAL_EXCEEDS_QUOTA}；构造器不变量
 * （现金价值非负、比例 (0,1]、金额为正）与上限计算。
 * </p>
 */
class WithdrawalQuotaTest {

    @Test
    @DisplayName("正例：领取额低于可领取上限 → 放行")
    void shouldAllowWithinLimit() {
        WithdrawalQuota quota = new WithdrawalQuota(new BigDecimal("100000"), new BigDecimal("0.5"),
                new BigDecimal("30000"));
        assertTrue(quota.isWithinWithdrawalLimit());
        assertDoesNotThrow(quota::requireWithinWithdrawalLimit);
    }

    @Test
    @DisplayName("正例：领取额等于可领取上限 → 放行（边界值放行）")
    void shouldAllowEqualsLimit() {
        WithdrawalQuota quota = new WithdrawalQuota(new BigDecimal("100000"), new BigDecimal("0.5"),
                new BigDecimal("50000"));
        assertTrue(quota.isWithinWithdrawalLimit());
        assertDoesNotThrow(quota::requireWithinWithdrawalLimit);
    }

    @Test
    @DisplayName("反例：领取额超过可领取上限 → 拒绝并携 71 段错误码")
    void shouldRejectExceedingLimit() {
        WithdrawalQuota quota = new WithdrawalQuota(new BigDecimal("100000"), new BigDecimal("0.5"),
                new BigDecimal("50001"));
        assertFalse(quota.isWithinWithdrawalLimit());
        MaintenanceValidationException ex = assertThrows(MaintenanceValidationException.class,
                quota::requireWithinWithdrawalLimit);
        assertEquals(MaintenanceErrorCode.MAINTENANCE_WITHDRAWAL_EXCEEDS_QUOTA, ex.getErrorCode());
    }

    @Test
    @DisplayName("反例：可领取上限阈值 0（现金价值 0）→ 任意领取额一律拒绝")
    void shouldRejectWhenLimitIsZero() {
        WithdrawalQuota quota = new WithdrawalQuota(BigDecimal.ZERO, new BigDecimal("0.5"),
                new BigDecimal("1"));
        assertFalse(quota.isWithinWithdrawalLimit());
        assertThrows(MaintenanceValidationException.class, quota::requireWithinWithdrawalLimit);
    }

    @Test
    @DisplayName("上限计算：现金价值 × 可领取比例，保留两位小数")
    void shouldComputeMaxWithdrawableAmount() {
        WithdrawalQuota quota = new WithdrawalQuota(new BigDecimal("121.2"), new BigDecimal("0.6"),
                new BigDecimal("1"));
        assertEquals(new BigDecimal("72.72"), quota.maxWithdrawableAmount());
    }

    @Test
    @DisplayName("构造器不变量：可领取比例为 0 → 构造即拒绝")
    void shouldRejectZeroRatioOnConstruction() {
        assertThrows(MaintenanceValidationException.class,
                () -> new WithdrawalQuota(new BigDecimal("100000"), BigDecimal.ZERO, new BigDecimal("1")));
    }

    @Test
    @DisplayName("构造器不变量：可领取比例超过 1 → 构造即拒绝")
    void shouldRejectRatioAboveOneOnConstruction() {
        assertThrows(MaintenanceValidationException.class,
                () -> new WithdrawalQuota(new BigDecimal("100000"), new BigDecimal("1.2"), new BigDecimal("1")));
    }

    @Test
    @DisplayName("构造器不变量：申请领取金额必须大于零 → 构造即拒绝")
    void shouldRejectNonPositiveRequestedOnConstruction() {
        assertThrows(MaintenanceValidationException.class,
                () -> new WithdrawalQuota(new BigDecimal("100000"), new BigDecimal("0.5"), BigDecimal.ZERO));
    }
}
