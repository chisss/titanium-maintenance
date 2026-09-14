package com.titanium.maintenance.infrastructure.adapter.document;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.titanium.common.kafka.KafkaPublishSupport;
import com.titanium.maintenance.common.constant.MaintenanceConstants;
import com.titanium.maintenance.port.document.EndorsementDocumentPort;
import com.titanium.maintenance.valueobject.endorsement.EndorsementDocument;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保全批单单证适配器（infrastructure/adapter/document）
 * <p>
 * {@link EndorsementDocumentPort} 的出口侧实现：把批单要素投递到
 * {@code maintenance-endorsement-issued} 主题，由 document 域消费后渲染正文、落盘并建档
 * （{@code DocumentType.ENDORSEMENT_DOC}）。与理赔域 {@code DocumentServiceAdapter} 同构。
 * </p>
 * <p>
 * 🔴 <b>线格式</b>：本适配器发**要素 POJO 本身**，由生产者 value serializer（装配了
 * {@code JavaTimeModule} 的 {@code JsonSerializer}）序列化为 JSON 对象——与
 * {@code MaintenanceKafkaEventPublisher} 的既有形态一致，<b>不得</b>改为先经 fastjson2 序列化成字符串
 * 再发（那样 value serializer 会二次编码）。消费端 document 域以 String 反序列化后按 JSON 解析，两种
 * 形态均可承接，但同一域内不应混用。
 * </p>
 * <p>
 * 🔴 <b>失败会抛出</b>：由 {@link KafkaPublishSupport#awaitSent} 等待 broker 确认，失败抛
 * {@code KafkaPublishException} → 触发处理器重试并最终入 DLQ 定时重投。<b>不可</b>改为发后即弃：
 * 保全批单是生效事实的对外凭证，静默丢失将导致案件已生效而批单永久缺失且无从对账。
 * </p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EndorsementDocumentAdapter implements EndorsementDocumentPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 投递批单要素至 document 域。
     * <p>
     * 🔴 <b>分区键取保全案件ID</b>：判据是「下游消费的保序维度」——document 侧建档单元是「案件派生的
     * 批单」，同一案件的多次出站须保序，故按案件分区，而非按保单（同一保单可派生多案，各案彼此独立）。
     * </p>
     */
    @Override
    public String archiveEndorsementDocument(EndorsementDocument document) {
        log.info("[保全批单-出站] 发布批单要素: maintenanceId={}, policyId={}, 保全项数={}",
                document.maintenanceId(), document.policyId(), document.items().size());
        KafkaPublishSupport.awaitSent(
                () -> kafkaTemplate.send(MaintenanceConstants.KafkaTopic.MAINTENANCE_ENDORSEMENT_ISSUED,
                        document.maintenanceId(), document),
                MaintenanceConstants.KafkaTopic.MAINTENANCE_ENDORSEMENT_ISSUED, document.maintenanceId());
        return document.maintenanceId();
    }
}
