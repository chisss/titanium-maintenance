package com.titanium.maintenance.infrastructure.adapter.policy;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.titanium.maintenance.infrastructure.client.insurance.InsuranceServiceClient;
import com.titanium.maintenance.infrastructure.client.policy.PolicyServiceClient;
import com.titanium.maintenance.port.policy.PolicyServicePort;
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
 * {@link PolicyStatusSnapshot}，并把「调用异常」归一为「不存在/状态未知」。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyServiceAdapter implements PolicyServicePort {

    private final PolicyServiceClient policyServiceClient;
    private final InsuranceServiceClient insuranceServiceClient;

    @Override
    public boolean policyExists(String policyId, String tenantId) {
        try {
            ApiResponse<PolicyResponse> response = policyServiceClient.getPolicyById(policyId, tenantId);
            return validPolicy(response, tenantId);
        } catch (Exception e) {
            log.warn("校验保单存在失败, policyId={}, 原因={}", policyId, e.getMessage());
            return false;
        }
    }

    @Override
    public String getPolicyProductId(String policyId, String tenantId) {
        try {
            ApiResponse<PolicyResponse> response = policyServiceClient.getPolicyById(policyId, tenantId);
            return validPolicy(response, tenantId) ? response.getData().getProductId() : null;
        } catch (Exception e) {
            log.warn("获取保单产品失败, policyId={}, 原因={}", policyId, e.getMessage());
            return null;
        }
    }

    @Override
    public PolicyFinancialSnapshot getPolicyFinancialSnapshot(String policyId, String tenantId) {
        try {
            ApiResponse<PolicyResponse> response = policyServiceClient.getPolicyById(policyId, tenantId);
            if (!validPolicy(response, tenantId)) {
                return null;
            }
            PolicyResponse policy = response.getData();
            ApiResponse<InsuranceResponse> insuranceResponse = insuranceServiceClient.getInsurance(
                    policy.getApplicationId(), tenantId);
            if (!validInsurance(insuranceResponse, policy, tenantId)
                    || policy.getEffectiveDate() == null || policy.getPremium() == null
                    || policy.getPremium().getValue() == null || policy.getPremium().getCurrency() == null) {
                return null;
            }
            return new PolicyFinancialSnapshot(
                    policy.getProductId(), insuranceResponse.getData().getBizNo(),
                    policy.getEffectiveDate().toLocalDate(),
                    policy.getPremium().getValue(), policy.getPremium().getCurrency());
        } catch (Exception exception) {
            log.warn("获取保单财务日期快照失败, policyId={}, 原因={}", policyId, exception.getMessage());
            return null;
        }
    }

    @Override
    public PolicyStatusSnapshot getPolicyStatus(String policyId, String tenantId) {
        ApiResponse<PolicyStatusResponse> response = policyServiceClient.getPolicyStatus(policyId, tenantId);
        if (response == null || !response.isSuccess() || response.getData() == null) {
            return new PolicyStatusSnapshot(false, false);
        }
        String status = response.getData().getStatus();
        return new PolicyStatusSnapshot("EFFECTIVE".equals(status), "LAPSED".equals(status));
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
