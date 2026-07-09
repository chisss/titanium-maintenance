package com.titanium.maintenance.infrastructure.adapter;

import org.springframework.stereotype.Component;

import com.titanium.maintenance.infrastructure.client.CustomerServiceClient;
import com.titanium.maintenance.port.CustomerServicePort;

import lombok.extern.slf4j.Slf4j;

/**
 * 客户服务适配器（六边形架构 driven adapter）
 * <p>
 * {@link CustomerServicePort} 的基础设施实现，调用客户域 {@link CustomerServiceClient}（Feign）， 把「调用异常」归一为「客户不存在」。
 * </p>
 */
@Slf4j
@Component
public class CustomerServiceAdapter implements CustomerServicePort {

    private final CustomerServiceClient customerServiceClient;

    public CustomerServiceAdapter(CustomerServiceClient customerServiceClient) {
        this.customerServiceClient = customerServiceClient;
    }

    @Override
    public boolean customerExists(String customerId, String tenantId) {
        try {
            customerServiceClient.getCustomerById(customerId, tenantId);
            return true;
        } catch (Exception e) {
            log.warn("校验客户存在失败, customerId={}, 原因={}", customerId, e.getMessage());
            return false;
        }
    }
}
