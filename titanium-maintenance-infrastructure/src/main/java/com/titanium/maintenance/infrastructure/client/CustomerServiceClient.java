package com.titanium.maintenance.infrastructure.client;

import java.time.LocalDateTime;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "titanium-customer", path = "/api/v1/customers")
public interface CustomerServiceClient {
    @GetMapping("/{id}")
    CustomerResponse getCustomerById(@PathVariable("id") String id,
                                   @RequestHeader("X-Tenant-ID") String tenantId);

    // 内部类用于响应
    record CustomerResponse(
            String id,
            String customerNumber,
            String name,
            String idType,
            String idNumber,
            String phoneNumber,
            String email,
            String address,
            String status,
            LocalDateTime createdAt,
            String createdBy
    ) {}
}
