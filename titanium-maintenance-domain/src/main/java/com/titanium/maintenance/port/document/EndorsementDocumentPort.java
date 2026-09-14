package com.titanium.maintenance.port.document;

import com.titanium.maintenance.valueobject.endorsement.EndorsementDocument;

/**
 * 保全批单单证端口（出口侧 Port，对端域 document）
 * <p>
 * 保全案件生效完成后，须向 document 域交付一份**批改文书（批单）**归档留痕。本端口表达「保全域需要
 * 出具批单」这一领域能力，具体实现（出站机制、载荷编码）由 infrastructure 的
 * {@code infrastructure/adapter/document/EndorsementDocumentAdapter} 承担——按六边形架构，Port 是内核
 * 边界概念，Adapter 属基础设施。
 * </p>
 * <p>
 * 🔴 <b>与案件内 DOCUMENT 步骤的层次区分</b>：保全工作流模板末段的 DOCUMENT 步骤
 * （{@code RecordMaintenanceDocumentCommand}）记录的是**域内凭证事实**（保全项执行凭证，落本域
 * {@code MaintenanceDocumentEvidence}），不触达 document 域；本端口产出的是案件生效完成后**对外签发的
 * 批改文书**，二者不同层、不重复。判据见 m15-1805 与 m16-1902 的实施说明。
 * </p>
 * <p>
 * 🔴 <b>为何经 Kafka 而非 Feign</b>：批单是可延迟的**旁路产物**，不应把 document 域可用性绑进「案件生效」
 * 这一关键事务——异步出站下 document 暂时不可达只会令批单延后生成（出站失败入 DLQ 定时重投），不会令
 * 保全生效失败。与理赔域 {@code DocumentServicePort}（m14-1705）的跨域取向一致。
 * </p>
 */
public interface EndorsementDocumentPort {

    /**
     * 交付批单要素，由 document 域渲染正文、落盘并建档。
     *
     * @param document 批单要素（保全案件已生效的权威事实，自足载荷）
     * @return 保全案件ID（出站幂等键，与分区键同源）
     */
    String archiveEndorsementDocument(EndorsementDocument document);
}
