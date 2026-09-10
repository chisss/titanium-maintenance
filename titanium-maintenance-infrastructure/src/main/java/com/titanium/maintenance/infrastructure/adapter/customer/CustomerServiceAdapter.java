package com.titanium.maintenance.infrastructure.adapter.customer;

import org.springframework.stereotype.Component;

import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.infrastructure.client.customer.CustomerServiceClient;
import com.titanium.maintenance.port.customer.CustomerServicePort;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 客户服务适配器（六边形架构 driven adapter）
 * <p>
 * {@link CustomerServicePort} 的基础设施实现，调用客户域 {@link CustomerServiceClient}（Feign）。
 * 存在性校验改用专用 {@code /{customerId}/exists} 端点，比"调 getCustomer、用异常判 false"更准确：
 * customer 域 getCustomer 找不到时返回 HTTP 200 + null body，旧逻辑会误判为"存在"。
 * </p>
 * <p>
 * 🔴 <b>「不可达」与「不存在」严格区分</b>（与 {@code PolicyServiceAdapter} 同规，D13）：
 * {@code /exists} 正常应答的 {@code false} 才是「客户不存在」；调用抛异常（连接失败、超时、5xx、
 * 契约反序列化失败）一律抛 {@link MaintenanceRemoteCallException}（错误码
 * {@code MAINTENANCE_CUSTOMER_REMOTE_ERROR}，Web 层映射 HTTP 502）， 禁止把客户服务故障伪装成「客户不存在」。
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
        } catch (Exception exception) {
            log.warn("客户域远程调用失败(服务不可达), action=客户存在性校验, customerId={}, 原因={}", customerId,
                    exception.getMessage());
            throw new MaintenanceRemoteCallException(
                    "客户域客户存在性校验失败(服务不可达), customerId=" + customerId,
                    MaintenanceErrorCode.MAINTENANCE_CUSTOMER_REMOTE_ERROR, exception);
        }
    }
}
