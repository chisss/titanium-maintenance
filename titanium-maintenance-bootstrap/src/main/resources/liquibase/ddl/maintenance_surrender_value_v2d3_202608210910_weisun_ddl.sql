--liquibase formatted sql
--changeset weisun:maintenance-surrender-value-v2d3-202608210910
ALTER TABLE t_maintenance_view
    ADD COLUMN surrender_policy_code VARCHAR(64) COMMENT 'Product退保价值策略编码' AFTER balance_currency,
    ADD COLUMN surrender_policy_version VARCHAR(32) COMMENT 'Product退保价值策略版本' AFTER surrender_policy_code,
    ADD COLUMN surrender_policy_content_hash VARCHAR(64) COMMENT 'Product退保价值策略hash' AFTER surrender_policy_version,
    ADD COLUMN surrender_policy_year INT COMMENT '退保计算保单年度' AFTER surrender_policy_content_hash,
    ADD COLUMN cooling_off_days INT COMMENT '适用犹豫期天数' AFTER surrender_policy_year,
    ADD COLUMN surrender_refund_type VARCHAR(32) COMMENT '退保退费类型' AFTER cooling_off_days,
    ADD COLUMN within_cooling_off TINYINT(1) COMMENT '是否处于犹豫期' AFTER surrender_refund_type,
    ADD COLUMN cash_value_rate DECIMAL(20,8) COMMENT '适用现金价值率' AFTER within_cooling_off,
    ADD COLUMN retained_customer_amount DECIMAL(20,8) COMMENT '退保后客户金额保留值' AFTER cash_value_rate,
    ADD COLUMN internal_cost_retention_rate DECIMAL(20,8) COMMENT '内部成本保留率' AFTER retained_customer_amount;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN internal_cost_retention_rate, DROP COLUMN retained_customer_amount, DROP COLUMN cash_value_rate, DROP COLUMN within_cooling_off, DROP COLUMN surrender_refund_type, DROP COLUMN cooling_off_days, DROP COLUMN surrender_policy_year, DROP COLUMN surrender_policy_content_hash, DROP COLUMN surrender_policy_version, DROP COLUMN surrender_policy_code;
