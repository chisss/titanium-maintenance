package com.titanium.maintenance.port;

/**
 * 客户服务端口（出口/driven port）
 * <p>
 * 应用侧表达对客户域的能力需求：校验客户存在。具体的跨微服务调用（Feign）由基础设施层 {@code infrastructure.adapter}
 * 的适配器实现，领域侧不依赖远程响应类型（防腐）。
 * </p>
 */
public interface CustomerServicePort {

    /**
     * 校验客户是否存在
     *
     * @param customerId 客户ID
     * @param tenantId 租户ID
     * @return 存在返回 true，否则 false
     */
    boolean customerExists(String customerId, String tenantId);
}
