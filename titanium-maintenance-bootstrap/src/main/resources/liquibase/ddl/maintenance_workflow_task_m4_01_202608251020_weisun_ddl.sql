--liquibase formatted sql
--changeset weisun:maintenance-workflow-task-m4-01-202608251020
CREATE TABLE t_maintenance_workflow_task_view (
    task_id VARCHAR(191) NOT NULL COMMENT '案件项目步骤稳定任务ID',
    maintenance_id VARCHAR(64) NOT NULL COMMENT '保全案件ID',
    item_code VARCHAR(64) NOT NULL COMMENT '来源保全项编码',
    item_order INT NOT NULL COMMENT '建案项目选择顺序',
    step_sequence INT NOT NULL COMMENT '项目内步骤顺序',
    step_type VARCHAR(32) NOT NULL COMMENT '标准步骤类型',
    step_mode VARCHAR(16) NOT NULL COMMENT '必需、条件或跳过',
    condition_rule_code VARCHAR(128) COMMENT '条件规则引用',
    task_status VARCHAR(32) NOT NULL COMMENT '任务运行状态',
    tenant_id VARCHAR(32) NOT NULL COMMENT '租户ID',
    version BIGINT COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL COMMENT '投影创建时间',
    update_time DATETIME NOT NULL COMMENT '投影更新时间',
    PRIMARY KEY (task_id),
    UNIQUE KEY uk_maintenance_workflow_task (
        tenant_id, maintenance_id, item_code, step_type),
    KEY idx_maintenance_workflow_case (
        tenant_id, maintenance_id, step_sequence, item_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保全案件流程任务投影';

--rollback DROP TABLE IF EXISTS t_maintenance_workflow_task_view;
