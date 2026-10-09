package com.titanium.maintenance.valueobject.workflow;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/**
 * 保单贷款放款权威回执（贷款类保全案件生效出口的产物，g04-03）。
 * <p>
 * 与 {@link MaintenanceInvestmentSwitchEvidence} 并列的第三种生效回执：放款不改保单状态与字段，只在保单域
 * 贷款账户增加放款明细，因此不产生批单号、不推进保单版本、不携带应用字段——独立出口事实，不得塞进保单
 * 回执模型（后者以「实际版本必须递增」「字段型回执必须含实际字段」为不变量）。
 * </p>
 * <p>
 * 字段分两类：<b>请求侧</b>（贷款业务号、放款本金、冻结利率、放款日期）与<b>对端权威侧</b>（保单域回执回显
 * 要素、账务入账流水号、支付出账指令号）。未偿余额等派生值<b>不在此复算</b>——余额权威在保单域贷款账户聚合。
 * </p>
 */
public record MaintenanceLoanDisbursementEvidence(
        String        requestId,
        String        loanId,
        String        policyId,
        BigDecimal    loanAmount,
        BigDecimal    interestRate,
        LocalDateTime loanDate,
        String        billingPostingId,
        String        paymentOrderId,
        LocalDateTime disbursedAt) {

    public MaintenanceLoanDisbursementEvidence {
        requestId = requireText("requestId", requestId);
        loanId = requireText("loanId", loanId);
        policyId = requireText("policyId", policyId);
        loanAmount = requirePositive("loanAmount", loanAmount);
        interestRate = requireInUnitInterval("interestRate", interestRate);
        if (loanDate == null || disbursedAt == null) {
            throw invalid("loanDate", "放款日期与回执时间不能为空");
        }
        billingPostingId = requireText("billingPostingId", billingPostingId);
        paymentOrderId = requireText("paymentOrderId", paymentOrderId);
    }

    /**
     * 回执版本：放款不推进保单版本，以生效请求号作为版本标识，保证与案件级请求证据同源可勾稽
     * （与 {@link MaintenanceInvestmentSwitchEvidence#evidenceVersion()} 同规）。
     */
    public String evidenceVersion() {
        return requestId;
    }

    /**
     * 载荷幂等指纹：供工作流操作载荷摘要与重试去重使用。
     * <p>
     * 🔴 {@code disbursedAt} <b>不参与</b>摘要——它是本域收到回执的时刻，响应丢失后原载荷重试会取到新时刻，
     * 纳入摘要将使同一笔放款在重试时得到不同指纹、幂等失效（与 {@link MaintenanceInvestmentSwitchEvidence#contentHash()}
     * 同规）。对端回执字段（loanId/policyId/金额/利率/日期/入账号/出账号）与请求侧字段全部参与。
     * </p>
     */
    public String contentHash() {
        StringBuilder canonical = new StringBuilder();
        append(canonical, requestId);
        append(canonical, loanId);
        append(canonical, policyId);
        append(canonical, loanAmount.toPlainString());
        append(canonical, interestRate.toPlainString());
        append(canonical, loanDate.toString());
        append(canonical, billingPostingId);
        append(canonical, paymentOrderId);
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

    private static BigDecimal requireInUnitInterval(String field, BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw invalid(field, "必须落在 [0,1] 区间");
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
        return new MaintenanceValidationException("MaintenanceLoanDisbursementEvidence", field, message);
    }
}
