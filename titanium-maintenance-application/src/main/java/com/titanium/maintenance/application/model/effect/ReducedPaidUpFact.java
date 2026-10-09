package com.titanium.maintenance.application.model.effect;

import java.math.BigDecimal;
import java.util.List;

import com.titanium.maintenance.common.enums.change.PolicyFieldDataType;
import com.titanium.maintenance.port.policy.PolicyFinancialPort.PolicyFinancialData;
import com.titanium.maintenance.port.policy.PolicyMaintenanceApplicationPort.FieldChange;
import com.titanium.maintenance.valueobject.ReducedPaidUpDetail;

/**
 * 减额缴清核定事实（application 层编排出参，g04-05）。
 * <p>
 * 「当前现金价值 + 原保额 + 趸缴净费率 → 新保额」的核定结果，全部要素取自保单域财务快照与产品域
 * 费率策略（本域不臆造任何金额）。本事实的<b>唯一产物是两条保单字段变更</b>——主险段保额改为新保额、
 * 缴费方式改为趸缴——由生效编排交给标准 Policy 生效出口回写。
 * </p>
 * <p>
 * 🔴 与贷款/账户转换出口的形态差异：那两个出口产生的是<b>本域之外的新事实</b>（放款、还款、账户转换），
 * 故需专用回执命令落库；减额缴清<b>只改合同自身要素</b>，产物即 {@code ApplicationRequest} 的字段变更，
 * 权威回执由既有的 {@code RecordMaintenanceCasePolicyApplicationCommand} 承载，
 * <b>不新增业务凭证、不新增回执证据</b>。
 * </p>
 */
public record ReducedPaidUpFact(
        String policyId,
        String mainSegmentId,
        BigDecimal cashValue,
        BigDecimal originalSumInsured,
        BigDecimal netSinglePremiumRatePerUnit,
        BigDecimal newSumInsured) {

    /** 主险段保额字段码（policy 域字段目录；执行器要求 objectId 为主险段 {@code policyProductId}）。 */
    private static final String SUM_INSURED_FIELD_CODE = "policy.coverage.sumInsured";
    /** 缴费方式字段码（policy 域字段目录；执行器要求 objectId 为保单ID）。 */
    private static final String PAYMENT_METHOD_FIELD_CODE = "policy.payment.method";
    /**
     * 趸缴缴费方式取值（policy 域 {@code PremiumPaymentMethod.SINGLE_PAYMENT} 的 code）。
     * <p>
     * 🔴 权威定义在保单域 policy-common，跨域不可 import，故按跨域契约常量承载（同
     * {@code LoanRepaymentOrchestrator.COLLECTION_DESCRIPTION} 先例）；取值经 policy 域字段执行器
     * {@code BaseEnum.fromCode} 解析，保单域改名即跨域失配，须同批改此处。
     * </p>
     */
    private static final String SINGLE_PAYMENT_METHOD_CODE = "SINGLE_PAYMENT";

    /**
     * 由保单财务快照与已核定明细装配事实。
     *
     * @param financial 保单域财务快照（提供保单ID与主险段ID）
     * @param detail 已通过「减额自洽性」校验的明细（新保额取 {@link ReducedPaidUpDetail#reducedSumInsured()}，
     *               此处不重复推导，避免出现第二个计算口径）
     */
    public static ReducedPaidUpFact of(PolicyFinancialData financial, ReducedPaidUpDetail detail) {
        return new ReducedPaidUpFact(
                financial.policyId(), financial.mainSegmentId(),
                detail.cashValue(), detail.originalSumInsured(),
                detail.netSinglePremiumRatePerUnit(), detail.reducedSumInsured());
    }

    /**
     * 翻译为提交给 Policy 域的两条字段变更。
     * <p>
     * 🔴 objectId 是两个字段的强约束，取错即被保单域字段执行器拒绝：
     * 保额取<b>主险段标识</b>（{@code policyProductId}，非保单ID），缴费方式取<b>保单ID</b>。
     * 🔴 itemCode 取<b>案件生效任务</b>的真实保全项编码（调用方传入），本地不硬编码——
     * 保单域以 itemCode 承载保全项归属，自报一个不存在的项码会污染回执与下游投影。
     * </p>
     *
     * @param itemCode 案件生效任务的保全项编码
     * @return 字段变更序列（顺序固定：先保额，后缴费方式）
     */
    public List<FieldChange> toFieldChanges(String itemCode) {
        return List.of(
                new FieldChange(itemCode, mainSegmentId, SUM_INSURED_FIELD_CODE,
                        PolicyFieldDataType.DECIMAL.getCode(), newSumInsured.toPlainString()),
                new FieldChange(itemCode, policyId, PAYMENT_METHOD_FIELD_CODE,
                        PolicyFieldDataType.ENUM.getCode(), SINGLE_PAYMENT_METHOD_CODE));
    }
}
