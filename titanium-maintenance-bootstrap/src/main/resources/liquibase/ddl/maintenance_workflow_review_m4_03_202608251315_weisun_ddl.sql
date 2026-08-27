--liquibase formatted sql
--changeset weisun:maintenance-workflow-review-m4-03-202608251315
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN review_mode VARCHAR(16) COMMENT '人工或自动审核';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN review_decision VARCHAR(16) COMMENT '审核通过或拒绝';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN review_policy_code VARCHAR(128) COMMENT '冻结审核策略编码';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN review_policy_version VARCHAR(64) COMMENT '审核策略版本';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN review_context_hash VARCHAR(64) COMMENT '审核上下文SHA-256';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN review_gate_evidence_json TEXT COMMENT '自动审核七门禁结构化证据';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN review_comment VARCHAR(500) COMMENT '人工意见或自动结论说明';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN review_decided_at DATETIME COMMENT '审核决定时间';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN review_decided_by VARCHAR(64) COMMENT '审核主体';

--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN review_decided_by;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN review_decided_at;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN review_comment;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN review_gate_evidence_json;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN review_context_hash;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN review_policy_version;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN review_policy_code;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN review_decision;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN review_mode;
