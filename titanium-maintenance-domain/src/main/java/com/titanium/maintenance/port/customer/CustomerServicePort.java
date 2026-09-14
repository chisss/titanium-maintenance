package com.titanium.maintenance.port.customer;

/**
 * 客户服务端口（出口/driven port）
 * <p>
 * 应用侧表达对客户域的能力需求：校验客户存在。具体的跨微服务调用（Feign）由基础设施层 {@code infrastructure.adapter}
 * 的适配器实现，领域侧不依赖远程响应类型（防腐）。
 * </p>
 * <p>
 * 🔴 <b>本端口即 maintenance 与 customer 的全部协作面，不订阅客户主题（2026-09-14 m6-908 判定）</b>：
 * 本域对客户的唯一需求是<b>保全受理时点的同步存在性校验</b>，无长期状态订阅语义。客户信息变更应经
 * <b>保全批改</b>落到保单（本域正是该通道的执行方，有案件、有留痕、可审核），而非由客户事件静默改写。
 * 故 {@code customer-created/updated/status-changed/relationship-added} 四主题均不接入本域，
 * 判定依据见 {@code docs/技术文档/跨域事件目录-2026-09.md} §六.16。
 * </p>
 * <p>
 * ⚠️ 本域 {@code infrastructure/config/KafkaConfig} 带 {@code @EnableKafka} 但<b>零 {@code @KafkaListener}</b>
 * （开关空置，无入站消费）——若未来确需入站消费，开关已就绪；当前不构成缺陷。
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
