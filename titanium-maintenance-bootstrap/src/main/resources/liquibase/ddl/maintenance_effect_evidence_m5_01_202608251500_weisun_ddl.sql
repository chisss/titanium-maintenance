--liquibase formatted sql
--changeset weisun:maintenance-effect-evidence-m5-01-202608251500
ALTER TABLE t_maintenance_view ADD COLUMN effect_status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED' COMMENT 'Policy正交生效状态';

ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN effect_request_id VARCHAR(128) COMMENT 'Policy生效请求ID';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN effect_request_hash VARCHAR(64) COMMENT 'Policy生效请求SHA-256';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN effect_expected_policy_version BIGINT COMMENT '请求期望Policy版本';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN effect_time_type VARCHAR(20) COMMENT '生效时间类型';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN effect_requested_effective_at DATETIME COMMENT '请求生效时间';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN effect_proposed_snapshot_hash VARCHAR(64) COMMENT '请求引用的proposed快照摘要';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN effect_requested_at DATETIME COMMENT '生效请求冻结时间';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN policy_endorsement_no VARCHAR(64) COMMENT 'Policy权威批单号';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN policy_actual_version BIGINT COMMENT 'Policy实际应用版本';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN policy_application_hash VARCHAR(64) COMMENT 'Policy应用结果SHA-256';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN applied_snapshot_storage_key VARCHAR(512) COMMENT 'applied快照引用';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN applied_snapshot_hash VARCHAR(64) COMMENT 'applied快照摘要';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN applied_snapshot_policy_version BIGINT COMMENT 'applied快照Policy版本';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN applied_snapshot_captured_at VARCHAR(40) COMMENT 'applied快照采集时间及偏移';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN applied_fields_json TEXT COMMENT 'Policy实际字段值结构化证据';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN policy_applied_at DATETIME COMMENT 'Policy实际应用时间';

--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN policy_applied_at;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN applied_fields_json;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN applied_snapshot_captured_at;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN applied_snapshot_policy_version;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN applied_snapshot_hash;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN applied_snapshot_storage_key;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN policy_application_hash;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN policy_actual_version;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN policy_endorsement_no;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN effect_requested_at;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN effect_proposed_snapshot_hash;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN effect_requested_effective_at;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN effect_time_type;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN effect_expected_policy_version;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN effect_request_hash;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN effect_request_id;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN effect_status;
