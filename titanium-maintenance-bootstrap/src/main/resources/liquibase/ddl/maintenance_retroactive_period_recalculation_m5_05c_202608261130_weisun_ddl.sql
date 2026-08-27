--liquibase formatted sql
--changeset weisun:maintenance-retroactive-period-recalculation-m5-05c-202608261130
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_period_recalculation_id VARCHAR(64) COMMENT '当前追溯期间重算ID';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_period_recalculation_version INT COMMENT '当前追溯期间重算版本';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_period_recalculation_operation_id VARCHAR(128) COMMENT '追溯期间重算幂等操作ID';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_period_recalculation_request_hash VARCHAR(64) COMMENT '追溯期间重算请求摘要';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_period_analysis_id VARCHAR(64) COMMENT '绑定的追溯影响分析ID';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_period_analysis_version INT COMMENT '绑定的追溯影响分析版本';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_period_analysis_result_hash VARCHAR(64) COMMENT '绑定的追溯影响分析摘要';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_period_recalculation_status VARCHAR(24) COMMENT '追溯期间重算状态';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_product_recalculation_id VARCHAR(64) COMMENT 'Product期间重算ID';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_product_recalculation_version VARCHAR(64) COMMENT 'Product期间重算版本';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_product_original_calculation_id VARCHAR(128) COMMENT 'Product原计算ID';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_product_original_result_hash VARCHAR(64) COMMENT 'Product原计算摘要';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_product_replacement_calculation_id VARCHAR(128) COMMENT 'Product替换计算ID';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_product_replacement_result_hash VARCHAR(64) COMMENT 'Product替换计算摘要';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_product_direction VARCHAR(16) COMMENT 'Product总差额方向';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_product_amount DECIMAL(20,8) COMMENT 'Product总差额金额';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_product_currency VARCHAR(8) COMMENT 'Product总差额币种';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_product_input_hash VARCHAR(64) COMMENT 'Product重算输入摘要';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_product_result_hash VARCHAR(64) COMMENT 'Product重算结果摘要';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_product_calculated_at DATETIME COMMENT 'Product重算时间';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_period_count INT NOT NULL DEFAULT 0 COMMENT '追溯受影响期间数';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_billing_batch_id VARCHAR(64) COMMENT 'Billing期间调整批次ID';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_billing_status VARCHAR(32) COMMENT 'Billing期间调整批次状态';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_billing_posted_count INT NOT NULL DEFAULT 0 COMMENT 'Billing已登记期间数';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_billing_review_count INT NOT NULL DEFAULT 0 COMMENT 'Billing关闭期间复核数';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_billing_request_hash VARCHAR(64) COMMENT 'Billing调整请求摘要';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_billing_result_hash VARCHAR(64) COMMENT 'Billing调整结果摘要';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_billing_adjusted_at DATETIME COMMENT 'Billing调整时间';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_period_failure_code VARCHAR(64) COMMENT '期间重算失败码';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_period_failure_message VARCHAR(500) COMMENT '期间重算失败信息';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_period_started_at DATETIME COMMENT '期间重算开始时间';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_period_completed_at DATETIME COMMENT '期间重算完成或失败时间';
ALTER TABLE t_maintenance_view ADD COLUMN retroactive_period_updated_at DATETIME COMMENT '期间重算最近更新时间';

CREATE TABLE t_maintenance_retroactive_period_adjustment_view (
    period_record_id VARCHAR(191) NOT NULL COMMENT '重算与期间稳定组合ID',
    maintenance_id VARCHAR(64) NOT NULL COMMENT '保全案件ID',
    period_recalculation_id VARCHAR(64) NOT NULL COMMENT '追溯期间重算ID',
    period_recalculation_version INT NOT NULL COMMENT '追溯期间重算版本',
    analysis_id VARCHAR(64) NOT NULL COMMENT '追溯影响分析ID',
    analysis_version INT NOT NULL COMMENT '追溯影响分析版本',
    period_id VARCHAR(128) NOT NULL COMMENT '受影响期间稳定ID',
    source_reference_id VARCHAR(128) NOT NULL COMMENT 'Billing权威来源ID',
    accounting_period VARCHAR(16) COMMENT '会计期间',
    period_start DATETIME NOT NULL COMMENT '期间业务起点',
    original_amount DECIMAL(20,8) NOT NULL COMMENT '变更前期间金额',
    recalculated_amount DECIMAL(20,8) NOT NULL COMMENT '变更后期间金额',
    direction VARCHAR(16) NOT NULL COMMENT '差额方向',
    difference_amount DECIMAL(20,8) NOT NULL COMMENT '差额金额',
    currency VARCHAR(8) NOT NULL COMMENT '币种',
    billing_status VARCHAR(32) COMMENT 'Billing期间处理状态',
    source_evidence_hash VARCHAR(64) NOT NULL COMMENT 'Billing来源证据摘要',
    product_result_hash VARCHAR(64) NOT NULL COMMENT 'Product期间结果摘要',
    billing_result_hash VARCHAR(64) COMMENT 'Billing期间结果摘要',
    tenant_id VARCHAR(32) NOT NULL COMMENT '租户ID',
    version BIGINT COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL COMMENT '投影创建时间',
    update_time DATETIME NOT NULL COMMENT '投影更新时间',
    PRIMARY KEY (period_record_id),
    UNIQUE KEY uk_retro_period_item (tenant_id, maintenance_id, period_recalculation_id, period_id),
    KEY idx_retro_period_case (tenant_id, maintenance_id, period_recalculation_id),
    KEY idx_retro_period_status (tenant_id, billing_status, accounting_period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保全追溯期间调整投影';

--rollback DROP TABLE IF EXISTS t_maintenance_retroactive_period_adjustment_view;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_period_updated_at;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_period_completed_at;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_period_started_at;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_period_failure_message;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_period_failure_code;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_billing_adjusted_at;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_billing_result_hash;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_billing_request_hash;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_billing_review_count;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_billing_posted_count;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_billing_status;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_billing_batch_id;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_period_count;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_product_calculated_at;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_product_result_hash;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_product_input_hash;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_product_currency;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_product_amount;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_product_direction;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_product_replacement_result_hash;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_product_replacement_calculation_id;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_product_original_result_hash;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_product_original_calculation_id;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_product_recalculation_version;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_product_recalculation_id;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_period_recalculation_status;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_period_analysis_result_hash;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_period_analysis_version;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_period_analysis_id;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_period_recalculation_request_hash;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_period_recalculation_operation_id;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_period_recalculation_version;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN retroactive_period_recalculation_id;
