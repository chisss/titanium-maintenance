package com.titanium.maintenance.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import com.titanium.customer.api.response.CustomerResponse;

/**
 * 客户域 Feign 客户端（防腐适配，路径与响应类型严格对齐 customer 域 CustomerApi 真实契约）
 * <p>
 * 路径 {@code /api/v1/customers}，与 customer 域 {@code CustomerApi} 保持一致。
 * 响应类型复用 {@code titanium-customer-api} 模块的 {@code CustomerResponse}，避免本域自维护镜像 record
 * 导致字段漂移（原自定义 {@code CustomerResponse} record 字段名与 customer 域真实响应不一致）。
 * </p>
 * <p>
 * 存在性校验优先走专用 {@code /{customerId}/exists} 端点，避免通过异常捕获判断是否存在的"错误即false"模式。
 * </p>
 */
@FeignClient(name = "titanium-customer", contextId = "maintenanceCustomerClient", path = "/api/v1/customers")
public interface CustomerServiceClient {

    /**
     * 获取客户详情
     *
     * @param customerId 客户ID
     * @param tenantId 租户ID
     * @return 客户详情 DTO，客户不存在时返回 null
     */
    @GetMapping("/{customerId}")
    CustomerResponse getCustomer(@PathVariable("customerId") String customerId,
                                    @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 判断客户是否存在（专用端点，比 getCustomer + 异常捕获更准确）
     *
     * @param customerId 客户ID
     * @param tenantId 租户ID
     * @return 存在返回 true
     */
    @GetMapping("/{customerId}/exists")
    boolean isCustomerExists(@PathVariable("customerId") String customerId,
                             @RequestHeader("X-Tenant-Id") String tenantId);
}
