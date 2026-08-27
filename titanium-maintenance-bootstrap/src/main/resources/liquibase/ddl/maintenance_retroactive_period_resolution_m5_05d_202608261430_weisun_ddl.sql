--liquibase formatted sql
--changeset weisun:maintenance-retroactive-period-resolution-m5-05d-202608261430
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_period_resolution_id VARCHAR(64) COMMENT '关闭期间处理ID';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_period_resolution_operation_id VARCHAR(128) COMMENT '关闭期间处理幂等操作ID';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_period_resolution_request_hash VARCHAR(64) COMMENT '关闭期间处理请求摘要';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_period_resolution_status VARCHAR(24) COMMENT '关闭期间处理状态';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_billing_resolution_id VARCHAR(64) COMMENT 'Billing关闭期间处理结论ID';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_period_resolution_source_batch_hash VARCHAR(64) COMMENT '来源Billing批次摘要';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_period_resolution_target_period VARCHAR(16) COMMENT '结转目标会计期间';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_period_resolution_resolved_line_count INT NOT NULL DEFAULT 0 COMMENT '已处理关闭期间行数';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_period_resolution_result_hash VARCHAR(64) COMMENT '关闭期间处理结果摘要';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_period_resolution_reason VARCHAR(500) COMMENT '关闭期间处理原因';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_period_resolution_failure_code VARCHAR(64) COMMENT '关闭期间处理失败码';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_period_resolution_failure_message VARCHAR(500) COMMENT '关闭期间处理失败信息';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_period_resolution_started_at DATETIME COMMENT '关闭期间处理开始时间';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_period_resolution_completed_at DATETIME COMMENT '关闭期间处理完成或失败时间';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_period_resolution_updated_at DATETIME COMMENT '关闭期间处理最近更新时间';

ALTER TABLE t_maintenance_retroactive_period_adjustment_view ADD COLUMN target_accounting_period VARCHAR(16) COMMENT '结转目标会计期间';
ALTER TABLE t_maintenance_retroactive_period_adjustment_view ADD COLUMN resolution_status VARCHAR(24) COMMENT '关闭期间处理状态';
ALTER TABLE t_maintenance_retroactive_period_adjustment_view ADD COLUMN posting_reference VARCHAR(128) COMMENT 'Billing服务端入账引用';
ALTER TABLE t_maintenance_retroactive_period_adjustment_view ADD COLUMN resolution_result_hash VARCHAR(64) COMMENT '单期间处理结果摘要';
CREATE INDEX idx_retro_period_resolution ON t_maintenance_retroactive_period_adjustment_view
    (tenant_id, resolution_status, target_accounting_period);

--rollback DROP INDEX idx_retro_period_resolution ON t_maintenance_retroactive_period_adjustment_view;
--rollback ALTER TABLE t_maintenance_retroactive_period_adjustment_view DROP COLUMN resolution_result_hash;
--rollback ALTER TABLE t_maintenance_retroactive_period_adjustment_view DROP COLUMN posting_reference;
--rollback ALTER TABLE t_maintenance_retroactive_period_adjustment_view DROP COLUMN resolution_status;
--rollback ALTER TABLE t_maintenance_retroactive_period_adjustment_view DROP COLUMN target_accounting_period;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_period_resolution_updated_at;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_period_resolution_completed_at;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_period_resolution_started_at;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_period_resolution_failure_message;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_period_resolution_failure_code;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_period_resolution_reason;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_period_resolution_result_hash;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_period_resolution_resolved_line_count;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_period_resolution_target_period;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_period_resolution_source_batch_hash;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_billing_resolution_id;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_period_resolution_status;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_period_resolution_request_hash;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_period_resolution_operation_id;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_period_resolution_id;
