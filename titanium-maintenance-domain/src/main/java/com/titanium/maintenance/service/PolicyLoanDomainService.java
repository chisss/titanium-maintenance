package com.titanium.maintenance.service;

import com.titanium.maintenance.valueobject.PolicyLoanDetail;
import com.titanium.maintenance.valueobject.PolicyLoanResult;

/**
 * 保单贷款计算领域服务（纯领域服务）
 * <p>
 * 承载寿险长期险「以现金价值质押借款」的额度核定规则。遵循「三无」纪律——无 CommandGateway、
 * 无外部 Port、无基础设施依赖，入参/出参仅值对象，可脱离 Spring 容器以 {@code new} 直测。
 * 取数据（现金价值/可贷比例来源）与发命令、回写保单由应用层编排。
 * </p>
 * <p>
 * 🔴 减额缴清<b>不在此服务内</b>：其新保额核定的唯一权威是值对象 {@code ReducedPaidUpDetail}
 * （{@code reducedSumInsured()} 与 {@code isReductionValid()}），取数+核定+拒绝的时序编排落在
 * application 层 {@code ReducedPaidUpOrchestrator}。曾有的 {@code calculateReducedPaidUp} 是
 * 「以值对象为唯一入参、仅转发一行」的直通壳（属贫血伪装），已随 g04-05 出口落地删除。
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
}
