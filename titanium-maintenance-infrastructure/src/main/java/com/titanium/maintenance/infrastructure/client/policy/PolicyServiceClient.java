package com.titanium.maintenance.infrastructure.client.policy;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.request.maintenance.ApplyPolicyMaintenanceRequest;
import com.titanium.policy.api.response.maintenance.PolicyEndorsementResponse;
import com.titanium.policy.api.response.maintenance.PolicyMaintenanceApplicationResponse;
import com.titanium.policy.api.response.maintenance.PolicyMaintenanceSnapshotResponse;
import com.titanium.policy.api.response.policy.PolicyResponse;
import com.titanium.policy.api.response.policy.PolicyStatusResponse;

/** Policy 正式 API 的保全域 Feign 客户端，响应类型与 PolicyApi 保持一致。 */
@FeignClient(name = "titanium-policy", contextId = "maintenancePolicyClient", path = "/api/v1/policies")
public interface PolicyServiceClient {
    @PostMapping("/{id}/maintenance-applications")
    ApiResponse<PolicyMaintenanceApplicationResponse> applyMaintenance(
            @PathVariable String id,
            @RequestBody ApplyPolicyMaintenanceRequest request);

    @GetMapping("/{id}")
    ApiResponse<PolicyResponse> getPolicyById(@PathVariable String id);

    @GetMapping("/{id}/status")
    ApiResponse<PolicyStatusResponse> getPolicyStatus(@PathVariable String id);

    @GetMapping("/{id}/maintenance-snapshot")
    ApiResponse<PolicyMaintenanceSnapshotResponse> getMaintenanceSnapshot(
            @PathVariable String id);

    @GetMapping("/{id}/endorsements")
    ApiResponse<List<PolicyEndorsementResponse>> getEndorsements(
            @PathVariable String id);
}
