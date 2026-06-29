package com.titanium.maintenance.client;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "titanium-policy", path = "/api/v1/policies")
public interface PolicyServiceClient {
    @GetMapping("/{id}")
    PolicyResponse getPolicyById(@PathVariable("id") String id,
                                @RequestHeader("X-Tenant-ID") String tenantId);

    @GetMapping("/{id}/status")
    PolicyStatusResponse getPolicyStatus(@PathVariable("id") String id,
                                        @RequestHeader("X-Tenant-ID") String tenantId);

    // 内部类用于响应
    record PolicyResponse(
            String id,
            String policyNumber,
            String customerId,
            String productId,
            LocalDateTime startDate,
            LocalDateTime endDate,
            BigDecimal sumInsured,
            String status,
            LocalDateTime createdAt,
            String createdBy
    ) {}

    record PolicyStatusResponse(
            String id,
            String status
    ) {
        public boolean isActive() {
            return "ACTIVE".equals(status);
        }

        /**
         * 是否已失效（可复效的前置状态）
         */
        public boolean isTerminated() {
            return "TERMINATED".equals(status) || "LAPSED".equals(status) || "EXPIRED".equals(status);
        }
    }
}
