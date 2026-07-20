package com.titanium.maintenance.infrastructure.adapter;

import org.springframework.stereotype.Component;

import com.titanium.maintenance.infrastructure.client.CustomerServiceClient;
import com.titanium.maintenance.port.CustomerServicePort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 客户服务适配器（六边形架构 driven adapter）
 * <p>
 * {@link CustomerServicePort} 的基础设施实现，调用客户域 {@link CustomerServiceClient}（Feign）。
 * 存在性校验改用专用 {@code /{customerId}/exists} 端点，比"调 getCustomer、用异常判 false"更准确：
 * customer 域 getCustomer 找不到时返回 HTTP 200 + null body，旧逻辑会误判为"存在"。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerServiceAdapter implements CustomerServicePort {

    private final CustomerServiceClient customerServiceClient;

    @Override
    public boolean customerExists(String customerId, String tenantId) {
        try {
            return customerServiceClient.isCustomerExists(customerId, tenantId);
        } catch (Exception e) {
            log.warn("校验客户存在失败, customerId={}, 原因={}", customerId, e.getMessage());
            return false;
        }
    }
}
