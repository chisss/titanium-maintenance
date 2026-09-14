package com.titanium.maintenance.application.saga;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;

import com.titanium.maintenance.event.MaintenanceEndorsementIssuedEvent;
import com.titanium.maintenance.port.document.EndorsementDocumentPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保全批单单证派发器（application/saga，事件驱动的异步编排）
 * <p>
 * 监听 {@link MaintenanceEndorsementIssuedEvent}（案件级生效回执落定时由聚合发布），把自足的批单要素
 * 交付 {@link EndorsementDocumentPort}，由 document 域渲染正文、落盘并建档。本类只做「事件 → 出口 Port」
 * 的搬运，不含业务规则（规则属聚合），亦不查询任何读模型（要素由写侧固化在事件载荷中）。
 * </p>
 * <p>
 * 🔴 <b>为何复用 {@code maintenance-kafka-group} 而非新建处理组</b>：该组已登记在
 * {@code titanium.axon.outbound-relay.groups}（见 application.yml），其语义是「按事件存储即 Outbox 形态
 * 装配，且首启位点取事件流<b>末端</b>」。若为本派发器新建处理组，新组不在该名单中，首次部署将从事件流
 * <b>头部</b>重放——对全部历史已生效保全案件逐个补发批单，造成单证洪水。<b>不得</b>改动本组的归属配置。
 * </p>
 * <p>
 * 🔴 <b>失败不吞</b>：Port 实现抛出的异常一律向上传播，由本组的 DLQ 机制（{@code dlq.enabled: true}）捕获并
 * 定时重投。批单是保全生效事实的对外凭证，静默丢失将导致案件已生效而批单永久缺失且无从对账。
 * </p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ProcessingGroup(MaintenanceEndorsementDocumentSaga.PROCESSING_GROUP)
public class MaintenanceEndorsementDocumentSaga {

    /**
     * 跨域出站处理组名。
     * <p>🔴 必须与 {@code MaintenanceKafkaEventPublisher.PROCESSING_GROUP}、application.yml 的
     * {@code axon.eventhandling.processors.<name>} 键、{@code titanium.axon.outbound-relay.groups} 取值
     * 四者一致：漂移时 Axon 会为本组退回默认处理器，丢失 DLQ 与「首启取末端」配置——处理器仍存在（故不
     * 报错），但既无重投能力，首启还会重放全量历史事件补发批单。</p>
     */
    public static final String PROCESSING_GROUP = "maintenance-kafka-group";

    private final EndorsementDocumentPort endorsementDocumentPort;

    @EventHandler
    public void on(MaintenanceEndorsementIssuedEvent event) {
        log.info("Handling MaintenanceEndorsementIssuedEvent: maintenanceId={}, 保全项数={}",
                event.maintenanceId().id(), event.document().items().size());
        String archived = endorsementDocumentPort.archiveEndorsementDocument(event.document());
        log.info("[保全批单-派发] 批单要素已交付 document 域: maintenanceId={}, 回执键={}",
                event.maintenanceId().id(), archived);
    }
}
