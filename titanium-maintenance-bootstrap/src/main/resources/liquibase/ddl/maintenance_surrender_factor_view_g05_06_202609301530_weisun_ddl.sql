--liquibase formatted sql
--changeset weisun:maintenance-surrender-factor-view-g05-06-202609301530
-- 退费因子明细读模型（g05-06，AC-06）+ 字段差异跨案冗余键。
--
-- 【一】t_maintenance_surrender_factor_view：退费因子明细的**独立子表**。
--   为何不把 6 个因子平铺成列：因子集合随产品类型/退费类型扩展（Product 侧
--   SurrenderRefundBreakdown 构造期按 CREDIT/DEBIT 双向汇总），平铺列会在每新增一类因子时
--   改表；独立子表 + factor_order 保序既容纳扩展，也如实保留对端回执的原始顺序（不重排、不合并）。
--   🔴 因子类型/方向/文案一律存**字符串码**：本域不以因子做任何金额重算，退费金额的权威仍在
--   Product 域（ProductSurrenderValuePort），此处只是防腐镜像，未知码原样透传。
--   🔴 读模型最终一致（事件投影维护），**禁止回流写侧做金额/资格决策**（根规约 §3.4.9 ⑥）。
--
-- 【二】t_maintenance_field_change_view 补 6 列：字段级行原先既无保单标识也无生效口径，
--   无法跨案检索、无法自描述。补冗余键 + 生效区间两要素 + 外部单证号 + 费用方向。
--   已存在的行这 6 列全部为 NULL，由配套 DML 回填可回填的 5 列（批单号不可回填，见该脚本注释）。
CREATE TABLE t_maintenance_surrender_factor_view (
    factor_view_id VARCHAR(64) NOT NULL COMMENT '因子明细行ID（租户+案件+序号+因子类型派生摘要）',
    maintenance_id VARCHAR(64) NOT NULL COMMENT '保全案件ID',
    factor_order INT NOT NULL COMMENT '因子在回执中的原始顺序（0 起，保序展示不重排）',
    factor_type VARCHAR(64) NOT NULL COMMENT '退费因子类型码（对端Product字符串码，防腐透传）',
    direction VARCHAR(16) NOT NULL COMMENT '金额方向码（CREDIT退费/DEBIT补收/NONE无金额）',
    amount DECIMAL(20,8) COMMENT '因子金额绝对值（权威在Product域，本域不重算）',
    explain_key VARCHAR(191) COMMENT '因子文案键（展示层按语言渲染）',
    tenant_id VARCHAR(32) NOT NULL COMMENT '租户ID',
    version BIGINT COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL COMMENT '投影创建时间',
    update_time DATETIME NOT NULL COMMENT '投影更新时间',
    PRIMARY KEY (factor_view_id),
    UNIQUE KEY uk_maintenance_surrender_factor (tenant_id, maintenance_id, factor_order),
    KEY idx_maintenance_surrender_factor_case (tenant_id, maintenance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保全退费因子明细投影';

ALTER TABLE t_maintenance_field_change_view ADD COLUMN policy_id VARCHAR(64) COMMENT '保单ID（冗余键，随案件读模型刷新）';
ALTER TABLE t_maintenance_field_change_view ADD COLUMN policy_number VARCHAR(64) COMMENT '保单号（冗余键，字段级行自描述与跨案检索）';
ALTER TABLE t_maintenance_field_change_view ADD COLUMN endorsement_no VARCHAR(64) COMMENT '外部单证号-批单号（生效回执时回填，未生效为NULL）';
ALTER TABLE t_maintenance_field_change_view ADD COLUMN specific_effective_date DATETIME COMMENT '生效区间起-约定生效日（案件读模型刷新）';
ALTER TABLE t_maintenance_field_change_view ADD COLUMN business_effective_at VARCHAR(40) COMMENT '生效区间止-业务生效时点（ISO-8601含偏移，非本地时区无歧义）';
ALTER TABLE t_maintenance_field_change_view ADD COLUMN fee_direction VARCHAR(16) COMMENT '费用方向（DEBIT补收/CREDIT退费/NONE无费用）';

-- 冗余键要真能承担「按保单跨案检索字段级行」，否则该列只是占位；历史行为 NULL 不影响索引可用性。
CREATE INDEX idx_maintenance_field_change_policy ON t_maintenance_field_change_view (tenant_id, policy_number);

--rollback ALTER TABLE t_maintenance_field_change_view DROP INDEX idx_maintenance_field_change_policy;
--rollback ALTER TABLE t_maintenance_field_change_view DROP COLUMN fee_direction;
--rollback ALTER TABLE t_maintenance_field_change_view DROP COLUMN business_effective_at;
--rollback ALTER TABLE t_maintenance_field_change_view DROP COLUMN specific_effective_date;
--rollback ALTER TABLE t_maintenance_field_change_view DROP COLUMN endorsement_no;
--rollback ALTER TABLE t_maintenance_field_change_view DROP COLUMN policy_number;
--rollback ALTER TABLE t_maintenance_field_change_view DROP COLUMN policy_id;
--rollback DROP TABLE IF EXISTS t_maintenance_surrender_factor_view;
