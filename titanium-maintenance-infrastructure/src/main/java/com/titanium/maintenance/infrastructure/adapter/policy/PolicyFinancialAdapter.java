package com.titanium.maintenance.infrastructure.adapter.policy;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.common.exception.PolicyFinancialUnavailableException;
import com.titanium.maintenance.infrastructure.client.policy.PolicyFinancialClient;
import com.titanium.maintenance.port.policy.PolicyFinancialPort;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.response.policy.PolicyFinancialResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单财务快照适配器（六边形架构 driven adapter，g04-02）。
 * <p>
 * {@link PolicyFinancialPort} 的基础设施实现，调用保单域 {@link PolicyFinancialClient}（Feign），
 * 将远程响应防腐翻译为领域侧类型化的 {@link PolicyFinancialData}。
 * </p>
 * <p>
 * 🔴 <b>失败关闭三分面</b>（D13 范式，禁止以 0 兜底）：
 * </p>
 * <ul>
 * <li><b>不可达</b>：调用<b>抛异常</b>（连接失败、超时、5xx、契约反序列化失败等）—— 一律抛
 * {@link MaintenanceRemoteCallException}（错误码 {@code MAINTENANCE_POLICY_REMOTE_ERROR}，Web 层
 * 映射 HTTP 502）；</li>
 * <li><b>取数失败</b>：保单域<b>正常应答</b>但业务码非成功（保单不存在 / 财务要素缺失，下游
 * 失败关闭语义）或应答体无效 —— 抛 {@link PolicyFinancialUnavailableException}
 * （{@code MAINTENANCE_POLICY_FINANCIAL_UNAVAILABLE}），由调用方按业务规则失败关闭；</li>
 * <li><b>回显不一致</b>：应答保单号与请求不符 —— 契约无效，同「取数失败」处置。租户隔离由
 * Feign 请求头透传 + 保单域聚合校验保证（响应契约不含租户字段，无从回显校验）。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyFinancialAdapter implements PolicyFinancialPort {

    private final PolicyFinancialClient client;

    @Override
    public PolicyFinancialData getFinancialSnapshot(String policyId, String tenantId) {
        ApiResponse<PolicyFinancialResponse> response;
        try {
            response = client.getFinancialSnapshot(policyId);
        } catch (RuntimeException exception) {
            log.warn("保单财务取数失败(服务不可达), policyId={}, 原因={}", policyId, exception.getMessage());
            throw new MaintenanceRemoteCallException(
                    "保单域财务快照查询失败(服务不可达), policyId=" + policyId,
                    MaintenanceErrorCode.MAINTENANCE_POLICY_REMOTE_ERROR, exception);
        }
        PolicyFinancialResponse data = requireSuccessfulResponse(response, policyId);
        validateEcho(policyId, data);
        return new PolicyFinancialData(
                data.policyId(),
                data.cashValue(),
                data.sumInsured(),
                data.mainSegmentId(),
                data.loanOutstandingPrincipal(),
                data.loanOutstandingInterest(),
                data.arrearsAmount(),
                data.dividendBalance(),
                data.accountValue(),
                data.effectiveDate(),
                data.paidInstallments(),
                data.issuanceBizNo(),
                data.annualPremium(),
                data.coverageEndDate(),
                data.premiumPaidUp());
    }

    /** 保单域正常应答但业务码失败/缺数据 → 「取数失败」，与「不可达」严格区分。 */
    private PolicyFinancialResponse requireSuccessfulResponse(
            ApiResponse<PolicyFinancialResponse> response, String policyId) {
        if (response == null || !response.isSuccess() || response.getData() == null) {
            String message = response != null ? response.getMessage() : "无响应";
            throw new PolicyFinancialUnavailableException(
                    "保单财务取数失败(下游业务码失败), policyId=" + policyId + ", message=" + message);
        }
        return response.getData();
    }

    /** 应答回显校验：保单号与请求不符即契约无效，不做任何本地推断。 */
    private void validateEcho(String policyId, PolicyFinancialResponse data) {
        if (!Objects.equals(policyId, data.policyId())) {
            throw new PolicyFinancialUnavailableException(
                    "保单财务取数失败(应答回显不一致), policyId=" + policyId);
        }
    }
}
