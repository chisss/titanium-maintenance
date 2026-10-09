package com.titanium.maintenance.configuration.control;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 保全项额度控制引用键（g04-01）
 * <p>
 * 承载现金价值运用类保全的额度参数<b>引用键</b>——可贷比例/贷款利率、可领取比例、保额上下限
 * 等数值阈值统一由产品定价配置承载（R-1/R-3 裁示），本配置面只声明「引用哪个产品配置」，
 * 不含任何数值阈值字面量（AC-11：阈值单一来源 = 产品定价配置；G04/G09 边界：本类不引表达式
 * 引擎，规则表达式归 G09）。未配置时额度校验走领域值对象兜底不变量（PolicyLoanDetail /
 * WithdrawalQuota / SumInsuredBound）。
 * </p>
 *
 * @param loanPolicyReferenceCode 保单贷款定价配置引用键（可贷比例/贷款利率来源）
 * @param withdrawalPolicyReferenceCode 部分领取定价配置引用键（可领取比例来源）
 * @param sumInsuredBoundReferenceCode 保额上下限配置引用键（最低/最高保额来源）
 */
public record MaintenanceQuotaControls(String loanPolicyReferenceCode,
                                       String withdrawalPolicyReferenceCode,
                                       String sumInsuredBoundReferenceCode) {

    public MaintenanceQuotaControls {
        loanPolicyReferenceCode = normalize(loanPolicyReferenceCode);
        withdrawalPolicyReferenceCode = normalize(withdrawalPolicyReferenceCode);
        sumInsuredBoundReferenceCode = normalize(sumInsuredBoundReferenceCode);
    }

    /** 未配置额度引用键的空控制（走领域兜底不变量）。 */
    public static MaintenanceQuotaControls none() {
        return new MaintenanceQuotaControls(null, null, null);
    }

    /** 是否配置了任一额度引用键。 */
    // 🔴 @JsonIgnore 不可删（D-501-19）：派生 getter 会被 Axon 的 Jackson 序列化器写进事件载荷。
    @JsonIgnore
    public boolean isConfigured() {
        return loanPolicyReferenceCode != null || withdrawalPolicyReferenceCode != null
                || sumInsuredBoundReferenceCode != null;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
