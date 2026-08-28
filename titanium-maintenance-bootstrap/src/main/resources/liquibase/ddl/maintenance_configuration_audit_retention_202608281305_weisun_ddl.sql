--liquibase formatted sql
--changeset weisun:maintenance-configuration-audit-retention-202608281305
ALTER TABLE t_maintenance_item_configuration_audit
    DROP FOREIGN KEY fk_maintenance_config_audit_configuration;

--rollback ALTER TABLE t_maintenance_item_configuration_audit ADD CONSTRAINT fk_maintenance_config_audit_configuration FOREIGN KEY (configuration_id) REFERENCES t_maintenance_item_configuration(configuration_id);
