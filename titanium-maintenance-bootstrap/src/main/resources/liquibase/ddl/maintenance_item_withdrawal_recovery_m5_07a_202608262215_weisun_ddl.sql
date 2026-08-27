--liquibase formatted sql
--changeset weisun:maintenance-item-withdrawal-recovery-m5-07a-202608262215
ALTER TABLE t_maintenance_case_item_view ADD COLUMN withdrawal_payment_method VARCHAR(64) COMMENT '撤销补收支付渠道代码';
ALTER TABLE t_maintenance_case_item_view ADD COLUMN withdrawal_recovery_configured_at DATETIME COMMENT '撤销自动恢复上下文配置时间';
ALTER TABLE t_maintenance_case_item_view ADD COLUMN withdrawal_recovery_lease_owner VARCHAR(128) COMMENT '撤销恢复租约持有者';
ALTER TABLE t_maintenance_case_item_view ADD COLUMN withdrawal_recovery_lease_until DATETIME COMMENT '撤销恢复租约到期时间';
CREATE INDEX idx_maintenance_item_withdrawal_recovery ON t_maintenance_case_item_view
    (withdrawal_status, update_time, withdrawal_recovery_lease_until);

--rollback DROP INDEX idx_maintenance_item_withdrawal_recovery ON t_maintenance_case_item_view;
--rollback ALTER TABLE t_maintenance_case_item_view DROP COLUMN withdrawal_recovery_lease_until;
--rollback ALTER TABLE t_maintenance_case_item_view DROP COLUMN withdrawal_recovery_lease_owner;
--rollback ALTER TABLE t_maintenance_case_item_view DROP COLUMN withdrawal_recovery_configured_at;
--rollback ALTER TABLE t_maintenance_case_item_view DROP COLUMN withdrawal_payment_method;
