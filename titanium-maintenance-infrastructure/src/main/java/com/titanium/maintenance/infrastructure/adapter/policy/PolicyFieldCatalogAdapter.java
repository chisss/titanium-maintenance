package com.titanium.maintenance.infrastructure.adapter.policy;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.titanium.maintenance.common.exception.PolicyFieldCatalogUnavailableException;
import com.titanium.maintenance.infrastructure.client.policy.PolicyFieldCatalogClient;
import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.response.fieldcatalog.PolicyFieldCapabilityResponse;
import com.titanium.policy.api.response.fieldcatalog.PolicyFieldCatalogResponse;
import com.titanium.policy.api.response.fieldcatalog.PolicyFieldDescriptorResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Policy 字段目录的 Maintenance 防腐层适配器。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyFieldCatalogAdapter implements PolicyFieldCatalogPort {

    private final PolicyFieldCatalogClient client;

    @Override
    public PolicyFieldCatalogEvidence getCatalog(PolicyFieldCatalogRequest request) {
        if (request == null) {
            throw new PolicyFieldCatalogUnavailableException("Policy字段目录查询请求不能为空");
        }
        try {
            ApiResponse<PolicyFieldCatalogResponse> response = client.getCurrentCatalog(
                    request.tenantId(), request.productType(), request.policyType(), request.businessDate());
            PolicyFieldCatalogResponse data = requireSuccessfulResponse(response);
            validateEcho(request, data);
            List<PolicyFieldDescriptorEvidence> fields = data.fields().stream().map(this::toEvidence).toList();
            return new PolicyFieldCatalogEvidence(
                    data.tenantId(),
                    data.productType(),
                    data.policyType(),
                    data.businessDate(),
                    data.catalogVersion(),
                    data.contentHash(),
                    fields);
        } catch (PolicyFieldCatalogUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("获取Policy字段目录失败, tenantId={}, productType={}, policyType={}, businessDate={}, errorType={}",
                    request.tenantId(), request.productType(), request.policyType(), request.businessDate(),
                    exception.getClass().getSimpleName());
            throw new PolicyFieldCatalogUnavailableException("Policy字段目录不可用或契约无效", exception);
        }
    }

    private PolicyFieldCatalogResponse requireSuccessfulResponse(
            ApiResponse<PolicyFieldCatalogResponse> response) {
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw new PolicyFieldCatalogUnavailableException("Policy字段目录服务未返回有效数据");
        }
        return response.getData();
    }

    private void validateEcho(PolicyFieldCatalogRequest request, PolicyFieldCatalogResponse response) {
        if (!Objects.equals(request.tenantId(), response.tenantId())
                || !Objects.equals(request.productType(), response.productType())
                || !Objects.equals(request.policyType(), response.policyType())
                || !Objects.equals(request.businessDate(), response.businessDate())) {
            throw new PolicyFieldCatalogUnavailableException("Policy字段目录查询条件回显不一致");
        }
    }

    private PolicyFieldDescriptorEvidence toEvidence(PolicyFieldDescriptorResponse field) {
        if (field == null) {
            throw new PolicyFieldCatalogUnavailableException("Policy字段目录包含空字段");
        }
        return new PolicyFieldDescriptorEvidence(
                field.fieldCode(),
                field.objectType(),
                field.valueType(),
                field.labelKey(),
                field.collection(),
                field.objectIdentityField(),
                toEvidence(field.capability()),
                field.sensitivity(),
                field.maskingPolicy(),
                field.deprecatedAt());
    }

    private PolicyFieldCapabilityEvidence toEvidence(PolicyFieldCapabilityResponse capability) {
        if (capability == null) {
            throw new PolicyFieldCatalogUnavailableException("Policy字段目录缺少字段能力");
        }
        return new PolicyFieldCapabilityEvidence(
                capability.readable(),
                capability.proposable(),
                capability.clearable(),
                capability.executionSupported(),
                capability.requiresObjectId(),
                capability.changeTypeCode());
    }
}
