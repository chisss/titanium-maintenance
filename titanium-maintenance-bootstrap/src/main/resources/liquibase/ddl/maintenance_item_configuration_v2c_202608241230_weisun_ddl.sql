--liquibase formatted sql
--changeset weisun:maintenance-item-configuration-v2c-202608241230
CREATE TABLE t_maintenance_item_configuration (
    configuration_id VARCHAR(64) NOT NULL COMMENT '配置ID',
    tenant_id VARCHAR(64) NOT NULL COMMENT '租户ID',
    item_code VARCHAR(64) NOT NULL COMMENT '保全项编码',
    configuration_version VARCHAR(64) NOT NULL COMMENT '配置业务版本',
    revision_of_configuration_id VARCHAR(64) NULL COMMENT '来源配置ID',
    status VARCHAR(32) NOT NULL COMMENT '配置状态',
    valid_from DATETIME(6) NOT NULL COMMENT '配置生效时间',
    valid_to DATETIME(6) NULL COMMENT '配置失效时间',
    content_hash VARCHAR(64) NULL COMMENT '已发布内容SHA-256',
    configuration_json LONGTEXT NOT NULL COMMENT '配置聚合完整JSON快照',
    audit_entry_count INT NOT NULL COMMENT '已持久化审计条数',
    row_version BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA乐观锁版本',
    created_at DATETIME(6) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(6) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (configuration_id),
    CONSTRAINT uk_maintenance_config_business_key
        UNIQUE (tenant_id, item_code, configuration_version),
    INDEX idx_maintenance_config_effective
        (tenant_id, item_code, status, valid_from, valid_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保全项配置当前快照';

CREATE TABLE t_maintenance_item_configuration_audit (
    audit_id VARCHAR(36) NOT NULL COMMENT '审计ID',
    tenant_id VARCHAR(64) NOT NULL COMMENT '租户ID',
    configuration_id VARCHAR(64) NOT NULL COMMENT '配置ID',
    audit_sequence INT NOT NULL COMMENT '配置内审计序号',
    action VARCHAR(32) NOT NULL COMMENT '配置操作',
    operator_id VARCHAR(64) NOT NULL COMMENT '操作人',
    detail VARCHAR(1000) NULL COMMENT '操作说明',
    before_json LONGTEXT NULL COMMENT '变更前完整JSON快照',
    after_json LONGTEXT NOT NULL COMMENT '变更后完整JSON快照',
    before_hash VARCHAR(64) NULL COMMENT '变更前发布内容哈希',
    after_hash VARCHAR(64) NULL COMMENT '变更后发布内容哈希',
    source_ip VARCHAR(64) NOT NULL COMMENT '来源IP',
    correlation_id VARCHAR(128) NOT NULL COMMENT '请求关联号',
    operation_result VARCHAR(16) NOT NULL COMMENT '操作结果',
    occurred_at DATETIME(6) NOT NULL COMMENT '业务操作时间',
    recorded_at DATETIME(6) NOT NULL COMMENT '审计落库时间',
    PRIMARY KEY (audit_id),
    CONSTRAINT uk_maintenance_config_audit_sequence
        UNIQUE (tenant_id, configuration_id, audit_sequence),
    CONSTRAINT fk_maintenance_config_audit_configuration
        FOREIGN KEY (configuration_id) REFERENCES t_maintenance_item_configuration(configuration_id),
    INDEX idx_maintenance_config_audit_correlation (tenant_id, correlation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保全项配置追加式审计';
--rollback DROP TABLE t_maintenance_item_configuration_audit;
--rollback DROP TABLE t_maintenance_item_configuration;
