package com.titanium.maintenance.infrastructure.adapter.document;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.titanium.common.kafka.KafkaPublishSupport;
import com.titanium.maintenance.common.constant.MaintenanceConstants;
import com.titanium.maintenance.port.document.PolicyReissueDocumentPort;
import com.titanium.maintenance.valueobject.reissue.PolicyReissueDocument;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单补发单证请求适配器（infrastructure/adapter/document）
 * <p>
 * {@link PolicyReissueDocumentPort} 的出口侧实现：把补发单证请求事实投递到
 * {@code maintenance-policy-reissue-requested} 主题，由 document 域消费后渲染补发件、签章并寄送（G13）。
 * 与 {@link EndorsementDocumentAdapter} 同构（同为 document 域出站），仅分区键与失败语义的判据不同。
 * </p>
 * <p>
 * 🔴 <b>线格式</b>：本适配器发<b>要素 POJO 本身</b>，由生产者 value serializer（装配了 {@code JavaTimeModule}
 * 的 {@code JsonSerializer}）序列化为 JSON 对象——与 {@code MaintenanceKafkaEventPublisher} 的既有形态一致，
 * <b>不得</b>改为先经 fastjson2 序列化成字符串再发（那样 value serializer 会二次编码）。
 * </p>
 * <p>
 * 🔴 <b>失败会抛出</b>：由 {@link KafkaPublishSupport#awaitSent} 等待 broker 确认，失败抛
 * {@code KafkaPublishException}——在本出口上该异常<b>不是</b>「单证延后」而是「案件生效失败」（由调用方
 * {@code MaintenanceEffectApplicationService} 记为生效失败），因为补发请求事实正是本案生效的实质内容。
 * <b>不可</b>改为发后即弃：那将出现「案件显示已生效、补发请求却从未离开本域」且无从对账。
 * </p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PolicyReissueDocumentAdapter implements PolicyReissueDocumentPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 投递补发单证请求事实至 document 域。
     * <p>
     * 🔴 <b>分区键取保单ID</b>（与批单出口取案件ID不同）：判据是「下游消费的保序维度」——批单是案件派生的
     * 文书，各案彼此独立，故按案件分区；而补发指向的是<b>保单自身凭证</b>的替换关系，同一保单的多次补发
     * （挂失→解除→再挂失）在语义上互为先后，散到不同分区被并行消费即等于放弃保序。分区键取错是静默的：
     * 单条消息仍能送达，只有「后到的旧请求覆盖新请求」这类顺序缺陷会在事后暴露。补发是低频业务，按保单
     * 分区带来的串行代价可忽略。
     * </p>
     */
    @Override
    public String requestPolicyReissue(PolicyReissueDocument document) {
        log.info("[保全补发-出站] 发布补发单证请求事实: maintenanceId={}, policyId={}, 请求号={}",
                document.maintenanceId(), document.policyId(), document.reissueRequestId());
        KafkaPublishSupport.awaitSent(
                () -> kafkaTemplate.send(MaintenanceConstants.KafkaTopic.MAINTENANCE_POLICY_REISSUE_REQUESTED,
                        document.policyId(), document),
                MaintenanceConstants.KafkaTopic.MAINTENANCE_POLICY_REISSUE_REQUESTED, document.policyId());
        return document.reissueRequestId();
    }
}
