package com.titanium.maintenance.event;

import java.time.LocalDateTime;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.endorsement.EndorsementDocument;

/**
 * 保全批单已出具事件。
 * <p>
 * 在案件级生效回执落定（{@code MaintenanceEffectStatusChangedEvent(APPLIED)}）的<b>同一时刻</b>由聚合发布，
 * 携带自足的批单要素 {@link EndorsementDocument}：由其驱动 application 层
 * {@code MaintenanceEndorsementDocumentSaga} 向 document 域交付批单，全程无需回查读模型。
 * </p>
 * <p>
 * 🔴 <b>为何由写侧固化要素而非由 Saga 取数</b>：批单要素（被批改保单、保全项、字段前后值、生效时间）都是
 * 写侧已发生的事实，在案件生效那一刻已全部确定；若改由 Saga 查读模型取数，要素正确性将取决于读侧投影的
 * 推进时序（最终一致），且会把跨层查询依赖引入事件处理路径。事实由写侧固化，消费方只做搬运——与理赔域
 * 结案事件自足载荷（m14-1705）同范式。
 * </p>
 *
 * @param maintenanceId 保全案件ID
 * @param document      批单要素（跨域自足载荷）
 * @param issuedAt      批单出具时刻（案件级生效回执落定时刻）
 */
public record MaintenanceEndorsementIssuedEvent(MaintenanceId maintenanceId, EndorsementDocument document,
        LocalDateTime issuedAt) {
}
