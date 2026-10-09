package com.titanium.maintenance.valueobject.workflow;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/**
 * 保单贷款还款权威回执（贷款还款类保全案件生效出口的产物，g04-04）。
 * <p>
 * 与 {@link MaintenanceLoanDisbursementEvidence} 同族的第四种生效回执：还款不改保单状态与字段，只在保单域
 * 贷款账户冲减负债并记录收款，因此不产生批单号、不推进保单版本、不携带应用字段——独立出口事实，不得塞进
 * 保单回执模型（后者以「实际版本必须递增」「字段型回执必须含实际字段」为不变量）。
 * </p>
 * <p>
 * 🔴 <b>分配与余额一律取自保单域回执，本域不复算</b>：利息/本金拆分（先息后本、单利 365 分段计息）与
 * 还款后未偿余额的权威都在保单域贷款账户聚合——它持有该贷款的全量还款记录，重组案件的回执顺序与调用
 * 方所见顺序无关。本类型只承载与留痕，任何派生值都不在此计算。
 * </p>
 * <p>
 * 🔴 <b>两个时间语义不同、不可互替</b>：{@code repaymentDate} 是<b>保单域权威的还款日期</b>（本次计息
 * 区间终点，重试时回显首次值，参与载荷摘要）；{@code repaidAt} 是本域<b>收到回执的时刻</b>（重试会漂移，
 * 不参与摘要）。
 * </p>
 */
public record MaintenanceLoanRepaymentEvidence(
        String        requestId,
        String        repaymentId,
        String        loanId,
        String        policyId,
        BigDecimal    repaymentAmount,
        BigDecimal    interestPortion,
        BigDecimal    principalPortion,
        BigDecimal    accruedInterest,
        BigDecimal    outstandingPrincipalAfter,
        BigDecimal    outstandingInterestAfter,
        boolean       settled,
        LocalDateTime repaymentDate,
        String        billingPostingId,
        String        paymentOrderId,
        LocalDateTime repaidAt) {

    public MaintenanceLoanRepaymentEvidence {
        requestId = requireText("requestId", requestId);
        repaymentId = requireText("repaymentId", repaymentId);
        loanId = requireText("loanId", loanId);
        policyId = requireText("policyId", policyId);
        repaymentAmount = requirePositive("repaymentAmount", repaymentAmount);
        interestPortion = requireNonNegative("interestPortion", interestPortion);
        principalPortion = requireNonNegative("principalPortion", principalPortion);
        accruedInterest = requireNonNegative("accruedInterest", accruedInterest);
        outstandingPrincipalAfter = requireNonNegative("outstandingPrincipalAfter", outstandingPrincipalAfter);
        outstandingInterestAfter = requireNonNegative("outstandingInterestAfter", outstandingInterestAfter);
        if (interestPortion.add(principalPortion).compareTo(repaymentAmount) != 0) {
            throw invalid("principalPortion", "利息与本金拆分合计必须等于还款总额");
        }
        if (repaymentDate == null) {
            throw invalid("repaymentDate", "还款日期不能为空");
        }
        billingPostingId = requireText("billingPostingId", billingPostingId);
        paymentOrderId = requireText("paymentOrderId", paymentOrderId);
        if (repaidAt == null) {
            throw invalid("repaidAt", "回执时间不能为空");
        }
    }

    /**
     * 回执版本：还款不推进保单版本，以生效请求号作为版本标识，保证与案件级请求证据同源可勾稽
     * （与 {@link MaintenanceLoanDisbursementEvidence#evidenceVersion()} 同规）。
     */
    public String evidenceVersion() {
        return requestId;
    }

    /**
     * 载荷幂等指纹：供工作流操作载荷摘要与重试去重使用。
     * <p>
     * 🔴 {@code repaidAt} <b>不参与</b>摘要——它是本域收到回执的时刻，响应丢失后原载荷重试会取到新时刻，
     * 纳入摘要将使同一笔还款在重试时得到不同指纹、幂等失效（与 {@link MaintenanceLoanDisbursementEvidence#contentHash()}
     * 同规）。对端回执字段（还款号/贷款号/保单号/金额/分配/余额/结清标志/还款日期/两个回执号）全部参与。
     * </p>
     */
    public String contentHash() {
        StringBuilder canonical = new StringBuilder();
        append(canonical, requestId);
        append(canonical, repaymentId);
        append(canonical, loanId);
        append(canonical, policyId);
        append(canonical, repaymentAmount.toPlainString());
        append(canonical, interestPortion.toPlainString());
        append(canonical, principalPortion.toPlainString());
        append(canonical, accruedInterest.toPlainString());
        append(canonical, outstandingPrincipalAfter.toPlainString());
        append(canonical, outstandingInterestAfter.toPlainString());
        append(canonical, Boolean.toString(settled));
        append(canonical, repaymentDate.toString());
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
        return new MaintenanceValidationException("MaintenanceLoanRepaymentEvidence", field, message);
    }
}
