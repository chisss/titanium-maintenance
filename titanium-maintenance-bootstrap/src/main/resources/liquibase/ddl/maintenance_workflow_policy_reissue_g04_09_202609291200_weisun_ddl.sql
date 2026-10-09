--liquibase formatted sql
--changeset weisun:maintenance-workflow-policy-reissue-g04-09-202609291200
-- 保单补发单证请求回执列（g04-09）：补发类保全案件生效出口的权威回执落库。
-- 与保单回执（policy_*）、投资账户转换回执（switch_*）、保单贷款放款回执（loan_*）、贷款还款回执
-- （loan_repayment_*）并列的第五种生效回执形态，五者互斥（一笔生效只由一个出口受理），
-- 故新增列全部可空，非补发类案件恒为 NULL。
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN reissue_request_id VARCHAR(32) COMMENT '保单补发请求回执-交付对端的补发请求号（稳定派生，RISS前缀32字符）';
ALTER TABLE t_maintenance_workflow_task_view ADD COLUMN reissue_requested_at DATETIME COMMENT '保单补发请求回执-本域交付请求事实的时间（不参与载荷摘要）';

--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN reissue_requested_at;
--rollback ALTER TABLE t_maintenance_workflow_task_view DROP COLUMN reissue_request_id;
