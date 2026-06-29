package com.titanium.maintenance.api.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import com.titanium.maintenance.api.dto.ChangeMaintenanceStatusRequest;
import com.titanium.maintenance.api.dto.CreateMaintenanceRequest;
import com.titanium.maintenance.api.dto.MaintenanceResponse;

@FeignClient(name = "titanium-maintenance", path = "/api/v1/maintenances")
public interface MaintenanceApiClient {
    @PostMapping
    String createMaintenance(@RequestBody CreateMaintenanceRequest request,
                           @RequestHeader("X-Tenant-ID") String tenantId);

    @PutMapping("/{id}/status")
    String changeMaintenanceStatus(@PathVariable("id") String id,
                                 @RequestBody ChangeMaintenanceStatusRequest request,
                                 @RequestHeader("X-Tenant-ID") String tenantId);

    @GetMapping("/{id}")
    MaintenanceResponse getMaintenanceById(@PathVariable("id") String id,
                                         @RequestHeader("X-Tenant-ID") String tenantId);

    @GetMapping("/policy/{policyId}")
    List<MaintenanceResponse> getMaintenancesByPolicyId(@PathVariable("policyId") String policyId,
                                                      @RequestHeader("X-Tenant-ID") String tenantId);

    @GetMapping("/customer/{customerId}")
    List<MaintenanceResponse> getMaintenancesByCustomerId(@PathVariable("customerId") String customerId,
                                                        @RequestHeader("X-Tenant-ID") String tenantId);
}
