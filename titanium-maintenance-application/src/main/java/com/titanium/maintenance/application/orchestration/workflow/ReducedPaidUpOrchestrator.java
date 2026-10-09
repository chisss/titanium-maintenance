package com.titanium.maintenance.application.orchestration.workflow;

import org.springframework.stereotype.Service;

import com.titanium.maintenance.application.model.effect.ReducedPaidUpFact;
import com.titanium.maintenance.application.model.effect.ReducedPaidUpOrder;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.port.policy.PolicyFinancialPort;
import com.titanium.maintenance.port.policy.PolicyFinancialPort.PolicyFinancialData;
import com.titanium.maintenance.port.product.ProductReducedPaidUpPolicyPort;
import com.titanium.maintenance.port.product.ProductReducedPaidUpPolicyPort.ReducedPaidUpPolicy;
import com.titanium.maintenance.valueobject.ReducedPaidUpDetail;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 减额缴清核定编排器（application 层同步编排，g04-05）。
 * <p>
 * 「取数 → 核定 → 产出字段变更」两步编排：<b>取数</b> = 保单域财务快照（现金价值、原保额、主险段标识）
 * + 产品域已发布趸缴净费率策略；<b>核定</b> = 委派 {@link ReducedPaidUpDetail}（新保额 =
 * 现金价值 ÷ 每元保额趸缴净费率，及其「新保额 ≤ 原保额」自洽性校验）。本类<b>不含任何费率或舍入规则</b>。
 * </p>
 * <p>
 * 🔴 为何是 application 编排器而非领域服务：本类依赖两个外部 Port（保单域取数、产品域取数）并做跨服务
 * 时序编排，按根规约 §3.4.4「领域服务禁止依赖任何 Port」必须落 application 层。曾有的
 * {@code PolicyLoanDomainService.calculateReducedPaidUp} 是「以值对象为唯一入参、仅转发一行」的直通壳，
 * 属贫血伪装，已随本出口落地一并删除。
 * </p>
 * <p>
 * 🔴 失败关闭（禁止任何兜底）：现金价值/原保额/主险段标识缺失由保单域失败关闭
 * （{@code PolicyFinancialUnavailableException}）；无已发布费率策略由产品域失败关闭
 * （{@code ProductReducedPaidUpPolicyUnavailableException}）；新保额超过原保额（减额不成立）由本类显式
 * 拒绝（{@code MAINTENANCE_REDUCED_PAID_UP_EXCEEDS_SUM_INSURED}，71007012）。三者在任何写入之前发生。
 * </p>
 * <p>
 * 🔴 派生字段变更<b>不过</b>本域字段目录能力校验（{@code validateExecutionCapabilities}）：该校验的输入是
 * 案件<b>提案</b>字段视图，而减额缴清案件结构上无字段提案（目录 {@code changeTypeCode} 值域不含该保全项）。
 * 可执行性的兜底在保单域字段执行器注册表——字段码未开放执行即以
 * {@code POLICY_MAINTENANCE_FIELD_NOT_EXECUTABLE} 失败关闭，不存在静默跳过。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReducedPaidUpOrchestrator {

    private final PolicyFinancialPort policyFinancialPort;
    private final ProductReducedPaidUpPolicyPort productReducedPaidUpPolicyPort;

    /**
     * 核定减额缴清：取保单财务快照与产品费率策略 → 计算并校验新保额 → 产出减额缴清事实。
     *
     * @param order 核定指令（保全案件号、保单ID、产品ID、业务时点）
     * @return 减额缴清事实（含两条待回写的保单字段变更）
     * @throws MaintenanceValidationException 新保额超过原保额（71007012，减额不成立）
     */
    public ReducedPaidUpFact calculate(ReducedPaidUpOrder order) {
        PolicyFinancialData financial =
                policyFinancialPort.getFinancialSnapshot(order.policyId(), order.tenantId());
        ReducedPaidUpPolicy ratePolicy = productReducedPaidUpPolicyPort
                .queryEffectiveReducedPaidUpPolicy(order.productId(), order.businessTime());
        ReducedPaidUpDetail detail = new ReducedPaidUpDetail(
                financial.cashValue(), financial.sumInsured(), ratePolicy.netSinglePremiumRatePerUnit());
        if (!detail.isReductionValid()) {
            throw new MaintenanceValidationException(
                    MaintenanceErrorCode.MAINTENANCE_REDUCED_PAID_UP_EXCEEDS_SUM_INSURED,
                    "ReducedPaidUpOrchestrator",
                    "减额缴清新保额超过原保额，不构成减额: maintenanceId=" + order.maintenanceId()
                            + ", policyId=" + order.policyId()
                            + ", 新保额=" + detail.reducedSumInsured().toPlainString()
                            + ", 原保额=" + detail.originalSumInsured().toPlainString());
        }
        log.info("减额缴清核定: maintenanceId={}, policyId={}, productId={}, 现金价值={}, 原保额={},"
                        + " 趸缴净费率={}, 费率策略={}, 新保额={}",
                order.maintenanceId(), order.policyId(), order.productId(),
                detail.cashValue().toPlainString(), detail.originalSumInsured().toPlainString(),
                detail.netSinglePremiumRatePerUnit().toPlainString(), ratePolicy.policyId(),
                detail.reducedSumInsured().toPlainString());
        return ReducedPaidUpFact.of(financial, detail);
    }
}
