--liquibase formatted sql
--changeset weisun:maintenance-field-conflict-m5-06a-202608261730
ALTER TABLE t_maintenance_field_change_view ADD COLUMN conflict_operation_id VARCHAR(128) COMMENT '最近一次冲突检测操作ID';
ALTER TABLE t_maintenance_field_change_view ADD COLUMN conflict_detected_at DATETIME COMMENT '最近一次冲突检测时间';
ALTER TABLE t_maintenance_field_change_view ADD COLUMN conflict_policy_version BIGINT COMMENT '冲突检测对应Policy版本';
ALTER TABLE t_maintenance_field_change_view ADD COLUMN conflict_evidence_hash VARCHAR(64) COMMENT '冲突检测请求证据摘要';
ALTER TABLE t_maintenance_field_change_view ADD COLUMN resolution_operation_id VARCHAR(128) COMMENT '最近一次冲突解决操作ID';
ALTER TABLE t_maintenance_field_change_view ADD COLUMN resolution_reason VARCHAR(500) COMMENT '冲突解决原因';
ALTER TABLE t_maintenance_field_change_view ADD COLUMN resolution_evidence_hash VARCHAR(64) COMMENT '冲突解决请求证据摘要';
ALTER TABLE t_maintenance_field_change_view ADD COLUMN resolved_by VARCHAR(64) COMMENT '冲突解决操作人';
ALTER TABLE t_maintenance_field_change_view ADD COLUMN resolved_at DATETIME COMMENT '冲突解决时间';
CREATE INDEX idx_maintenance_field_conflict ON t_maintenance_field_change_view
    (tenant_id, maintenance_id, conflict_status);

--rollback DROP INDEX idx_maintenance_field_conflict ON t_maintenance_field_change_view;
--rollback ALTER TABLE t_maintenance_field_change_view DROP COLUMN resolved_at;
--rollback ALTER TABLE t_maintenance_field_change_view DROP COLUMN resolved_by;
--rollback ALTER TABLE t_maintenance_field_change_view DROP COLUMN resolution_evidence_hash;
--rollback ALTER TABLE t_maintenance_field_change_view DROP COLUMN resolution_reason;
--rollback ALTER TABLE t_maintenance_field_change_view DROP COLUMN resolution_operation_id;
--rollback ALTER TABLE t_maintenance_field_change_view DROP COLUMN conflict_evidence_hash;
--rollback ALTER TABLE t_maintenance_field_change_view DROP COLUMN conflict_policy_version;
--rollback ALTER TABLE t_maintenance_field_change_view DROP COLUMN conflict_detected_at;
--rollback ALTER TABLE t_maintenance_field_change_view DROP COLUMN conflict_operation_id;
