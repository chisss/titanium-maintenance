--liquibase formatted sql
--changeset weisun:maintenance-workflow-underwriting-m4-04-202608251520
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN underwriting_case_id VARCHAR(64) COMMENT 'Underwriting核保案件号';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN underwriting_request_hash VARCHAR(64) COMMENT '核保请求SHA-256';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN underwriting_rule_version VARCHAR(64) COMMENT '核保规则版本';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN underwriting_model_version VARCHAR(64) COMMENT '核保模型版本';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN underwriting_conclusion VARCHAR(32) COMMENT '核保结论';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN underwriting_conditions_json TEXT COMMENT '结构化附加条件';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN underwriting_summary VARCHAR(500) COMMENT '脱敏核保摘要';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN underwriting_completed_at DATETIME COMMENT '核保完成时间';

--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN underwriting_completed_at;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN underwriting_summary;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN underwriting_conditions_json;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN underwriting_conclusion;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN underwriting_model_version;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN underwriting_rule_version;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN underwriting_request_hash;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN underwriting_case_id;
