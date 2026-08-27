--liquibase formatted sql
--changeset weisun:maintenance-case-projection-m3-06-202608241900
ALTER TABLE t_maintenance_view
    ADD COLUMN case_source VARCHAR(16) COMMENT '独立案件来源';
ALTER TABLE t_maintenance_view
    ADD COLUMN client_request_key VARCHAR(128) COMMENT '来源空间内幂等键';
ALTER TABLE t_maintenance_view
    ADD COLUMN request_fingerprint VARCHAR(64) COMMENT '建案请求指纹';
ALTER TABLE t_maintenance_view
    ADD COLUMN independent_case BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否独立建案';
ALTER TABLE t_maintenance_view
    ADD COLUMN initialization_completed BOOLEAN NOT NULL DEFAULT FALSE COMMENT '初始化是否完成';
ALTER TABLE t_maintenance_view
    ADD COLUMN initialization_completed_at DATETIME COMMENT '初始化完成时间';
ALTER TABLE t_maintenance_view
    ADD COLUMN planned_item_count INT NOT NULL DEFAULT 0 COMMENT '计划保全项数量';
ALTER TABLE t_maintenance_view
    ADD COLUMN policy_number VARCHAR(64) COMMENT '保单号';
ALTER TABLE t_maintenance_view
    ADD COLUMN product_id VARCHAR(64) COMMENT '产品ID';
ALTER TABLE t_maintenance_view
    ADD COLUMN product_version VARCHAR(64) COMMENT '产品版本';
ALTER TABLE t_maintenance_view
    ADD COLUMN plan_version VARCHAR(64) COMMENT '计划版本';
ALTER TABLE t_maintenance_view
    ADD COLUMN policy_baseline_version BIGINT COMMENT 'Policy业务基准版本';
ALTER TABLE t_maintenance_view
    ADD COLUMN business_effective_at VARCHAR(40) COMMENT 'Policy业务有效时点及偏移';
CREATE UNIQUE INDEX uk_maintenance_case_idempotency
    ON t_maintenance_view (tenant_id, case_source, client_request_key);
CREATE INDEX idx_maintenance_case_visible
    ON t_maintenance_view (tenant_id, independent_case, initialization_completed, create_time);
CREATE INDEX idx_maintenance_case_policy_number
    ON t_maintenance_view (tenant_id, policy_number);

CREATE TABLE t_maintenance_case_item_view (
    item_view_id VARCHAR(191) NOT NULL COMMENT '案件与保全项稳定组合ID',
    maintenance_id VARCHAR(64) NOT NULL COMMENT '保全案件ID',
    item_code VARCHAR(64) NOT NULL COMMENT '保全项编码',
    item_name VARCHAR(128) NOT NULL COMMENT '保全项名称',
    item_category VARCHAR(32) NOT NULL COMMENT '保全项分类',
    configuration_id VARCHAR(64) COMMENT '保全配置ID',
    configuration_version VARCHAR(64) NOT NULL COMMENT '保全配置版本',
    configuration_content_hash VARCHAR(64) COMMENT '保全配置内容摘要',
    offering_id VARCHAR(64) COMMENT 'Product Offering ID',
    offering_version VARCHAR(64) COMMENT 'Product Offering版本',
    offering_content_hash VARCHAR(64) COMMENT 'Product Offering内容摘要',
    evidence_resolved_at VARCHAR(40) COMMENT '权威证据解析时间及偏移',
    selected_at DATETIME NOT NULL COMMENT '项目冻结时间',
    tenant_id VARCHAR(32) NOT NULL COMMENT '租户ID',
    version BIGINT COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL COMMENT '投影创建时间',
    update_time DATETIME NOT NULL COMMENT '投影更新时间',
    PRIMARY KEY (item_view_id),
    UNIQUE KEY uk_maintenance_case_item (tenant_id, maintenance_id, item_code),
    KEY idx_maintenance_case_item_filter (tenant_id, item_code, maintenance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保全独立案件项目投影';

CREATE TABLE t_maintenance_field_change_view (
    field_change_id VARCHAR(64) NOT NULL COMMENT '字段变化稳定摘要ID',
    maintenance_id VARCHAR(64) NOT NULL COMMENT '保全案件ID',
    item_code VARCHAR(64) NOT NULL COMMENT '所属保全项',
    object_id VARCHAR(128) NOT NULL COMMENT '稳定业务对象ID',
    field_code VARCHAR(128) NOT NULL COMMENT 'Policy字段编码',
    label_key VARCHAR(191) COMMENT '字段国际化标签键',
    data_type VARCHAR(16) NOT NULL COMMENT '字段值类型',
    base_value LONGTEXT COMMENT '案件基准值',
    current_value LONGTEXT COMMENT '草稿录入时Policy当前值',
    proposed_value LONGTEXT COMMENT '拟变更值',
    applied_value LONGTEXT COMMENT 'Policy实际生效值',
    conflict_status VARCHAR(16) NOT NULL COMMENT '顺序外冲突状态',
    resolution_code VARCHAR(64) COMMENT '冲突解决方式',
    sensitivity VARCHAR(16) COMMENT '字段敏感级别',
    masking_policy VARCHAR(24) COMMENT '字段掩码策略',
    change_type_code VARCHAR(64) COMMENT 'Policy业务变更类别',
    tenant_id VARCHAR(32) NOT NULL COMMENT '租户ID',
    version BIGINT COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL COMMENT '投影创建时间',
    update_time DATETIME NOT NULL COMMENT '投影更新时间',
    PRIMARY KEY (field_change_id),
    UNIQUE KEY uk_maintenance_field_change (tenant_id, maintenance_id, item_code, object_id, field_code),
    KEY idx_maintenance_field_case (tenant_id, maintenance_id, item_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保全案件字段差异投影';

CREATE TABLE t_maintenance_snapshot_view (
    maintenance_id VARCHAR(64) NOT NULL COMMENT '保全案件ID',
    before_storage_key VARCHAR(512) COMMENT 'before快照引用',
    before_content_hash VARCHAR(64) COMMENT 'before快照摘要',
    before_policy_version BIGINT COMMENT 'before Policy版本',
    before_captured_at VARCHAR(40) COMMENT 'before采集时间及偏移',
    proposed_storage_key VARCHAR(512) COMMENT 'proposed快照引用',
    proposed_content_hash VARCHAR(64) COMMENT 'proposed快照摘要',
    proposed_policy_version BIGINT COMMENT 'proposed Policy版本',
    proposed_captured_at VARCHAR(40) COMMENT 'proposed采集时间及偏移',
    applied_storage_key VARCHAR(512) COMMENT 'applied快照引用',
    applied_content_hash VARCHAR(64) COMMENT 'applied快照摘要',
    applied_policy_version BIGINT COMMENT 'applied Policy版本',
    applied_captured_at VARCHAR(40) COMMENT 'applied采集时间及偏移',
    tenant_id VARCHAR(32) NOT NULL COMMENT '租户ID',
    version BIGINT COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL COMMENT '投影创建时间',
    update_time DATETIME NOT NULL COMMENT '投影更新时间',
    PRIMARY KEY (maintenance_id),
    KEY idx_maintenance_snapshot_tenant (tenant_id, maintenance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保全案件快照引用投影';

--rollback DROP TABLE IF EXISTS t_maintenance_snapshot_view;
--rollback DROP TABLE IF EXISTS t_maintenance_field_change_view;
--rollback DROP TABLE IF EXISTS t_maintenance_case_item_view;
--rollback DROP INDEX idx_maintenance_case_policy_number ON t_maintenance_view;
--rollback DROP INDEX idx_maintenance_case_visible ON t_maintenance_view;
--rollback DROP INDEX uk_maintenance_case_idempotency ON t_maintenance_view;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN business_effective_at;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN policy_baseline_version;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN plan_version;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN product_version;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN product_id;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN policy_number;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN planned_item_count;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN initialization_completed_at;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN initialization_completed;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN independent_case;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN request_fingerprint;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN client_request_key;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN case_source;
