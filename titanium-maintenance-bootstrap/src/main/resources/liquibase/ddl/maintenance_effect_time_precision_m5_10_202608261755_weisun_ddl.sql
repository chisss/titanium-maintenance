--liquibase formatted sql
--changeset weisun:maintenance-effect-time-precision-m5-10-202608261755
-- 生效时间参与 Policy 请求摘要，必须完整保留事件中的纳秒精度。
ALTER TABLE t_maintenance_workflow_task_view
    MODIFY COLUMN effect_requested_effective_at VARCHAR(40) COMMENT '请求生效时间，ISO-8601纳秒精度';

--rollback ALTER TABLE t_maintenance_workflow_task_view MODIFY COLUMN effect_requested_effective_at DATETIME COMMENT '请求生效时间';
