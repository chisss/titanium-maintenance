package com.titanium.maintenance.infrastructure.client;

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
        /**
         * 是否生效：对齐保单域原生状态码 EFFECTIVE（此前误判 ACTIVE，保单域无此值）
         */
        public boolean isActive() {
            return "EFFECTIVE".equals(status);
        }

        /**
         * 是否为可复效的前置状态：仅失效(LAPSED)保单可复效。
         * 保单域复效状态机仅允许 LAPSED→EFFECTIVE；TERMINATED/EXPIRED 为终态不可复效，故不纳入。
         */
        public boolean isReinstatable() {
            return "LAPSED".equals(status);
        }
    }
}
