--liquibase formatted sql
-- 说明：以下业务表无对应聚合根/读模型实体，依据《全域DDL重建方案清单》§3.9 建立，字段为方案清单标注字段。
--changeset weisun:maintenance-2
-- 保单贷款表，依方案清单 §3.9
CREATE TABLE IF NOT EXISTS t_policy_loan (
    id            VARCHAR(32)   NOT NULL COMMENT '主键(雪花)',
    policy_id     VARCHAR(36)   NOT NULL COMMENT '保单ID',
    loan_amount   DECIMAL(18,2) NOT NULL COMMENT '贷款金额',
    interest_rate DECIMAL(9,6)           COMMENT '贷款利率',
    loan_date     DATETIME               COMMENT '贷款日期',
    repaid        TINYINT       NOT NULL DEFAULT 0 COMMENT '已偿还(0否1是)',
    tenant_id     VARCHAR(32)   NOT NULL COMMENT '租户ID',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by    VARCHAR(32)   NOT NULL DEFAULT 'system' COMMENT '创建人',
    updated_by    VARCHAR(32)   NOT NULL DEFAULT 'system' COMMENT '更新人',
    is_deleted    TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除(0否1是)',
    PRIMARY KEY (id),
    KEY idx_policy_loan_tenant (tenant_id),
    KEY idx_policy_loan_policy (policy_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保单贷款表(方案清单§3.9,无聚合根实体)';
--rollback DROP TABLE IF EXISTS t_policy_loan;

--changeset weisun:maintenance-3
-- 退保记录表，依方案清单 §3.9(含 cash_value/refund_amount/surrender_type)
CREATE TABLE IF NOT EXISTS t_surrender (
    id             VARCHAR(32)   NOT NULL COMMENT '主键(雪花)',
    policy_id      VARCHAR(36)   NOT NULL COMMENT '保单ID',
    cash_value     DECIMAL(18,2)          COMMENT '现金价值',
    refund_amount  DECIMAL(18,2)          COMMENT '退还金额',
    surrender_type VARCHAR(32)            COMMENT '退保类型',
    effective_date DATETIME               COMMENT '生效日期',
    tenant_id      VARCHAR(32)   NOT NULL COMMENT '租户ID',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by     VARCHAR(32)   NOT NULL DEFAULT 'system' COMMENT '创建人',
    updated_by     VARCHAR(32)   NOT NULL DEFAULT 'system' COMMENT '更新人',
    is_deleted     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除(0否1是)',
    PRIMARY KEY (id),
    KEY idx_surrender_tenant (tenant_id),
    KEY idx_surrender_policy (policy_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退保记录表(方案清单§3.9,无聚合根实体)';
--rollback DROP TABLE IF EXISTS t_surrender;

--changeset weisun:maintenance-4
-- 保全保费差额表(补退)，依方案清单 §3.9
CREATE TABLE IF NOT EXISTS t_maintenance_premium_diff (
    id             VARCHAR(32)   NOT NULL COMMENT '主键(雪花)',
    maintenance_id VARCHAR(36)   NOT NULL COMMENT '保全案件ID',
    diff_amount    DECIMAL(18,2) NOT NULL COMMENT '差额金额',
    direction      VARCHAR(16)            COMMENT '方向(补/退)',
    tenant_id      VARCHAR(32)   NOT NULL COMMENT '租户ID',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by     VARCHAR(32)   NOT NULL DEFAULT 'system' COMMENT '创建人',
    updated_by     VARCHAR(32)   NOT NULL DEFAULT 'system' COMMENT '更新人',
    is_deleted     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除(0否1是)',
    PRIMARY KEY (id),
    KEY idx_maint_premium_diff_tenant (tenant_id),
    KEY idx_maint_premium_diff_maint (maintenance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保全保费差额表(方案清单§3.9,无聚合根实体)';
--rollback DROP TABLE IF EXISTS t_maintenance_premium_diff;
