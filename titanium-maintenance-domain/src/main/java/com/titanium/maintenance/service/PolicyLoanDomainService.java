package com.titanium.maintenance.service;

import com.titanium.maintenance.valueobject.PolicyLoanDetail;
import com.titanium.maintenance.valueobject.PolicyLoanResult;
import com.titanium.maintenance.valueobject.ReducedPaidUpDetail;

/**
 * 保单贷款/减额缴清计算领域服务（纯领域服务）
 * <p>
 * 承载寿险长期险两类现金价值运用的业务规则：保单贷款（以现金价值质押借款）额度核定，
 * 减额缴清（以现金价值趸缴购买减额保单）新保额核定。遵循「三无」纪律——无 CommandGateway、
 * 无外部 Port、无基础设施依赖，入参/出参仅值对象，可脱离 Spring 容器以 {@code new} 直测。
 * 取数据（现金价值/费率来源）与发命令、回写保单由应用层编排。
 * </p>
 */
public interface PolicyLoanDomainService {

    /**
     * 核定保单贷款额度：申请额在可贷上限内则核准放款，否则拒绝。
     *
     * @param detail 保单贷款明细
     * @return 贷款计算结果
     */
    PolicyLoanResult evaluateLoan(PolicyLoanDetail detail);

    /**
     * 核定减额缴清后的新保额。
     *
     * @param detail 减额缴清明细
     * @return 减额后新保额
     */
    java.math.BigDecimal calculateReducedPaidUp(ReducedPaidUpDetail detail);
}
