package com.titanium.maintenance.infrastructure.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.request.maintenance.ApplyPolicyMaintenanceRequest;
import com.titanium.policy.api.response.PolicyEndorsementResponse;
import com.titanium.policy.api.response.PolicyMaintenanceSnapshotResponse;
import com.titanium.policy.api.response.PolicyResponse;
import com.titanium.policy.api.response.PolicyStatusResponse;
import com.titanium.policy.api.response.maintenance.PolicyMaintenanceApplicationResponse;

/** Policy 正式 API 的保全域 Feign 客户端，响应类型与 PolicyApi 保持一致。 */
@FeignClient(name = "titanium-policy", contextId = "maintenancePolicyClient", path = "/api/v1/policies")
public interface PolicyServiceClient {
    @PostMapping("/{id}/maintenance-applications")
    ApiResponse<PolicyMaintenanceApplicationResponse> applyMaintenance(
            @PathVariable("id") String id,
            @RequestBody ApplyPolicyMaintenanceRequest request,
            @RequestHeader("X-Operator-Id") String operatorId,
            @RequestHeader("X-Tenant-Id") String tenantId);

    @GetMapping("/{id}")
    ApiResponse<PolicyResponse> getPolicyById(@PathVariable("id") String id,
                                              @RequestHeader("X-Tenant-Id") String tenantId);

    @GetMapping("/{id}/status")
    ApiResponse<PolicyStatusResponse> getPolicyStatus(@PathVariable("id") String id,
                                                      @RequestHeader("X-Tenant-Id") String tenantId);

    @GetMapping("/{id}/maintenance-snapshot")
    ApiResponse<PolicyMaintenanceSnapshotResponse> getMaintenanceSnapshot(
            @PathVariable("id") String id,
            @RequestHeader("X-Tenant-Id") String tenantId);

    @GetMapping("/{id}/endorsements")
    ApiResponse<List<PolicyEndorsementResponse>> getEndorsements(
            @PathVariable("id") String id,
            @RequestHeader("X-Tenant-Id") String tenantId);
}
