--liquibase formatted sql
--changeset weisun:maintenance-effect-compensation-m5-03-202608252010
ALTER TABLE t_maintenance_view ADD COLUMN effect_compensation_required BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否需要人工补偿勾稽';
ALTER TABLE t_maintenance_view ADD COLUMN effect_compensation_id VARCHAR(128) COMMENT '补偿事实ID';
ALTER TABLE t_maintenance_view ADD COLUMN effect_compensation_request_id VARCHAR(128) COMMENT 'Policy请求ID';
ALTER TABLE t_maintenance_view ADD COLUMN effect_compensation_endorsement_no VARCHAR(64) COMMENT 'Policy已生成批单号';
ALTER TABLE t_maintenance_view ADD COLUMN effect_compensation_policy_version BIGINT COMMENT 'Policy已应用版本';
ALTER TABLE t_maintenance_view ADD COLUMN effect_compensation_application_hash VARCHAR(64) COMMENT 'Policy应用摘要';
ALTER TABLE t_maintenance_view ADD COLUMN effect_compensation_reason VARCHAR(500) COMMENT '案件回执写入失败原因';
ALTER TABLE t_maintenance_view ADD COLUMN effect_compensation_recorded_at DATETIME COMMENT '补偿事实记录时间';
ALTER TABLE t_maintenance_view ADD COLUMN effect_compensation_resolved_at DATETIME COMMENT '补偿勾稽完成时间';
ALTER TABLE t_maintenance_view ADD COLUMN effect_compensation_resolved_by VARCHAR(64) COMMENT '补偿处理人';

--rollback ALTER TABLE t_maintenance_view DROP COLUMN effect_compensation_resolved_by;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN effect_compensation_resolved_at;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN effect_compensation_recorded_at;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN effect_compensation_reason;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN effect_compensation_application_hash;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN effect_compensation_policy_version;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN effect_compensation_endorsement_no;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN effect_compensation_request_id;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN effect_compensation_id;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN effect_compensation_required;
