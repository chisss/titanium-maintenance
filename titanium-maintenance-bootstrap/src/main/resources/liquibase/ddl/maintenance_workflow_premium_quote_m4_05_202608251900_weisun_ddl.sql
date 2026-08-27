--liquibase formatted sql
--changeset weisun:maintenance-workflow-premium-quote-m4-05-202608251900
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN premium_quote_status VARCHAR(32) COMMENT '保全报价检查点状态';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN premium_quote_id VARCHAR(64) COMMENT 'Product报价事实ID';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN premium_quote_version VARCHAR(64) COMMENT '内容寻址报价版本';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN premium_quote_request_hash VARCHAR(64) COMMENT '报价请求SHA-256';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN premium_quote_original_calculation_id VARCHAR(128) COMMENT '原确认计算ID';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN premium_quote_original_result_hash VARCHAR(64) COMMENT '原确认计算结果SHA-256';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN premium_quote_replacement_calculation_id VARCHAR(128) COMMENT '替代确认计算ID';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN premium_quote_replacement_result_hash VARCHAR(64) COMMENT '替代确认计算结果SHA-256';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN premium_quote_pricing_plan_version VARCHAR(64) COMMENT '实际定价计划版本';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN premium_quote_pricing_plan_hash VARCHAR(64) COMMENT '实际定价计划内容SHA-256';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN premium_quote_result_hash VARCHAR(64) COMMENT 'Product报价结果SHA-256';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN premium_quote_detail_summary VARCHAR(500) COMMENT '脱敏报价明细摘要';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN premium_quote_direction VARCHAR(16) COMMENT '客户余额方向';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN premium_quote_amount DECIMAL(20,8) COMMENT 'Product最终差额';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN premium_quote_currency VARCHAR(3) COMMENT '报价币种';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN premium_quoted_at DATETIME COMMENT 'Product报价时间';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN premium_quote_valid_until DATETIME COMMENT '报价有效期截止时间';

--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN premium_quote_valid_until;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN premium_quoted_at;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN premium_quote_currency;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN premium_quote_amount;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN premium_quote_direction;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN premium_quote_detail_summary;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN premium_quote_result_hash;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN premium_quote_pricing_plan_hash;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN premium_quote_pricing_plan_version;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN premium_quote_replacement_result_hash;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN premium_quote_replacement_calculation_id;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN premium_quote_original_result_hash;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN premium_quote_original_calculation_id;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN premium_quote_request_hash;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN premium_quote_version;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN premium_quote_id;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN premium_quote_status;
