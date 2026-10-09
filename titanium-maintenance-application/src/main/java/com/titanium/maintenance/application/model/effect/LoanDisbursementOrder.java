package com.titanium.maintenance.application.model.effect;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 保单贷款放款指令（application 层编排入参，g04-03）。
 * <p>
 * 由生效编排调用方组装：金额为案件受理时冻结的申请额（重试时经请求哈希锁定不变），放款日期为本次
 * 执行时点（幂等重试时以保单域首次入账为准，见 {@code LoanDisbursementOrchestrator} 的 loanId 铁律）。
 * </p>
 *
 * @param tenantId 租户ID
 * @param maintenanceId 保全案件号（贷款明细业务号的稳定派生源）
 * @param policyId 保单ID
 * @param productId 产品ID（查产品域已发布贷款策略）
 * @param customerId 收款客户ID（放款收款人）
 * @param operatorId 操作人（计费域入账事实落库操作人）
 * @param requestedAmount 申请贷款金额（> 0）
 * @param loanDate 放款日期（本次执行时点）
 */
public record LoanDisbursementOrder(
        String tenantId,
        String maintenanceId,
        String policyId,
        String productId,
        String customerId,
        String operatorId,
        BigDecimal requestedAmount,
        LocalDateTime loanDate) {

    /**
     * 载荷幂等指纹：供生效请求冻结与重试时的原载荷校验使用。
     * <p>
     * 与 Policy 出口 {@code ApplicationRequest.requestPayloadHash} 同规——摘要<b>不含</b>操作人与放款日期
     * （操作人不改变业务载荷；放款日期是执行时点元数据，重试漂移但业务载荷等价，保单域幂等回显以首次
     * 入账为准）。申请金额参与摘要：重试时金额变更即拒绝，防止同键异额串号。
     * </p>
     */
    public String requestPayloadHash() {
        StringBuilder canonical = new StringBuilder();
        append(canonical, tenantId);
        append(canonical, policyId);
        append(canonical, requestedAmount.toPlainString());
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
