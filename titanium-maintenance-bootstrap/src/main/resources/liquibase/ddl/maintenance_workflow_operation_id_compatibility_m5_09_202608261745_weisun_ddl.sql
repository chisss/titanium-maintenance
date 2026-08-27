--liquibase formatted sql
--changeset weisun:maintenance-workflow-operation-id-compatibility-m5-09-202608261745
-- 生效操作号包含阶段摘要和任务号，合法长度可能超过原 128 字符投影上限。
ALTER TABLE t_maintenance_workflow_task_view
    MODIFY COLUMN last_operation_id VARCHAR(256) COMMENT '最近操作幂等号';

--rollback ALTER TABLE t_maintenance_workflow_task_view MODIFY COLUMN last_operation_id VARCHAR(128) COMMENT '最近操作幂等号';
