--liquibase formatted sql
--changeset weisun:maintenance-workflow-loan-repayment-g04-04-202609281800
-- 保单贷款还款回执列（g04-04）：还款类保全案件生效出口的权威回执落库。
-- 与 switch_*（投资账户转换回执）、loan_*（保单贷款放款回执）并列的第四种生效回执形态，四者互斥
-- （一笔生效只由一个出口受理），故新增列全部可空，非还款案件恒为 NULL。
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN loan_repayment_id VARCHAR(32) COMMENT '保单贷款还款回执-还款业务号（稳定派生，RPAY前缀32字符）';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN loan_repayment_amount DECIMAL(24,8) COMMENT '保单贷款还款回执-还款总额';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN loan_repayment_interest_portion DECIMAL(24,8) COMMENT '保单贷款还款回执-利息部分（保单域权威分配）';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN loan_repayment_principal_portion DECIMAL(24,8) COMMENT '保单贷款还款回执-本金部分（保单域权威分配）';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN loan_repayment_accrued_interest DECIMAL(24,8) COMMENT '保单贷款还款回执-本次计息区间增量利息（单利365）';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN loan_outstanding_principal_after DECIMAL(24,8) COMMENT '保单贷款还款回执-还款后未偿本金';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN loan_outstanding_interest_after DECIMAL(24,8) COMMENT '保单贷款还款回执-还款后已计未付利息';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN loan_repayment_settled TINYINT(1) COMMENT '保单贷款还款回执-是否已结清';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN loan_repayment_date DATETIME COMMENT '保单贷款还款回执-还款日期（保单域权威，参与载荷摘要）';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN loan_repayment_billing_posting_id VARCHAR(64) COMMENT '保单贷款还款回执-计费域入账流水号';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN loan_repayment_payment_order_id VARCHAR(64) COMMENT '保单贷款还款回执-支付域收款单号';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN loan_repaid_at DATETIME COMMENT '保单贷款还款回执-本域收到回执时间（不参与载荷摘要）';

--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN loan_repaid_at;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN loan_repayment_payment_order_id;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN loan_repayment_billing_posting_id;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN loan_repayment_date;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN loan_repayment_settled;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN loan_outstanding_interest_after;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN loan_outstanding_principal_after;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN loan_repayment_accrued_interest;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN loan_repayment_principal_portion;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN loan_repayment_interest_portion;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN loan_repayment_amount;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN loan_repayment_id;
