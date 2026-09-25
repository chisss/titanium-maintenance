package com.titanium.maintenance.infrastructure.adapter.customer;

import org.springframework.stereotype.Component;

import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.infrastructure.client.customer.CustomerServiceClient;
import com.titanium.maintenance.port.customer.CustomerServicePort;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.response.ApiResponse;

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
 * {@code /exists} 正常应答且携带有效载荷的 {@code false} 才是「客户不存在」；调用抛异常（连接失败、超时、5xx、
 * 契约反序列化失败）、或 2xx + 错误信封 / 成功但无载荷，一律抛 {@link MaintenanceRemoteCallException}（错误码
 * {@code MAINTENANCE_CUSTOMER_REMOTE_ERROR}，Web 层映射 HTTP 502）， 禁止把客户服务故障伪装成「客户不存在」。
 * </p>
 * <p>
 * 🔴 m24-01a：customer 域契约统一返回 {@code ApiResponse} 信封，本适配器同步解包——只改外层成形，
 * 上述失败语义不变。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerServiceAdapter implements CustomerServicePort {

    private final CustomerServiceClient customerServiceClient;

    @Override
    public boolean customerExists(String customerId) {
        ApiResponse<Boolean> response;
        try {
            response = customerServiceClient.isCustomerExists(customerId);
        } catch (Exception exception) {
            log.warn("客户域远程调用失败(服务不可达), action=客户存在性校验, customerId={}, 原因={}", customerId,
                    exception.getMessage(), exception);
            throw new MaintenanceRemoteCallException(
                    "客户域客户存在性校验失败(服务不可达), customerId=" + customerId,
                    MaintenanceErrorCode.MAINTENANCE_CUSTOMER_REMOTE_ERROR, exception);
        }
        // m24-01a 信封化：无有效载荷（错误信封 / 成功但无 data）同样按「不可达」失败关闭——
        // D13 只承认「正常应答的 false」为「客户不存在」，绝不把依赖故障伪装成业务事实
        if (response == null || !response.isSuccess() || response.getData() == null) {
            log.warn("客户域应答无有效载荷, action=客户存在性校验, customerId={}, code={}, message={}", customerId,
                    response == null ? null : response.getCode(), response == null ? null : response.getMessage());
            throw new MaintenanceRemoteCallException(
                    "客户域客户存在性校验失败(应答无有效载荷), customerId=" + customerId,
                    MaintenanceErrorCode.MAINTENANCE_CUSTOMER_REMOTE_ERROR, null);
        }
        return response.getData();
    }
}
