package com.titanium.maintenance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.service.impl.PolicyLoanDomainServiceImpl;
import com.titanium.maintenance.valueobject.PolicyLoanDetail;
import com.titanium.maintenance.valueobject.PolicyLoanResult;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;

/**
 * 保单贷款额度校验测试（g04-01 / AC-01）
 * <p>
 * 额度上限有牙正反成对：超上限必须拒绝并携 71 段 {@link MaintenanceErrorCode#MAINTENANCE_LOAN_EXCEEDS_QUOTA}
 * 错误码、不得静默截断到上限；等于上限放行；上限阈值 0 时一律拒绝。纯领域 new 直测，不启动容器。
 * </p>
 */
class PolicyLoanQuotaTest {

    private final PolicyLoanDomainService service = new PolicyLoanDomainServiceImpl();

    @Test
    @DisplayName("AC-01 正例：申请额低于可贷上限 → 核准且放款额=申请额")
    void shouldApproveWhenRequestedBelowLimit() {
        PolicyLoanResult result = service.evaluateLoan(detail("100000", "0.8", "50000"));

        assertTrue(result.approved(), "低于上限应核准");
        assertEquals(0, result.loanAmount().compareTo(new BigDecimal("50000")), "放款额必须等于申请额");
        assertNull(result.errorCode(), "核准结果不得携带错误码");
    }

    @Test
    @DisplayName("AC-01 正例：申请额等于可贷上限 → 核准放行（边界值放行）")
    void shouldApproveWhenRequestedEqualsLimit() {
        PolicyLoanResult result = service.evaluateLoan(detail("100000", "0.8", "80000"));

        assertTrue(result.approved(), "等于上限应放行");
        assertEquals(0, result.loanAmount().compareTo(new BigDecimal("80000")), "放款额必须等于申请额（80000）");
    }

    @Test
    @DisplayName("AC-01 反例：申请额超过可贷上限 → 拒绝且携 71 段额度错误码")
    void shouldRejectWhenRequestedExceedsLimit() {
        PolicyLoanResult result = service.evaluateLoan(detail("100000", "0.8", "90000"));

        assertFalse(result.approved(), "超上限必须拒绝");
        assertEquals(MaintenanceErrorCode.MAINTENANCE_LOAN_EXCEEDS_QUOTA, result.errorCode(),
                "拒绝必须携带 MAINTENANCE_LOAN_EXCEEDS_QUOTA（71 段）错误码");
    }

    @Test
    @DisplayName("AC-01 反例：超上限拒绝不得静默截断到上限（放款额为 0，非上限值）")
    void shouldNotSilentlyTruncateToLimit() {
        PolicyLoanResult result = service.evaluateLoan(detail("100000", "0.8", "90000"));

        assertEquals(BigDecimal.ZERO, result.loanAmount(),
                "拒绝时放款额必须为 0，不得静默截断为可贷上限 80000");
    }

    @Test
    @DisplayName("AC-01 反例：可贷上限阈值 0（现金价值 0）→ 任意申请额一律拒绝")
    void shouldRejectWhenLimitIsZero() {
        PolicyLoanResult result = service.evaluateLoan(detail("0", "0.8", "1"));

        assertFalse(result.approved(), "上限为 0 时申请 1 元也必须拒绝");
        assertEquals(MaintenanceErrorCode.MAINTENANCE_LOAN_EXCEEDS_QUOTA, result.errorCode());
    }

    @Test
    @DisplayName("AC-01 反例：requireWithinLoanableLimit 超限必须抛异常并携带额度错误码")
    void shouldThrowWithErrorCodeWhenRequiringWithinLimit() {
        PolicyLoanDetail detail = detail("100000", "0.8", "90000");

        MaintenanceValidationException ex = assertThrows(MaintenanceValidationException.class,
                detail::requireWithinLoanableLimit, "超限校验必须显式抛异常");
        assertEquals(MaintenanceErrorCode.MAINTENANCE_LOAN_EXCEEDS_QUOTA, ex.getErrorCode(),
                "异常必须携带 MAINTENANCE_LOAN_EXCEEDS_QUOTA 错误码");
    }

    @Test
    @DisplayName("AC-01 正例：requireWithinLoanableLimit 在限内（含等于上限）不抛异常")
    void shouldNotThrowWhenWithinLimit() {
        detail("100000", "0.8", "80000").requireWithinLoanableLimit();
        detail("100000", "0.8", "50000").requireWithinLoanableLimit();
    }

    /** 构造无未偿贷款、固定利率 5% 的贷款明细。 */
    private PolicyLoanDetail detail(String cashValue, String ratio, String requested) {
        return new PolicyLoanDetail(new BigDecimal(cashValue), new BigDecimal(ratio),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal(requested), new BigDecimal("0.05"));
    }
}
