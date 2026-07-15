--liquibase formatted sql
--changeset weisun:maintenance-1
-- 保全案件读模型表（对齐 MaintenanceView.java，主键 maintenance_id）
CREATE TABLE IF NOT EXISTS t_maintenance_view (
    maintenance_id          VARCHAR(36)   NOT NULL COMMENT '保全案件ID(聚合根ID,读模型主键)',
    policy_id               VARCHAR(36)            COMMENT '保单ID',
    customer_id             VARCHAR(36)            COMMENT '客户ID',
    maintenance_type        VARCHAR(50)            COMMENT '保全类型',
    status                  VARCHAR(20)            COMMENT '保全状态',
    effective_time_type     VARCHAR(20)            COMMENT '生效时间类型',
    specific_effective_date DATETIME               COMMENT '指定生效日期',
    total_amount            DECIMAL(18,2)          COMMENT '保全总金额',
    refund_amount           DECIMAL(18,2)          COMMENT '退费金额',
    description             VARCHAR(500)           COMMENT '保全描述',
    tenant_id               VARCHAR(32)   NOT NULL COMMENT '租户ID',
    version                 BIGINT                 COMMENT '乐观锁版本',
    create_time             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '投影创建时间',
    update_time             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '投影更新时间',
    PRIMARY KEY (maintenance_id),
    KEY idx_maintenance_view_tenant (tenant_id),
    KEY idx_maintenance_view_policy (policy_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保全案件读模型表';
--rollback DROP TABLE IF EXISTS t_maintenance_view;

--changeset weisun:maintenance-2
-- 保全读模型补列：创建人/更新人（对齐 MaintenanceView.createdBy/updatedBy，由领域事件的操作人字段投影写入）
ALTER TABLE t_maintenance_view
    ADD COLUMN created_by VARCHAR(64) COMMENT '创建人' AFTER description,
    ADD COLUMN updated_by VARCHAR(64) COMMENT '更新人' AFTER created_by;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN created_by, DROP COLUMN updated_by;
