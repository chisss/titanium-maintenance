--liquibase formatted sql
--changeset weisun:maintenance-case-atomic-effect-m5-03-202608251930
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN policy_state_action VARCHAR(20) COMMENT 'Policy合同状态动作';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN policy_status_before VARCHAR(32) COMMENT 'Policy变更前合同状态';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN policy_status_after VARCHAR(32) COMMENT 'Policy变更后合同状态';

--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN policy_status_after;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN policy_status_before;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN policy_state_action;
