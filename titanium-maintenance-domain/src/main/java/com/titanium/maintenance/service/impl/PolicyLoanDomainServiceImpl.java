package com.titanium.maintenance.service.impl;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.titanium.maintenance.service.PolicyLoanDomainService;
import com.titanium.maintenance.valueobject.PolicyLoanDetail;
import com.titanium.maintenance.valueobject.PolicyLoanResult;
import com.titanium.maintenance.valueobject.ReducedPaidUpDetail;

/**
 * 保单贷款/减额缴清领域服务实现（纯领域逻辑，无 Port/无 Repository/无 CommandGateway）
 * <p>
 * 保单贷款：申请额 ≤ 现金价值×可贷比例−已有未偿本息 则核准，否则拒绝并给出可贷上限。
 * 减额缴清：以现金价值作趸缴净保费，新保额 = 现金价值 / 每元保额趸缴净费率。可 {@code new} 直测。
 * </p>
 */
@Service
public class PolicyLoanDomainServiceImpl implements PolicyLoanDomainService {

    @Override
    public PolicyLoanResult evaluateLoan(PolicyLoanDetail detail) {
        BigDecimal maxLoanable = detail.maxLoanableAmount();
        if (detail.isWithinLoanableLimit()) {
            return new PolicyLoanResult(true, detail.requestedAmount(), maxLoanable,
                    detail.totalPrincipalAfterLoan(), null);
        }
        return new PolicyLoanResult(false, BigDecimal.ZERO, maxLoanable, detail.outstandingPrincipal(),
                "申请贷款金额超过可贷上限 " + maxLoanable);
    }

    @Override
    public BigDecimal calculateReducedPaidUp(ReducedPaidUpDetail detail) {
        return detail.reducedSumInsured();
    }
}
