package com.titanium.maintenance.query.scheduled;

import java.util.Optional;

import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.deadletter.SequencedDeadLetterProcessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * 死信队列监控 + 重试服务（读模型最终一致性保障）
 * <p>
 * 定时扫描保全读侧投影处理组的死信队列，重试因事件乱序等原因投影失败的事件序列。 处理组名须与
 * {@link com.titanium.maintenance.query.handler.projection.MaintenanceProjectionEventHandler} 的
 * {@code @ProcessingGroup} 一致。
 * </p>
 */
@Slf4j
@Service
public class DeadLetterQueueService {

    /** 投影处理组名，与 MaintenanceProjectionEventHandler 的 @ProcessingGroup 一致 */
    private static final String PROCESSING_GROUP = "maintenance-query-group";

    private final EventProcessingConfiguration eventProcessingConfig;

    public DeadLetterQueueService(EventProcessingConfiguration eventProcessingConfig) {
        this.eventProcessingConfig = eventProcessingConfig;
    }

    /**
     * 定时扫描死信队列并重试失败事件（每30秒一次）
     */
    @Scheduled(fixedRate = 30000)
    public void retryDeadLetterEvents() {
        Optional<SequencedDeadLetterProcessor<EventMessage<?>>> processorOpt = eventProcessingConfig
                .sequencedDeadLetterProcessor(PROCESSING_GROUP);

        if (processorOpt.isEmpty()) {
            log.debug("处理组 {} 未启用死信队列，跳过重试", PROCESSING_GROUP);
            return;
        }

        SequencedDeadLetterProcessor<EventMessage<?>> processor = processorOpt.get();
        try {
            boolean processed = processor.processAny();
            if (processed) {
                log.info("死信队列重试成功一条序列: group={}", PROCESSING_GROUP);
            } else {
                log.debug("死信队列为空或无可重试序列: group={}", PROCESSING_GROUP);
            }
        } catch (Exception e) {
            log.error("死信队列重试异常: group={}", PROCESSING_GROUP, e);
        }
    }
}
