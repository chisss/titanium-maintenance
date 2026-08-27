--liquibase formatted sql
--changeset weisun:maintenance-retroactive-impact-m5-05b-202608251930
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_impact_analysis_id VARCHAR(64) COMMENT '当前追溯影响分析ID';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_impact_analysis_version INT COMMENT '当前追溯影响分析版本';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_impact_operation_id VARCHAR(128) COMMENT '追溯影响分析幂等操作ID';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_impact_request_hash VARCHAR(64) COMMENT '追溯影响分析请求摘要';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_impact_scope_from DATETIME COMMENT '追溯影响范围起点';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_impact_scope_to DATETIME COMMENT '追溯影响范围终点';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_impact_status VARCHAR(20) COMMENT '追溯影响分析状态';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_impact_covered_domains VARCHAR(255) COMMENT '已覆盖权威域';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_impact_item_count INT NOT NULL DEFAULT 0 COMMENT '影响项总数';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_impact_blocking_count INT NOT NULL DEFAULT 0 COMMENT '阻断影响项数';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_impact_pending_count INT NOT NULL DEFAULT 0 COMMENT '待处理影响项数';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_impact_evidence_version VARCHAR(64) COMMENT '全域证据版本';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_impact_result_hash VARCHAR(64) COMMENT '全域分析结果摘要';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_impact_failure_code VARCHAR(64) COMMENT '分析失败码';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_impact_failure_message VARCHAR(500) COMMENT '分析失败信息';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_impact_started_at DATETIME COMMENT '分析开始时间';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_impact_completed_at DATETIME COMMENT '分析完成或失败时间';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_impact_updated_at DATETIME COMMENT '分析最近更新时间';

CREATE TABLE t_maintenance_retroactive_impact_view (
    impact_record_id VARCHAR(191) NOT NULL COMMENT '分析与影响项稳定组合ID',
    maintenance_id VARCHAR(64) NOT NULL COMMENT '保全案件ID',
    analysis_id VARCHAR(64) NOT NULL COMMENT '追溯影响分析ID',
    analysis_version INT NOT NULL COMMENT '追溯影响分析版本',
    item_id VARCHAR(128) NOT NULL COMMENT '影响项稳定ID',
    source_domain VARCHAR(16) NOT NULL COMMENT '权威归属域',
    impact_type VARCHAR(32) NOT NULL COMMENT '影响类型',
    reference_id VARCHAR(128) NOT NULL COMMENT '权威对象ID',
    reference_number VARCHAR(128) COMMENT '权威业务单号',
    occurred_at DATETIME NOT NULL COMMENT '影响事实发生时间',
    source_status VARCHAR(64) NOT NULL COMMENT '权威对象状态',
    amount DECIMAL(20,8) COMMENT '影响金额',
    currency VARCHAR(8) COMMENT '影响币种',
    severity VARCHAR(16) NOT NULL COMMENT '影响严重度',
    handling_status VARCHAR(16) NOT NULL COMMENT '影响处理状态',
    summary VARCHAR(500) NOT NULL COMMENT '后台影响摘要',
    evidence_version VARCHAR(64) NOT NULL COMMENT '单项证据版本',
    evidence_hash VARCHAR(64) NOT NULL COMMENT '单项证据摘要',
    tenant_id VARCHAR(32) NOT NULL COMMENT '租户ID',
    version BIGINT COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL COMMENT '投影创建时间',
    update_time DATETIME NOT NULL COMMENT '投影更新时间',
    PRIMARY KEY (impact_record_id),
    UNIQUE KEY uk_retro_impact_item (tenant_id, maintenance_id, analysis_id, item_id),
    KEY idx_retro_impact_case (tenant_id, maintenance_id, analysis_id),
    KEY idx_retro_impact_filter (tenant_id, source_domain, severity, handling_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保全追溯影响项投影';

--rollback DROP TABLE IF EXISTS t_maintenance_retroactive_impact_view;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_impact_updated_at;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_impact_completed_at;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_impact_started_at;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_impact_failure_message;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_impact_failure_code;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_impact_result_hash;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_impact_evidence_version;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_impact_pending_count;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_impact_blocking_count;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_impact_item_count;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_impact_covered_domains;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_impact_status;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_impact_scope_to;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_impact_scope_from;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_impact_request_hash;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_impact_operation_id;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_impact_analysis_version;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_impact_analysis_id;
