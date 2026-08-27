package com.titanium.maintenance.liquibase;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

class MaintenanceWorkflowTaskLiquibaseRollbackTest {

    private static final String CHANGELOG =
            "liquibase/ddl/maintenance_workflow_task_m4_01_202608251020_weisun_ddl.sql";

    @Test
    void shouldCreateTenantScopedUniqueWorkflowTasksAndRollback() throws Exception {
        String databaseName = "maintenance_workflow_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            Liquibase liquibase = new Liquibase(
                    CHANGELOG, new ClassLoaderResourceAccessor(), database);

            liquibase.update(new Contexts(), new LabelExpression());
            assertTrue(tableExists(connection, "t_maintenance_workflow_task_view"));
            insertTask(connection, "task-1", "tenant-1", "case-1", "POLICY_INFO_CHANGE", "DATA_ENTRY");
            assertThrows(SQLException.class, () ->
                    insertTask(connection, "task-2", "tenant-1", "case-1",
                            "POLICY_INFO_CHANGE", "DATA_ENTRY"));

            liquibase.rollback(1, new Contexts(), new LabelExpression());
            assertFalse(tableExists(connection, "t_maintenance_workflow_task_view"));
        }
    }

    private void insertTask(
            Connection connection,
            String taskId,
            String tenantId,
            String maintenanceId,
            String itemCode,
            String stepType) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO t_maintenance_workflow_task_view (
                    task_id, maintenance_id, item_code, item_order, step_sequence,
                    step_type, step_mode, task_status, tenant_id, create_time, update_time)
                VALUES (?, ?, ?, 0, 1, ?, 'REQUIRED', 'READY', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)) {
            statement.setString(1, taskId);
            statement.setString(2, maintenanceId);
            statement.setString(3, itemCode);
            statement.setString(4, stepType);
            statement.setString(5, tenantId);
            statement.executeUpdate();
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_NAME) = LOWER(?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) == 1;
            }
        }
    }
}
