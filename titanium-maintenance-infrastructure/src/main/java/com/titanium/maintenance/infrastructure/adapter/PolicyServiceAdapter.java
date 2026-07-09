package com.titanium.maintenance.infrastructure.adapter;

import org.springframework.stereotype.Component;

import com.titanium.maintenance.infrastructure.client.PolicyServiceClient;
import com.titanium.maintenance.port.PolicyServicePort;

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
public class PolicyServiceAdapter implements PolicyServicePort {

    private final PolicyServiceClient policyServiceClient;

    public PolicyServiceAdapter(PolicyServiceClient policyServiceClient) {
        this.policyServiceClient = policyServiceClient;
    }

    @Override
    public boolean policyExists(String policyId, String tenantId) {
        try {
            policyServiceClient.getPolicyById(policyId, tenantId);
            return true;
        } catch (Exception e) {
            log.warn("校验保单存在失败, policyId={}, 原因={}", policyId, e.getMessage());
            return false;
        }
    }

    @Override
    public PolicyStatusSnapshot getPolicyStatus(String policyId, String tenantId) {
        PolicyServiceClient.PolicyStatusResponse response = policyServiceClient.getPolicyStatus(policyId, tenantId);
        return new PolicyStatusSnapshot(response.isActive(), response.isReinstatable());
    }
}
