--liquibase formatted sql
--changeset weisun:maintenance-no-1
ALTER TABLE t_maintenance_view ADD COLUMN maintenance_no VARCHAR(32) NULL COMMENT '保全号（系统生成）';
CREATE UNIQUE INDEX uk_maintenance_view_no ON t_maintenance_view (tenant_id, maintenance_no);
--rollback DROP INDEX uk_maintenance_view_no ON t_maintenance_view;
--rollback ALTER TABLE t_maintenance_view DROP COLUMN maintenance_no;
