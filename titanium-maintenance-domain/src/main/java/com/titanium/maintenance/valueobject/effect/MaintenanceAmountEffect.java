package com.titanium.maintenance.valueobject.effect;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Pattern;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/**
 * 金额型保全项的生效载荷：一笔收付差额（金额 + 方向 + 币种 + 来源凭证）。
 * <p>
 * 与「字段变更」「合同状态动作」<b>并列</b>的第三种生效载荷。纯金额类保全项（追加/减额缴费、追加投资、
 * 部分领取）既无字段目录入口，也不改合同状态，变更的实质就是这笔差额本身——故它必须作为一等载荷递到
 * 保单域，而不是靠闸口放行后什么也不带。
 * </p>
 * <p>
 * 🔴 <b>拒绝空交易是构造期不变量，不是调用方约定</b>：
 * </p>
 * <ul>
 *   <li>{@code amount} 必须<b>严格大于零</b>——零额交易在合同上不构成任何变更，放行等于写入一条
 *       「什么都没发生」的批单，下游（监管报送、对账）无从区分；</li>
 *   <li>{@code direction} 必须明确且<b>不得为 {@link MaintenanceBalanceDirection#NONE}</b>——
 *       「无余额影响」与「这是一笔收付」自相矛盾；</li>
 *   <li>{@code currency} 必须是三字母代码——跨币种案件里缺失币种的金额不可入账；</li>
 *   <li>{@code sourceRef} 必须非空——差额须有<b>解释性来源</b>（Billing 入账凭证号或 Product 报价号），
 *       凭空出现的金额无法与人、与账勾稽（缺口 G05 将在此基础上承载差额明细）。</li>
 *   <li>{@code itemCode} 必须非空——金额本身不含语义，<b>「这笔钱因哪项保全而发生」只能由保全项给出</b>；
 *       保单域据它推导批改类型（加额缴费/减额缴费…），缺失则批单退化为人人相同的兜底类型。</li>
 * </ul>
 */
public record MaintenanceAmountEffect(
        String itemCode,
        BigDecimal amount,
        MaintenanceBalanceDirection direction,
        String currency,
        String sourceRef) {

    private static final Pattern CURRENCY_CODE = Pattern.compile("[A-Za-z]{3}");

    public MaintenanceAmountEffect {
        if (itemCode == null || itemCode.isBlank()) {
            throw new MaintenanceValidationException(
                    "MaintenanceAmountEffect", "itemCode", "金额型生效载荷必须指明所属保全项");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new MaintenanceValidationException(
                    "MaintenanceAmountEffect", "amount", "金额型生效载荷必须携带大于零的收付金额");
        }
        if (direction == null || direction == MaintenanceBalanceDirection.NONE) {
            throw new MaintenanceValidationException(
                    "MaintenanceAmountEffect", "direction", "金额型生效载荷必须给出明确的收付方向");
        }
        if (currency == null || !CURRENCY_CODE.matcher(currency.trim()).matches()) {
            throw new MaintenanceValidationException(
                    "MaintenanceAmountEffect", "currency", "金额型生效载荷的币种必须是三字母代码");
        }
        if (sourceRef == null || sourceRef.isBlank()) {
            throw new MaintenanceValidationException(
                    "MaintenanceAmountEffect", "sourceRef", "金额型生效载荷必须引用差额来源凭证");
        }
        currency = currency.trim().toUpperCase(Locale.ROOT);
        sourceRef = sourceRef.trim();
    }

    /** 收付方向为借记（客户应付增加，如追加缴费）。 */
    public boolean debit() {
        return direction == MaintenanceBalanceDirection.DEBIT;
    }
}
