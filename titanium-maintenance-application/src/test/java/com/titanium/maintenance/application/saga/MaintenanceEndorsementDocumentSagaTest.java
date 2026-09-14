package com.titanium.maintenance.application.saga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.config.ProcessingGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.maintenance.event.MaintenanceEndorsementIssuedEvent;
import com.titanium.maintenance.port.document.EndorsementDocumentPort;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.endorsement.EndorsementDocument;
import com.titanium.maintenance.valueobject.endorsement.EndorsementFieldChange;
import com.titanium.maintenance.valueobject.endorsement.EndorsementItem;

/**
 * 保全批单派发器测试
 * <p>
 * 锁死三条契约：① 搬运保真——事件载荷中的批单要素<b>原样</b>交付出口 Port（本类不取数、不查读模型、不加工，
 * 要素由写侧聚合固化）；② 🔴 <b>失败不吞</b>——Port 抛出的异常必须向上传播，由本组 DLQ 捕获并重投；静默
 * 吞掉将导致案件已生效而批单永久缺失且无从对账，故此处刻意与 document 域入站监听器的「就地吞掉」取向相反
 * （两侧按各自可靠性设施取值）；③ 🔴 <b>处理组名四方一致</b>——与 {@code MaintenanceKafkaEventPublisher}、
 * application.yml 的处理器键、{@code outbound-relay.groups} 取值必须同名，漂移会使本组退回默认处理器：既无
 * 重投能力，首启还会重放全量历史事件，对已生效案件洪水式补发批单。
 * </p>
 * 纯 mockito，不启动容器。
 */
class MaintenanceEndorsementDocumentSagaTest {

    private static final String        MAINTENANCE_ID = "MNT-20260914-0001";
    private static final String        POLICY_ID      = "POL-20260914-0001";
    private static final String        TENANT_ID      = "TENANT-A";
    private static final LocalDateTime NOW            = LocalDateTime.of(2026, 9, 14, 10, 0);

    private EndorsementDocumentPort     endorsementDocumentPort;
    private MaintenanceEndorsementDocumentSaga saga;

    @BeforeEach
    void setUp() {
        endorsementDocumentPort = mock(EndorsementDocumentPort.class);
        saga = new MaintenanceEndorsementDocumentSaga(endorsementDocumentPort);
    }

    @Test
    @DisplayName("事件触发派发：批单要素原样交付出口 Port，不经取数或再加工")
    void shouldDeliverElementPayloadToPortAsIs() {
        EndorsementDocument document = endorsementDocument();
        MaintenanceEndorsementIssuedEvent event = new MaintenanceEndorsementIssuedEvent(MaintenanceId.of(MAINTENANCE_ID),
                document, NOW);

        saga.on(event);

        verify(endorsementDocumentPort).archiveEndorsementDocument(document);
    }

    @Test
    @DisplayName("🔴 出站失败必须向上传播（不吞），否则批单不进死信队列、无法重投")
    void shouldPropagatePublishFailure() {
        EndorsementDocument document = endorsementDocument();
        MaintenanceEndorsementIssuedEvent event = new MaintenanceEndorsementIssuedEvent(MaintenanceId.of(MAINTENANCE_ID),
                document, NOW);
        when(endorsementDocumentPort.archiveEndorsementDocument(document))
                .thenThrow(new IllegalStateException("broker unreachable"));

        assertThrows(IllegalStateException.class, () -> saga.on(event));
    }

    @Test
    @DisplayName("🔴 处理组名与出站发布器同名：漂移会使 DLQ 与「首启取事件流末端」配置同时落空")
    void shouldDeclareProcessingGroupConsistently() {
        ProcessingGroup annotation = MaintenanceEndorsementDocumentSaga.class.getAnnotation(ProcessingGroup.class);

        assertNotNull(annotation, "出站派发器必须声明 @ProcessingGroup");
        assertEquals(MaintenanceEndorsementDocumentSaga.PROCESSING_GROUP, annotation.value());
        assertEquals("maintenance-kafka-group", MaintenanceEndorsementDocumentSaga.PROCESSING_GROUP,
                "组名须复用已在 outbound-relay.groups 名单中的既有组，新建组会触发历史事件洪水补发");
    }

    /** 构造批单要素（含一个保全项与一条字段变更，验明嵌套结构在搬运中不失真） */
    private static EndorsementDocument endorsementDocument() {
        return new EndorsementDocument(MAINTENANCE_ID, POLICY_ID, "POLICY_INFO_CHANGE",
                List.of(new EndorsementItem("POLICY_INFO_CHANGE", "保单信息变更",
                        List.of(new EndorsementFieldChange("policy-1", "policy.holder.mobile", "13800000000",
                                "13900000000")))),
                NOW, NOW.plusMinutes(2), TENANT_ID);
    }
}
