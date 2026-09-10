package com.titanium.maintenance.infrastructure.adapter.policy;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.infrastructure.client.insurance.InsuranceServiceClient;
import com.titanium.maintenance.infrastructure.client.policy.PolicyServiceClient;
import com.titanium.maintenance.port.policy.PolicyServicePort;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.response.InsuranceResponse;
import com.titanium.policy.api.response.PolicyResponse;
import com.titanium.policy.api.response.PolicyStatusResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单服务适配器（六边形架构 driven adapter）
 * <p>
 * {@link PolicyServicePort} 的基础设施实现，调用保单域 {@link PolicyServiceClient}（Feign）， 将远程响应翻译为领域侧类型化的
 * {@link PolicyStatusSnapshot}。
 * </p>
 * <p>
 * 🔴 <b>「不可达」与「不存在」严格区分</b>（D13 修复）：
 * </p>
 * <ul>
 * <li><b>不存在 / 事实不可用</b>：保单域<b>正常应答</b>但业务码非成功、数据缺失或租户回显不一致 —— 返回
 * {@code false} / {@code null}，由调用方按业务规则处理（如抛「保单不存在」）；</li>
 * <li><b>不可达</b>：调用<b>抛异常</b>（连接失败、超时、5xx、契约反序列化失败等）—— 一律抛
 * {@link MaintenanceRemoteCallException}（错误码 {@code MAINTENANCE_POLICY_REMOTE_ERROR}，Web 层映射 HTTP
 * 502）。<b>禁止再返回 {@code false} / {@code null}</b>，否则「保单服务宕机」被伪装成「保单不存在」，
 * 既造成业务误判，也把真实故障掩盖成数据问题。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyServiceAdapter implements PolicyServicePort {

    private final PolicyServiceClient policyServiceClient;
    private final InsuranceServiceClient insuranceServiceClient;

    @Override
    public boolean policyExists(String policyId, String tenantId) {
        return validPolicy(loadPolicy(policyId, tenantId), tenantId);
    }

    @Override
    public String getPolicyProductId(String policyId, String tenantId) {
        ApiResponse<PolicyResponse> response = loadPolicy(policyId, tenantId);
        return validPolicy(response, tenantId) ? response.getData().getProductId() : null;
    }

    @Override
    public PolicyFinancialSnapshot getPolicyFinancialSnapshot(String policyId, String tenantId) {
        ApiResponse<PolicyResponse> response = loadPolicy(policyId, tenantId);
        if (!validPolicy(response, tenantId)) {
            return null;
        }
        PolicyResponse policy = response.getData();
        ApiResponse<InsuranceResponse> insuranceResponse;
        try {
            insuranceResponse = insuranceServiceClient.getInsurance(policy.getApplicationId(), tenantId);
        } catch (Exception exception) {
            throw policyServiceUnreachable("投保单查询", policyId, exception);
        }
        if (!validInsurance(insuranceResponse, policy, tenantId)
                || policy.getEffectiveDate() == null || policy.getPremium() == null
                || policy.getPremium().getValue() == null || policy.getPremium().getCurrency() == null) {
            return null;
        }
        return new PolicyFinancialSnapshot(
                policy.getProductId(), insuranceResponse.getData().getBizNo(),
                policy.getEffectiveDate().toLocalDate(),
                policy.getPremium().getValue(), policy.getPremium().getCurrency());
    }

    @Override
    public PolicyStatusSnapshot getPolicyStatus(String policyId, String tenantId) {
        ApiResponse<PolicyStatusResponse> response;
        try {
            response = policyServiceClient.getPolicyStatus(policyId, tenantId);
        } catch (Exception exception) {
            throw policyServiceUnreachable("保单状态查询", policyId, exception);
        }
        if (response == null || !response.isSuccess() || response.getData() == null) {
            return new PolicyStatusSnapshot(false, false);
        }
        String status = response.getData().getStatus();
        return new PolicyStatusSnapshot("EFFECTIVE".equals(status), "LAPSED".equals(status));
    }

    /**
     * 查询保单详情。
     * <p>
     * 仅归一<b>技术失败</b>（抛异常 → 不可达）；业务语义上的「不存在」由应答体的业务码表达， 仍返回应答交
     * {@link #validPolicy} 判定，两者不得混同。
     * </p>
     */
    private ApiResponse<PolicyResponse> loadPolicy(String policyId, String tenantId) {
        try {
            return policyServiceClient.getPolicyById(policyId, tenantId);
        } catch (Exception exception) {
            throw policyServiceUnreachable("保单详情查询", policyId, exception);
        }
    }

    /** 构造「保单域不可达」异常（技术故障，区别于业务上的保单不存在）。 */
    private MaintenanceRemoteCallException policyServiceUnreachable(
            String action, String policyId, Exception cause) {
        log.warn("保单域远程调用失败(服务不可达), action={}, policyId={}, 原因={}", action, policyId, cause.getMessage());
        return new MaintenanceRemoteCallException(
                "保单域" + action + "失败(服务不可达), policyId=" + policyId,
                MaintenanceErrorCode.MAINTENANCE_POLICY_REMOTE_ERROR, cause);
    }

    private boolean validPolicy(ApiResponse<PolicyResponse> response, String tenantId) {
        return response != null
                && response.isSuccess()
                && response.getData() != null
                && Objects.equals(tenantId, response.getData().getTenantId());
    }

    private boolean validInsurance(
            ApiResponse<InsuranceResponse> response,
            PolicyResponse policy,
            String tenantId) {
        return response != null && response.isSuccess() && response.getData() != null
                && policy.getApplicationId() != null && !policy.getApplicationId().isBlank()
                && Objects.equals(policy.getApplicationId(), response.getData().getInsuranceId())
                && Objects.equals(policy.getProductId(), response.getData().getProductId())
                && Objects.equals(tenantId, response.getData().getTenantId())
                && response.getData().getBizNo() != null && !response.getData().getBizNo().isBlank();
    }
}
