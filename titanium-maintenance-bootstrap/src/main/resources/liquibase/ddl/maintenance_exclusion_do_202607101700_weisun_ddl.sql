--liquibase formatted sql

--changeset weisun:maintenance-exclusion-1
--comment 保全互斥规则 DO 表（配置数据，定义哪两类保全不可同时进行）
CREATE TABLE IF NOT EXISTS maintenance_exclusion (
    id                VARCHAR(36)   NOT NULL                    COMMENT '主键(雪花)',
    maintenance_type_1 VARCHAR(50)  NOT NULL                    COMMENT '保全类型1(MaintenanceType枚举code)',
    maintenance_type_2 VARCHAR(50)  NOT NULL                    COMMENT '保全类型2(MaintenanceType枚举code)',
    description       VARCHAR(200)                             COMMENT '互斥规则描述',
    tenant_id         VARCHAR(36)   NOT NULL                    COMMENT '租户ID',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by        VARCHAR(32)   NOT NULL DEFAULT 'system'   COMMENT '创建人',
    updated_by        VARCHAR(32)   NOT NULL DEFAULT 'system'   COMMENT '更新人',
    is_deleted        TINYINT       NOT NULL DEFAULT 0           COMMENT '逻辑删除(0否1是)',
    PRIMARY KEY (id),
    KEY idx_maintenance_exclusion_tenant (tenant_id),
    KEY idx_maintenance_exclusion_type1 (maintenance_type_1, tenant_id),
    UNIQUE KEY uk_maintenance_exclusion_pair (maintenance_type_1, maintenance_type_2, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保全互斥规则配置';
--rollback DROP TABLE IF EXISTS maintenance_exclusion;
