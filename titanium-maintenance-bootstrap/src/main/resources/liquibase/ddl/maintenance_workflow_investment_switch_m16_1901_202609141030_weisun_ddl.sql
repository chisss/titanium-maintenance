--liquibase formatted sql
--changeset weisun:maintenance-workflow-investment-switch-m16-1901-202609141030
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN switch_account_id VARCHAR(64) COMMENT '投资账户转换回执-投资账户ID';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN switch_evidence_hash VARCHAR(64) COMMENT '投资账户转换回执-载荷摘要SHA-256';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN switch_unit_price DECIMAL(24,8) COMMENT '投资账户转换回执-转换后单位净值';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN switch_total_units DECIMAL(24,8) COMMENT '投资账户转换回执-转换后持有单位数';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN switch_account_value DECIMAL(24,8) COMMENT '投资账户转换回执-转换后账户价值';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN switch_currency VARCHAR(8) COMMENT '投资账户转换回执-账户币种';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN switch_switched_at DATETIME COMMENT '投资账户转换回执-转换时间';

--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN switch_switched_at;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN switch_currency;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN switch_account_value;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN switch_total_units;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN switch_unit_price;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN switch_evidence_hash;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN switch_account_id;
