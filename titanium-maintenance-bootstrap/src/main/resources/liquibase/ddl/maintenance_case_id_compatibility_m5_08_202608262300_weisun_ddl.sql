--liquibase formatted sql
--changeset weisun:maintenance-case-id-compatibility-m5-08-202608262300
-- 兼容修复前已写入事件存储的超长确定性案件号；新案件号保持标准 UUID 长度。
ALTER TABLE t_maintenance_view MODIFY COLUMN maintenance_id VARCHAR(128) NOT NULL COMMENT '保全案件ID';
ALTER TABLE t_maintenance_case_item_view MODIFY COLUMN maintenance_id VARCHAR(128) NOT NULL COMMENT '保全案件ID';
ALTER TABLE t_maintenance_field_change_view MODIFY COLUMN maintenance_id VARCHAR(128) NOT NULL COMMENT '保全案件ID';
ALTER TABLE t_maintenance_premium_diff MODIFY COLUMN maintenance_id VARCHAR(128) NOT NULL COMMENT '保全案件ID';
ALTER TABLE t_maintenance_retroactive_impact_view MODIFY COLUMN maintenance_id VARCHAR(128) NOT NULL COMMENT '保全案件ID';
ALTER TABLE t_maintenance_retroactive_period_adjustment_view MODIFY COLUMN maintenance_id VARCHAR(128) NOT NULL COMMENT '保全案件ID';
ALTER TABLE t_maintenance_snapshot_view MODIFY COLUMN maintenance_id VARCHAR(128) NOT NULL COMMENT '保全案件ID';
ALTER TABLE t_maintenance_workflow_task_view MODIFY COLUMN maintenance_id VARCHAR(128) NOT NULL COMMENT '保全案件ID';

--rollback ALTER TABLE t_maintenance_workflow_task_view MODIFY COLUMN maintenance_id VARCHAR(64) NOT NULL COMMENT '保全案件ID';
--rollback ALTER TABLE t_maintenance_snapshot_view MODIFY COLUMN maintenance_id VARCHAR(64) NOT NULL COMMENT '保全案件ID';
--rollback ALTER TABLE t_maintenance_retroactive_period_adjustment_view MODIFY COLUMN maintenance_id VARCHAR(64) NOT NULL COMMENT '保全案件ID';
--rollback ALTER TABLE t_maintenance_retroactive_impact_view MODIFY COLUMN maintenance_id VARCHAR(64) NOT NULL COMMENT '保全案件ID';
--rollback ALTER TABLE t_maintenance_premium_diff MODIFY COLUMN maintenance_id VARCHAR(36) NOT NULL COMMENT '保全案件ID';
--rollback ALTER TABLE t_maintenance_field_change_view MODIFY COLUMN maintenance_id VARCHAR(64) NOT NULL COMMENT '保全案件ID';
--rollback ALTER TABLE t_maintenance_case_item_view MODIFY COLUMN maintenance_id VARCHAR(64) NOT NULL COMMENT '保全案件ID';
--rollback ALTER TABLE t_maintenance_view MODIFY COLUMN maintenance_id VARCHAR(36) NOT NULL COMMENT '保全案件ID';
