--liquibase formatted sql
--changeset weisun:maintenance-effect-schedule-m5-04-202608252300
ALTER TABLE t_maintenance_view ADD COLUMN effect_schedule_id VARCHAR(128) COMMENT '案件级未来生效计划ID';
ALTER TABLE t_maintenance_view ADD COLUMN effect_schedule_status VARCHAR(20) COMMENT '未来生效计划状态';
ALTER TABLE t_maintenance_view ADD COLUMN effect_schedule_tenant_zone_id VARCHAR(64) COMMENT '计划冻结的租户时区';
ALTER TABLE t_maintenance_view ADD COLUMN effect_schedule_next_execution_at DATETIME COMMENT '计划下一执行时间（UTC）';
ALTER TABLE t_maintenance_view ADD COLUMN effect_schedule_attempt_count INT NOT NULL DEFAULT 0 COMMENT '计划累计执行次数';
ALTER TABLE t_maintenance_view ADD COLUMN effect_schedule_last_attempt_id VARCHAR(128) COMMENT '最近调度尝试ID';
ALTER TABLE t_maintenance_view ADD COLUMN effect_schedule_last_attempt_at DATETIME COMMENT '最近调度尝试时间';
ALTER TABLE t_maintenance_view ADD COLUMN effect_schedule_last_error_code VARCHAR(64) COMMENT '最近调度错误码';
ALTER TABLE t_maintenance_view ADD COLUMN effect_schedule_last_error_message VARCHAR(500) COMMENT '最近调度错误信息';
ALTER TABLE t_maintenance_view ADD COLUMN effect_schedule_created_at DATETIME COMMENT '计划创建时间';
ALTER TABLE t_maintenance_view ADD COLUMN effect_schedule_updated_at DATETIME COMMENT '计划更新时间';
ALTER TABLE t_maintenance_view ADD COLUMN effect_schedule_lease_owner VARCHAR(128) COMMENT '当前调度租约持有者';
ALTER TABLE t_maintenance_view ADD COLUMN effect_schedule_lease_until DATETIME COMMENT '当前调度租约截止时间';
CREATE INDEX idx_maintenance_effect_schedule_due
    ON t_maintenance_view (effect_schedule_status, effect_schedule_next_execution_at, effect_schedule_lease_until);

--rollback DROP INDEX idx_maintenance_effect_schedule_due ON t_maintenance_view;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN effect_schedule_lease_until;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN effect_schedule_lease_owner;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN effect_schedule_updated_at;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN effect_schedule_created_at;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN effect_schedule_last_error_message;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN effect_schedule_last_error_code;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN effect_schedule_last_attempt_at;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN effect_schedule_last_attempt_id;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN effect_schedule_attempt_count;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN effect_schedule_next_execution_at;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN effect_schedule_tenant_zone_id;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN effect_schedule_status;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN effect_schedule_id;
