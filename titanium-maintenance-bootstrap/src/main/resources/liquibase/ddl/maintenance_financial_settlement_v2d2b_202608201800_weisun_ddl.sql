--liquibase formatted sql
--changeset weisun:maintenance-financial-settlement-v2d2b-202608201800
-- V2-D2-B：保存退费资金结算与佣金调整检查点。
ALTER TABLE t_maintenance_view
    ADD COLUMN refund_instruction_id VARCHAR(64) COMMENT 'Billing退款指令ID' AFTER billing_posting_id,
    ADD COLUMN refund_order_id VARCHAR(64) COMMENT 'Payment退款订单ID' AFTER refund_instruction_id,
    ADD COLUMN refund_status VARCHAR(32) COMMENT '退款处理状态' AFTER refund_order_id,
    ADD COLUMN commission_adjustment_count INT NOT NULL DEFAULT 0 COMMENT '佣金调整数量' AFTER refund_status;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN commission_adjustment_count, DROP COLUMN refund_status, DROP COLUMN refund_order_id, DROP COLUMN refund_instruction_id;
