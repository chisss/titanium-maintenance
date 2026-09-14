package com.titanium.maintenance.application.model.effect;

import java.math.BigDecimal;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/**
 * 账户转换（FUND_SWITCH）生效请求的投资参数。
 * <p>
 * 🔴 <b>参数为何随生效请求传入、而非取自案件读模型</b>：实读确认本域<b>无每案投资参数承载</b>——
 * {@code MaintenanceItemDefinition} 是版本化配置（只有字段规则与流程，无每案参数）；字段变更表
 * {@code MaintenanceFieldChangeView} 经保单字段目录 {@code PolicyFieldCatalog} 权威校验（投资标的与转出单位
 * 不在保单字段目录内，无法经提案通路落库）；案件项视图只有「撤销」一族项目专属列（{@code withdrawal*}）。
 * 故投资参数由生效调用方当次提供，冻结进通用生效请求证据（{@code MaintenanceEffectRequestEvidence}）的载荷摘要，
 * 重试时凭摘要校验原载荷一致。
 * </p>
 * <p>
 * 连带约束：{@code applyScheduled}（计划调度）无调用方可传参，故账户转换类案件<b>不支持未来生效调度</b>，
 * 遇调度入口即拒绝（fail-closed，不静默以空参数执行）。
 * </p>
 *
 * @param switchOutUnits  转出单位数（按账户当前净值计价，须大于0）
 * @param targetUnitPrice 转入标的单位净值（须大于0）
 * @param targetCurrency  转入标的净金币种（可空，缺省 CNY，须与账户币种一致）
 * @param targetFund      转入标的标识
 */
public record MaintenanceInvestmentSwitchInput(
        BigDecimal switchOutUnits,
        BigDecimal targetUnitPrice,
        String targetCurrency,
        String targetFund) {

    public MaintenanceInvestmentSwitchInput {
        switchOutUnits = requirePositive("switchOutUnits", switchOutUnits);
        targetUnitPrice = requirePositive("targetUnitPrice", targetUnitPrice);
        targetCurrency = targetCurrency == null || targetCurrency.isBlank() ? null : targetCurrency.trim();
        if (targetFund == null || targetFund.isBlank()) {
            throw invalid("targetFund", "转入标的不能为空");
        }
        targetFund = targetFund.trim();
    }

    private static BigDecimal requirePositive(String field, BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw invalid(field, "必须大于0");
        }
        return value;
    }

    private static MaintenanceValidationException invalid(String field, String message) {
        return new MaintenanceValidationException("MaintenanceInvestmentSwitchInput", field, message);
    }
}
