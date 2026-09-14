package com.titanium.maintenance.port.investment;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 投资账户转换出口端口（六边形架构 driven port）
 * <p>
 * 保全生效环节中「账户转换（FUND_SWITCH）」类案件的执行出口：按<b>关联保单</b>定位投资账户并执行基金转换。
 * 账户为何按保单而非账户ID寻址——保全案件只持有保单标识，账户是保单在投资域的下游派生（一保单可有多个形态
 * 账户），故由适配器完成「保单 → 账户」的解析；解析结果不唯一时明确失败而非静默择一。
 * </p>
 * <p>
 * 🔴 <b>出口语义</b>：账户转换只变换投资标的与持有单位，<b>不改保单状态、不改保单字段</b>（价值守恒），
 * 因此本端口与 {@code port/policy/PolicyMaintenanceApplicationPort} 是两条互斥的生效出口，不得混用：
 * 前者产出投资账户回执，后者产出保单批单回执。
 * </p>
 * <p>
 * 🔴 <b>空值语义</b>：保单名下无可用投资账户时返回 {@code null}（对端未命中，原样返回，由调用方决定处置），
 * <b>不得</b>以抛异常表达「不存在」；而远程服务不可达、契约反序列化失败、对端业务拒绝一律 fail-closed 抛
 * {@code MaintenanceRemoteCallException}，禁止把服务故障伪装成「账户不存在」。
 * </p>
 */
public interface InvestmentAccountSwitchPort {

    /**
     * 按关联保单执行投资账户转换
     *
     * @param request 转换请求（保单、转出单位、目标净值与标的）
     * @return 转换后的账户权威事实；保单名下无可用账户时返回 {@code null}
     */
    InvestmentSwitchFact switchByPolicy(InvestmentSwitchRequest request);

    /**
     * 账户转换请求（本域自有入参，不暴露对端契约类型）
     *
     * @param tenantId        租户ID
     * @param policyId        关联保单ID（账户的寻址入口）
     * @param switchOutUnits  转出单位数（按账户当前净值计价）
     * @param targetUnitPrice 转入标的单位净值（须与账户币种一致）
     * @param targetCurrency  转入标的净金币种
     * @param targetFund      转入标的标识
     * @param operatorId      操作人
     */
    record InvestmentSwitchRequest(
            String tenantId,
            String policyId,
            BigDecimal switchOutUnits,
            BigDecimal targetUnitPrice,
            String targetCurrency,
            String targetFund,
            String operatorId) {

        /**
         * 载荷幂等指纹：供生效请求冻结与重试时的原载荷校验使用。
         * <p>
         * 与 Policy 出口 {@code ApplicationRequest.requestPayloadHash} 同规——摘要<b>不含</b>操作人（操作人不改变
         * 业务载荷），使同一笔转换换人重试仍命中同一指纹。
         * </p>
         */
        public String requestPayloadHash() {
            StringBuilder canonical = new StringBuilder();
            append(canonical, tenantId);
            append(canonical, policyId);
            append(canonical, switchOutUnits.toPlainString());
            append(canonical, targetUnitPrice.toPlainString());
            append(canonical, targetCurrency);
            append(canonical, targetFund);
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

    /**
     * 账户转换权威事实（字段全部取自对端应答，本域不本地重算转换明细）
     *
     * @param accountId    投资账户ID
     * @param unitPrice    转换后单位净值（等于转入标的净值）
     * @param totalUnits   转换后持有单位数
     * @param accountValue 转换后账户价值
     * @param currency     账户币种
     */
    record InvestmentSwitchFact(
            String accountId,
            BigDecimal unitPrice,
            BigDecimal totalUnits,
            BigDecimal accountValue,
            String currency) {
    }
}
