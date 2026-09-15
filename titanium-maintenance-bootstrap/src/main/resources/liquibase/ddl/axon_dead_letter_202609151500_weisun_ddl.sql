--liquibase formatted sql

-- Axon 死信队列表（第 6 张 Axon 系统表）：仅在处理组配置 dlq.enabled=true 时才被读写。
-- 背景：本域建表脚本原只含 5 张 Axon 表，而 application.yml 已为 maintenance-query-group 与
-- maintenance-kafka-group 显式开启 DLQ（read 侧投影重投 + 跨域出站重发）。
-- 🔴 缺本表的实际后果（2026-09-15 真机实测，非理论风险）：Axon 的 TrackingEventProcessor 在
-- 处理任一事件前都会先查本表判断该事件序列是否已死信，表不存在即抛 SQLGrammarException
-- （Table 'titanium_maintenance.axon_dead_letter_entry' doesn't exist）→ 整个 UnitOfWork 回滚 →
-- Releasing claim on token and preparing for retry（退避 1s→2s→…→60s 无限循环）→ **token 永不推进**。
-- 实测现象：maintenance-query-group 与 maintenance-kafka-group 双双停在 global_index=849，
-- 读模型与跨域出站（maintenance-executed → policy 回写保单状态）**整域停滞**，
-- 而事件处理器日志中**看不到原始业务异常**——它被 DLQ 写入失败遮蔽。
-- 即：开启 DLQ 却不建本表，不是「重投能力失效」，而是**处理器架构性停摆**。
-- 表名 axon_dead_letter_entry 由 AxonTablePrefixPhysicalNamingStrategy 在运行期对齐（根规约 §6.3）。
-- 字段定义对齐 Axon 4.10 org.axonframework.eventhandling.deadletter.jpa.DeadLetterEntry。
--changeset weisun:maintenance-axon-dlq
CREATE TABLE IF NOT EXISTS axon_dead_letter_entry (
    dead_letter_id       VARCHAR(255) NOT NULL,
    cause_message        VARCHAR(1023),
    cause_type           VARCHAR(255),
    diagnostics          BLOB,
    enqueued_at          VARCHAR(255) NOT NULL,
    last_touched         VARCHAR(255),
    aggregate_identifier VARCHAR(255),
    event_identifier     VARCHAR(255) NOT NULL,
    message_type         VARCHAR(255) NOT NULL,
    meta_data            BLOB,
    payload              BLOB         NOT NULL,
    payload_revision     VARCHAR(255),
    payload_type         VARCHAR(255) NOT NULL,
    sequence_number      BIGINT,
    time_stamp           VARCHAR(255) NOT NULL,
    token                BLOB,
    token_type           VARCHAR(255),
    type                 VARCHAR(255),
    processing_group     VARCHAR(255) NOT NULL,
    processing_started   VARCHAR(255),
    sequence_identifier  VARCHAR(255) NOT NULL,
    sequence_index       BIGINT       NOT NULL,
    PRIMARY KEY (dead_letter_id),
    UNIQUE KEY uk_dle_seq (processing_group, sequence_identifier, sequence_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Axon 死信队列表';
--rollback DROP TABLE IF EXISTS axon_dead_letter_entry;
