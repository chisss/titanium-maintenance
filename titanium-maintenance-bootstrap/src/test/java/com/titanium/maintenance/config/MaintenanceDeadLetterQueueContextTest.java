package com.titanium.maintenance.config;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.titanium.billing.api.RetroactivePeriodAdjustmentApi;
import com.titanium.maintenance.Bootstrap;
import com.titanium.maintenance.infrastructure.client.billing.BillingPremiumLifecycleClient;
import com.titanium.maintenance.infrastructure.client.billing.BillingRetroactiveImpactClient;
import com.titanium.maintenance.infrastructure.client.claim.ClaimRetroactiveImpactClient;
import com.titanium.maintenance.infrastructure.client.customer.CustomerServiceClient;
import com.titanium.maintenance.infrastructure.client.insurance.InsuranceServiceClient;
import com.titanium.maintenance.infrastructure.client.payment.PaymentMaintenanceRefundClient;
import com.titanium.maintenance.infrastructure.client.payment.PaymentPremiumCollectionClient;
import com.titanium.maintenance.infrastructure.client.payment.PaymentRetroactiveImpactClient;
import com.titanium.maintenance.infrastructure.client.policy.PolicyFieldCatalogClient;
import com.titanium.maintenance.infrastructure.client.policy.PolicyServiceClient;
import com.titanium.maintenance.infrastructure.client.product.ProductMaintenanceOfferingClient;
import com.titanium.maintenance.infrastructure.client.product.ProductPremiumLifecycleClient;
import com.titanium.maintenance.infrastructure.client.product.ProductSurrenderValueClient;
import com.titanium.maintenance.infrastructure.event.MaintenanceKafkaEventPublisher;
import com.titanium.product.api.ProductMaintenancePremiumQuoteApi;
import com.titanium.product.api.ProductPremiumCalculationApi;
import com.titanium.underwriting.api.MaintenanceUnderwritingApi;

/**
 * 保全域出站处理组的上下文级守护（启动真实组合根，非 yml 文本解析）
 * <p>
 * 与 {@code MaintenanceKafkaEventPublisherTest}（单元级，含「组名与 {@code @ProcessingGroup} 一致」的
 * 注解断言）分工：本类验证的是 <b>运行时装配结果</b>——application.yml 声明、{@code @ProcessingGroup}
 * 取值、Axon 实际建出的处理器三者对得上，且死信队列真能取到。注解断言只证明类上写了什么，
 * 证明不了 Axon 按它建出了 tracking 处理器（组名漂移时配置被静默忽略、处理器退回默认形态）。
 * </p>
 * <p>
 * 🔴 <b>守护的出站组</b>：{@code maintenance-kafka-group}
 * （{@link MaintenanceKafkaEventPublisher}）——保全事件跨域外发。该链路失败只能靠「抛出 → 入 DLQ →
 * {@code DeadLetterQueueService} 定时重投」兜底，三条退化路径都不会让构建失败：
 * ① 组未注册（yml 键与 {@code @ProcessingGroup} 漂移）→ 处理器仍存在却退回默认配置，**不报错**；
 * ② 非 tracking（改回 subscribing）→ {@code SequencedDeadLetterQueue} 拿不到，失败即永久丢失；
 * ③ 未启用 DLQ（删 dlq 配置）→ {@code sequencedDeadLetterProcessor} 恒空，重投服务静默空转。
 * </p>
 * <p>
 * ⚠️ tracking 的已知代价是「首启位点」：令牌不存在时从事件流<b>头部</b>开始消费。该风险由
 * application.yml 的 {@code titanium.axon.outbound-relay.groups} 登记消解（登记后首启取流末端），
 * 故改动本组配置时须同步检查该登记项。
 * </p>
 * <p>
 * <b>为何 mock 全部 Feign 客户端</b>：本地无 {@code spring-cloud-starter-loadbalancer}，
 * 未被 mock 的客户端会因惰性解析失败而拖垮上下文。本域契约面最大——13 个
 * {@code infrastructure.client} 域内契约 + 4 个 {@code MaintenanceExternalFeignConfiguration}
 * 追加的外域正式 API。**新增 Feign 契约时必须同步补入下发表**。
 * </p>
 */
@SpringBootTest(classes = Bootstrap.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:maintenance-dlq;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.liquibase.enabled=false",
                "spring.task.scheduling.enabled=false",
                "axon.axonserver.enabled=false"
        })
@MockitoBean(types = {
        // 域内契约（infrastructure.client，@EnableFeignClients basePackages 扫描面）
        BillingPremiumLifecycleClient.class,
        BillingRetroactiveImpactClient.class,
        ClaimRetroactiveImpactClient.class,
        CustomerServiceClient.class,
        InsuranceServiceClient.class,
        PaymentMaintenanceRefundClient.class,
        PaymentPremiumCollectionClient.class,
        PaymentRetroactiveImpactClient.class,
        PolicyFieldCatalogClient.class,
        PolicyServiceClient.class,
        ProductMaintenanceOfferingClient.class,
        ProductPremiumLifecycleClient.class,
        ProductSurrenderValueClient.class,
        // 外域正式 API（MaintenanceExternalFeignConfiguration 以 clients= 显式追加）
        MaintenanceUnderwritingApi.class,
        ProductMaintenancePremiumQuoteApi.class,
        ProductPremiumCalculationApi.class,
        RetroactivePeriodAdjustmentApi.class
})
class MaintenanceDeadLetterQueueContextTest {

    @Autowired
    private EventProcessingConfiguration eventProcessingConfiguration;

    @Test
    @DisplayName("跨域出站组 maintenance-kafka-group：已注册 + tracking + DLQ 可用")
    void providesTrackingKafkaPublisherGroupWithDeadLetterQueue() {
        assertOutboundGroupIsTrackingWithDeadLetterQueue(MaintenanceKafkaEventPublisher.PROCESSING_GROUP);
    }

    /**
     * 断言出站处理组按「tracking + DLQ」装配（三条断言各有分工，见类注释）。
     */
    private void assertOutboundGroupIsTrackingWithDeadLetterQueue(String processingGroup) {
        var processor = eventProcessingConfiguration.eventProcessorByProcessingGroup(processingGroup);
        assertTrue(processor.isPresent(),
                "未注册处理组 " + processingGroup
                        + "：@ProcessingGroup 与 application.yml 的 processors 键不一致");
        assertInstanceOf(TrackingEventProcessor.class, processor.get(),
                "处理组 " + processingGroup
                        + " 未按 tracking 模式装配：subscribing 拿不到死信队列，发布失败即永久丢失");

        assertTrue(eventProcessingConfiguration.sequencedDeadLetterProcessor(processingGroup).isPresent(),
                "处理组 " + processingGroup
                        + " 未启用死信队列：需在 application.yml 该组下配 dlq.enabled=true，"
                        + "否则 DeadLetterQueueService 静默空转、失败事件永不重投");
    }
}
