--liquibase formatted sql
--changeset weisun:maintenance-premium-settlement-v2d2-202608201700
-- V2-D2-A：在保全读模型保存 Product 差额与 Billing 余额事实证据链。
ALTER TABLE t_maintenance_view
    ADD COLUMN premium_settlement_status VARCHAR(32) NOT NULL DEFAULT 'NOT_STARTED' COMMENT '费用事实登记状态' AFTER refund_amount,
    ADD COLUMN original_calculation_id VARCHAR(64) COMMENT '原确认计算ID' AFTER premium_settlement_status,
    ADD COLUMN replacement_calculation_id VARCHAR(64) COMMENT '保全替代计算ID' AFTER original_calculation_id,
    ADD COLUMN premium_adjustment_id VARCHAR(64) COMMENT 'Product生命周期差额ID' AFTER replacement_calculation_id,
    ADD COLUMN premium_adjustment_result_hash VARCHAR(64) COMMENT 'Product生命周期差额结果hash' AFTER premium_adjustment_id,
    ADD COLUMN billing_posting_id VARCHAR(64) COMMENT 'Billing余额事实ID' AFTER premium_adjustment_result_hash,
    ADD COLUMN balance_direction VARCHAR(16) COMMENT '客户余额方向' AFTER billing_posting_id,
    ADD COLUMN balance_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '客户余额差额绝对值' AFTER balance_direction,
    ADD COLUMN balance_currency VARCHAR(3) COMMENT '币种' AFTER balance_amount,
    ADD UNIQUE KEY uk_maintenance_premium_adjustment (tenant_id, premium_adjustment_id),
    ADD UNIQUE KEY uk_maintenance_billing_posting (tenant_id, billing_posting_id);
--rollback ALTER TABLE t_maintenance_view DROP INDEX uk_maintenance_premium_adjustment, DROP INDEX uk_maintenance_billing_posting, DROP COLUMN balance_currency, DROP COLUMN balance_amount, DROP COLUMN balance_direction, DROP COLUMN billing_posting_id, DROP COLUMN premium_adjustment_result_hash, DROP COLUMN premium_adjustment_id, DROP COLUMN replacement_calculation_id, DROP COLUMN original_calculation_id, DROP COLUMN premium_settlement_status;
