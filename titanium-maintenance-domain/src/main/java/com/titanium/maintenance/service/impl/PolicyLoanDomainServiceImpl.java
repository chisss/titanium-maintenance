package com.titanium.maintenance.service.impl;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.titanium.maintenance.service.PolicyLoanDomainService;
import com.titanium.maintenance.valueobject.PolicyLoanDetail;
import com.titanium.maintenance.valueobject.PolicyLoanResult;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;

/**
 * 保单贷款领域服务实现（纯领域逻辑，无 Port/无 Repository/无 CommandGateway）
 * <p>
 * 申请额 ≤ 现金价值×可贷比例−已有未偿本息 则核准，否则拒绝并给出可贷上限。可 {@code new} 直测。
 * </p>
 */
@Service
public class PolicyLoanDomainServiceImpl implements PolicyLoanDomainService {

    @Override
    public PolicyLoanResult evaluateLoan(PolicyLoanDetail detail) {
        BigDecimal maxLoanable = detail.maxLoanableAmount();
        if (detail.isWithinLoanableLimit()) {
            return PolicyLoanResult.approved(detail.requestedAmount(), maxLoanable,
                    detail.totalPrincipalAfterLoan());
        }
        return PolicyLoanResult.rejected(MaintenanceErrorCode.MAINTENANCE_LOAN_EXCEEDS_QUOTA,
                "申请贷款金额超过可贷上限 " + maxLoanable, maxLoanable, detail.outstandingPrincipal());
    }
}
