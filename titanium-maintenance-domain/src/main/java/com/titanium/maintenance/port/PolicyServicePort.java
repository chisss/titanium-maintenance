package com.titanium.maintenance.port;

/**
 * 保单服务端口（出口/driven port）
 * <p>
 * 领域/应用侧表达对保单域的能力需求：校验保单存在、获取保单状态快照。 具体的跨微服务调用（Feign）由基础设施层
 * {@code infrastructure.adapter} 的适配器实现， 领域侧不依赖任何远程响应类型（防腐）。
 * </p>
 */
public interface PolicyServicePort {

    /**
     * 校验保单是否存在
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 存在返回 true，否则 false
     */
    boolean policyExists(String policyId, String tenantId);

    /**
     * 获取保单状态快照
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 保单状态快照
     */
    PolicyStatusSnapshot getPolicyStatus(String policyId, String tenantId);

    /**
     * 保单状态快照（领域侧类型化视图，屏蔽远程响应细节）
     *
     * @param active 保单是否生效（对应保单域 EFFECTIVE）
     * @param reinstatable 保单是否处于可复效前置状态（仅 LAPSED）
     */
    record PolicyStatusSnapshot(boolean active, boolean reinstatable) {
    }
}
