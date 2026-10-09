--liquibase formatted sql
--changeset weisun:maintenance-workflow-loan-disbursement-g04-03-202609281530
-- 保单贷款放款回执列（g04-03）：贷款类保全案件生效出口的权威回执落库。
-- 与 switch_*（投资账户转换回执）并列的第三种生效回执形态，三者互斥（一笔生效只由一个出口受理），
-- 故新增列全部可空，非贷款案件恒为 NULL。
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN loan_id VARCHAR(32) COMMENT '保单贷款放款回执-贷款业务号（稳定派生，LOAN前缀32字符）';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN loan_amount DECIMAL(24,8) COMMENT '保单贷款放款回执-放款本金';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN loan_interest_rate DECIMAL(24,8) COMMENT '保单贷款放款回执-冻结年利率（[0,1]）';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN loan_date DATETIME COMMENT '保单贷款放款回执-放款日期';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN loan_billing_posting_id VARCHAR(64) COMMENT '保单贷款放款回执-计费域入账流水号';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN loan_payment_order_id VARCHAR(64) COMMENT '保单贷款放款回执-支付域出账指令号';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN loan_disbursed_at DATETIME COMMENT '保单贷款放款回执-本域收到回执时间';

--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN loan_disbursed_at;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN loan_payment_order_id;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN loan_billing_posting_id;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN loan_date;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN loan_interest_rate;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN loan_amount;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN loan_id;
