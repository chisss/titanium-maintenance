package com.titanium.maintenance.service;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.maintenance.service.impl.PolicyLoanDomainServiceImpl;
import com.titanium.maintenance.valueobject.PolicyLoanDetail;
import com.titanium.maintenance.valueobject.PolicyLoanResult;
import com.titanium.maintenance.valueobject.ReducedPaidUpDetail;

/**
 * 保单贷款/减额缴清领域服务测试（P2.10）
 * <p>
 * 覆盖保单贷款额度核定（上限内核准/超限拒绝/已有未偿贷款压缩额度）、利息计算与减额缴清新保额核定。
 * 纯领域服务 new 直测，不启动容器。
 * </p>
 */
class PolicyLoanDomainServiceTest {

    private final PolicyLoanDomainService service = new PolicyLoanDomainServiceImpl();

    @Test
    @DisplayName("申请额在可贷上限内：核准放款")
    void shouldApproveLoanWithinLimit() {
        // 现金价值10万，可贷80%=8万，无未偿，申请5万
        PolicyLoanDetail detail = new PolicyLoanDetail(new BigDecimal("100000"), new BigDecimal("0.8"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("50000"), new BigDecimal("0.05"));
        PolicyLoanResult result = service.evaluateLoan(detail);
        if (!result.approved() || result.loanAmount().compareTo(new BigDecimal("50000")) != 0) {
            throw new AssertionError("应核准放款5万，实际=" + result);
        }
        if (result.maxLoanableAmount().compareTo(new BigDecimal("80000.00")) != 0) {
            throw new AssertionError("可贷上限应为8万，实际=" + result.maxLoanableAmount());
        }
    }

    @Test
    @DisplayName("申请额超过可贷上限：拒绝")
    void shouldRejectLoanExceedingLimit() {
        PolicyLoanDetail detail = new PolicyLoanDetail(new BigDecimal("100000"), new BigDecimal("0.8"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("90000"), new BigDecimal("0.05"));
        PolicyLoanResult result = service.evaluateLoan(detail);
        if (result.approved() || result.loanAmount().compareTo(BigDecimal.ZERO) != 0) {
            throw new AssertionError("申请9万超8万上限应拒绝");
        }
        if (result.rejectReason() == null) {
            throw new AssertionError("拒绝应带原因");
        }
    }

    @Test
    @DisplayName("已有未偿贷款压缩可贷额度")
    void shouldShrinkLimitWithOutstandingLoan() {
        // 现金价值10万×80%=8万上限，已欠本金3万+利息5千=3.5万，剩余可贷4.5万；申请5万应拒绝
        PolicyLoanDetail detail = new PolicyLoanDetail(new BigDecimal("100000"), new BigDecimal("0.8"),
                new BigDecimal("30000"), new BigDecimal("5000"), new BigDecimal("50000"), new BigDecimal("0.05"));
        PolicyLoanResult result = service.evaluateLoan(detail);
        if (result.maxLoanableAmount().compareTo(new BigDecimal("45000.00")) != 0) {
            throw new AssertionError("扣除未偿本息后可贷上限应为4.5万，实际=" + result.maxLoanableAmount());
        }
        if (result.approved()) {
            throw new AssertionError("申请5万超4.5万剩余额度应拒绝");
        }
    }

    @Test
    @DisplayName("贷款利息按单利日计")
    void shouldAccrueInterest() {
        PolicyLoanDetail detail = new PolicyLoanDetail(new BigDecimal("100000"), new BigDecimal("0.8"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("50000"), new BigDecimal("0.05"));
        // 5万本金×5%×365/365 = 2500
        BigDecimal interest = detail.accruedInterest(365);
        if (interest.compareTo(new BigDecimal("2500.00")) != 0) {
            throw new AssertionError("一年利息应为2500，实际=" + interest);
        }
    }

    @Test
    @DisplayName("减额缴清：新保额=现金价值/每元趸缴净费率，且不超原保额")
    void shouldCalculateReducedPaidUp() {
        // 现金价值3万，每元保额趸缴净费率0.3 → 新保额10万；原保额20万
        ReducedPaidUpDetail detail = new ReducedPaidUpDetail(new BigDecimal("30000"), new BigDecimal("200000"),
                new BigDecimal("0.3"));
        BigDecimal reduced = service.calculateReducedPaidUp(detail);
        if (reduced.compareTo(new BigDecimal("100000.00")) != 0) {
            throw new AssertionError("减额后新保额应为10万，实际=" + reduced);
        }
        if (!detail.isReductionValid()) {
            throw new AssertionError("新保额10万应不超原保额20万");
        }
    }
}
