package com.titanium.maintenance.valueobject.workflow;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/**
 * 投资账户转换权威回执（账户转换类保全案件生效出口的产物）
 * <p>
 * 与 {@link MaintenancePolicyApplicationEvidence} 并列的第二种生效回执：账户转换只变换投资标的与持有单位，
 * <b>不改保单状态、不改保单字段</b>（价值守恒），因此不产生批单号、不推进保单版本、不携带应用字段——它是一个
 * 独立的出口事实，不得塞进保单回执模型（后者以「实际版本必须递增」「字段型回执必须含实际字段」为不变量）。
 * </p>
 * <p>
 * 字段分两类：<b>请求侧</b>（转出单位、目标净值、目标标的）与<b>对端权威侧</b>（账户标识、转换后净值、转换后
 * 持有单位、转换后账户价值、币种）。转换明细（转入单位数、转换价值）<b>不在此复算</b>——对端契约按价值守恒在
 * 聚合内计算，本域重算只会制造第二份可能与权威不一致的账。
 * </p>
 */
public record MaintenanceInvestmentSwitchEvidence(
        String        requestId,
        String        accountId,
        BigDecimal    switchOutUnits,
        BigDecimal    targetUnitPrice,
        String        targetFund,
        BigDecimal    unitPrice,
        BigDecimal    totalUnits,
        BigDecimal    accountValue,
        String        currency,
        LocalDateTime switchedAt) {

    public MaintenanceInvestmentSwitchEvidence {
        requestId = requireText("requestId", requestId);
        accountId = requireText("accountId", accountId);
        targetFund = requireText("targetFund", targetFund);
        currency = requireText("currency", currency);
        switchOutUnits = requirePositive("switchOutUnits", switchOutUnits);
        targetUnitPrice = requirePositive("targetUnitPrice", targetUnitPrice);
        unitPrice = requirePositive("unitPrice", unitPrice);
        totalUnits = requireNonNegative("totalUnits", totalUnits);
        accountValue = requireNonNegative("accountValue", accountValue);
        if (switchedAt == null) {
            throw invalid("switchedAt", "账户转换时间不能为空");
        }
    }

    /**
     * 回执版本：投资账户转换不推进保单版本，故以生效请求号作为版本标识，保证与案件级请求证据同源可勾稽。
     */
    public String evidenceVersion() {
        return requestId;
    }

    /**
     * 载荷幂等指纹：供工作流操作载荷摘要与重试去重使用。
     * <p>
     * 🔴 {@code switchedAt} <b>不参与</b>摘要——它是本域收到回执的时刻，响应丢失后原载荷重试会取到新时刻，
     * 纳入摘要将使同一笔转换在重试时得到不同指纹、幂等失效（与 {@code MaintenanceDocumentEvidence.contentHash()}
     * 同规）。
     * </p>
     */
    public String contentHash() {
        StringBuilder canonical = new StringBuilder();
        append(canonical, requestId);
        append(canonical, accountId);
        append(canonical, switchOutUnits.toPlainString());
        append(canonical, targetUnitPrice.toPlainString());
        append(canonical, targetFund);
        append(canonical, unitPrice.toPlainString());
        append(canonical, totalUnits.toPlainString());
        append(canonical, accountValue.toPlainString());
        append(canonical, currency);
        return sha256(canonical.toString());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK缺少SHA-256实现", exception);
        }
    }

    private static void append(StringBuilder target, String value) {
        target.append(value == null ? -1 : value.length()).append(':');
        if (value != null) {
            target.append(value);
        }
        target.append('\n');
    }

    private static BigDecimal requirePositive(String field, BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw invalid(field, "必须大于0");
        }
        return value;
    }

    private static BigDecimal requireNonNegative(String field, BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw invalid(field, "不能为负数");
        }
        return value;
    }

    private static String requireText(String field, String value) {
        if (value == null || value.isBlank()) {
            throw invalid(field, "字段不能为空");
        }
        return value.trim();
    }

    private static MaintenanceValidationException invalid(String field, String message) {
        return new MaintenanceValidationException("MaintenanceInvestmentSwitchEvidence", field, message);
    }
}
