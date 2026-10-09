package com.titanium.maintenance.application.model.effect;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 保单贷款还款指令（application 层编排入参，g04-04）。
 * <p>
 * 由生效编排调用方组装：目标贷款明细业务号与还款额为案件受理时冻结的申请要素（重试时经请求哈希锁定
 * 不变），还款日期为本次执行时点（幂等重试时以保单域首次记账为准，见 {@code LoanRepaymentOrchestrator}
 * 的 repaymentId 铁律）。
 * </p>
 *
 * @param tenantId 租户ID
 * @param maintenanceId 保全案件号（还款业务号的稳定派生源）
 * @param policyId 保单ID
 * @param customerId 付款客户ID（还款人，支付域收款单的对端客户）
 * @param operatorId 操作人（计费域入账事实落库操作人）
 * @param loanId 目标贷款明细业务号（业务事实，由发起方指名）
 * @param repaymentAmount 本次还款总额（> 0）
 * @param repaymentDate 还款日期（本次执行时点，保单域按首次记账回显）
 */
public record LoanRepaymentOrder(
        String tenantId,
        String maintenanceId,
        String policyId,
        String customerId,
        String operatorId,
        String loanId,
        BigDecimal repaymentAmount,
        LocalDateTime repaymentDate) {

    /**
     * 载荷幂等指纹：供生效请求冻结与重试时的原载荷校验使用。
     * <p>
     * 与 {@link LoanDisbursementOrder#requestPayloadHash()} 同规——摘要<b>不含</b>操作人与还款日期
     * （操作人不改变业务载荷；还款日期是执行时点元数据，重试漂移但业务载荷等价，保单域幂等回显以首次
     * 记账为准）。目标明细与还款额参与摘要：重试时二者任一变更即拒绝，防止同键异额/异明细串号。
     * </p>
     */
    public String requestPayloadHash() {
        StringBuilder canonical = new StringBuilder();
        append(canonical, tenantId);
        append(canonical, policyId);
        append(canonical, loanId);
        append(canonical, repaymentAmount.toPlainString());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
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
}
