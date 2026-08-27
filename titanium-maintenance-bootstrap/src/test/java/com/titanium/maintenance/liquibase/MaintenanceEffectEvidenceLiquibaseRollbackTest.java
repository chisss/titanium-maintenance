package com.titanium.maintenance.liquibase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

class MaintenanceEffectEvidenceLiquibaseRollbackTest {

    private static final String CHANGELOG =
            "liquibase/ddl/maintenance_effect_evidence_m5_01_202608251500_weisun_ddl.sql";
    private static final String TASK_TABLE = "t_maintenance_workflow_task_view";
    private static final List<String> TASK_COLUMNS = List.of(
            "effect_request_id",
            "effect_request_hash",
            "effect_expected_policy_version",
            "effect_time_type",
            "effect_requested_effective_at",
            "effect_proposed_snapshot_hash",
            "effect_requested_at",
            "policy_endorsement_no",
            "policy_actual_version",
            "policy_application_hash",
            "applied_snapshot_storage_key",
            "applied_snapshot_hash",
            "applied_snapshot_policy_version",
            "applied_snapshot_captured_at",
            "applied_fields_json",
            "policy_applied_at");

    @Test
    void shouldBackfillLegacyCaseAndRollbackOnlyEffectEvidence() throws Exception {
        String databaseName = "maintenance_effect_"
                + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            createBaseline(connection);
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            Liquibase liquibase = new Liquibase(
                    CHANGELOG, new ClassLoaderResourceAccessor(), database);

            liquibase.update(new Contexts(), new LabelExpression());

            assertEquals("NOT_STARTED", effectStatus(connection, "case-legacy"));
            for (String column : TASK_COLUMNS) {
                assertTrue(columnExists(connection, TASK_TABLE, column), column + " 应已创建");
            }

            liquibase.rollback(1, new Contexts(), new LabelExpression());

            assertTrue(tableExists(connection, "t_maintenance_view"));
            assertTrue(tableExists(connection, TASK_TABLE));
            assertFalse(columnExists(connection, "t_maintenance_view", "effect_status"));
            for (String column : TASK_COLUMNS) {
                assertFalse(columnExists(connection, TASK_TABLE, column), column + " 应已回滚");
            }
        }
    }

    private void createBaseline(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE t_maintenance_view (maintenance_id VARCHAR(64) PRIMARY KEY)");
            statement.execute("INSERT INTO t_maintenance_view (maintenance_id) VALUES ('case-legacy')");
            statement.execute("CREATE TABLE " + TASK_TABLE + " (task_id VARCHAR(191) PRIMARY KEY)");
        }
    }

    private String effectStatus(Connection connection, String maintenanceId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT effect_status FROM t_maintenance_view WHERE maintenance_id = ?")) {
            statement.setString(1, maintenanceId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getString(1);
            }
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

    private boolean columnExists(Connection connection, String tableName, String columnName)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE LOWER(TABLE_NAME) = LOWER(?) AND LOWER(COLUMN_NAME) = LOWER(?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) == 1;
            }
        }
    }
}
