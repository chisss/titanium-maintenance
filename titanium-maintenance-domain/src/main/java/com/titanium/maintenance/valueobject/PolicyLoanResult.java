package com.titanium.maintenance.valueobject;

import java.math.BigDecimal;

import com.titanium.metadata.errorcode.MaintenanceErrorCode;

/**
 * 保单贷款计算结果值对象
 * <p>
 * g04-01：拒绝结果必须携带 71 段 {@link MaintenanceErrorCode} 错误码枚举，供调用方以机器判据分派
 * （红线 19），拒绝文案仅作展示——拒绝必须显式、不得静默截断到上限。
 * </p>
 *
 * @param approved 是否核准放款（申请额在可贷上限内）
 * @param loanAmount 实际放款金额（核准时为申请额，拒绝时为 0，不截断）
 * @param maxLoanableAmount 可贷款上限
 * @param totalPrincipalAfterLoan 放款后累计贷款本金（拒绝时为已有未偿本金）
 * @param rejectReason 拒绝原因文案（核准时为 null）
 * @param errorCode 拒绝错误码（核准时为 null，拒绝时必填 71 段枚举）
 */
public record PolicyLoanResult(boolean approved, BigDecimal loanAmount, BigDecimal maxLoanableAmount,
                               BigDecimal totalPrincipalAfterLoan, String rejectReason,
                               MaintenanceErrorCode errorCode) {

    /** 核准结果工厂：放款额 = 申请额（不截断）。 */
    public static PolicyLoanResult approved(BigDecimal loanAmount, BigDecimal maxLoanableAmount,
                                            BigDecimal totalPrincipalAfterLoan) {
        return new PolicyLoanResult(true, loanAmount, maxLoanableAmount, totalPrincipalAfterLoan, null, null);
    }

    /** 拒绝结果工厂：放款额为 0，拒绝原因携错误码枚举。 */
    public static PolicyLoanResult rejected(MaintenanceErrorCode errorCode, String rejectReason,
                                            BigDecimal maxLoanableAmount, BigDecimal totalPrincipalAfterLoan) {
        return new PolicyLoanResult(false, BigDecimal.ZERO, maxLoanableAmount, totalPrincipalAfterLoan,
                rejectReason, errorCode);
    }
}
