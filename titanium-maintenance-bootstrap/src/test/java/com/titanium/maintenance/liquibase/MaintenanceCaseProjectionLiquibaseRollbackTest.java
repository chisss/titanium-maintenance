package com.titanium.maintenance.liquibase;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

class MaintenanceCaseProjectionLiquibaseRollbackTest {

    private static final String CHANGELOG =
            "liquibase/ddl/maintenance_case_projection_m3_06_202608241900_weisun_ddl.sql";

    @Test
    void shouldCreateUniqueIdempotencyIndexAndRollbackOnlyProjectionExtension() throws Exception {
        String databaseName = "maintenance_case_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            createBaselineView(connection);
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            Liquibase liquibase = new Liquibase(
                    CHANGELOG, new ClassLoaderResourceAccessor(), database);

            liquibase.update(new Contexts(), new LabelExpression());
            assertTrue(tableExists(connection, "t_maintenance_case_item_view"));
            assertTrue(tableExists(connection, "t_maintenance_field_change_view"));
            assertTrue(tableExists(connection, "t_maintenance_snapshot_view"));
            insertCase(connection, "case-1", "tenant-1", "MANUAL", "request-1");
            assertThrows(SQLException.class, () ->
                    insertCase(connection, "case-2", "tenant-1", "MANUAL", "request-1"));

            liquibase.rollback(1, new Contexts(), new LabelExpression());
            assertFalse(tableExists(connection, "t_maintenance_case_item_view"));
            assertFalse(tableExists(connection, "t_maintenance_field_change_view"));
            assertFalse(tableExists(connection, "t_maintenance_snapshot_view"));
            assertTrue(tableExists(connection, "t_maintenance_view"));
            assertFalse(columnExists(connection, "t_maintenance_view", "case_source"));
        }
    }

    private void createBaselineView(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE t_maintenance_view (
                        maintenance_id VARCHAR(64) PRIMARY KEY,
                        customer_id VARCHAR(64),
                        tenant_id VARCHAR(32) NOT NULL,
                        create_time DATETIME NOT NULL
                    )
                    """);
        }
    }

    private void insertCase(
            Connection connection, String caseId, String tenantId, String source, String requestKey)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO t_maintenance_view (
                    maintenance_id, customer_id, tenant_id, create_time, case_source, client_request_key)
                VALUES (?, 'customer-1', ?, CURRENT_TIMESTAMP, ?, ?)
                """)) {
            statement.setString(1, caseId);
            statement.setString(2, tenantId);
            statement.setString(3, source);
            statement.setString(4, requestKey);
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

    private boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
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
