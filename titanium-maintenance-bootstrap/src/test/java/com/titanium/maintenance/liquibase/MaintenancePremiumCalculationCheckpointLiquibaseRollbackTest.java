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
import java.util.UUID;

import org.junit.jupiter.api.Test;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

class MaintenancePremiumCalculationCheckpointLiquibaseRollbackTest {

    private static final String CHANGELOG =
            "liquibase/ddl/maintenance_premium_calculation_checkpoint_m5_11_202608271730_weisun_ddl.sql";
    private static final String COLUMN = "premium_calculation_checkpoint_conflict";

    @Test
    void shouldAddFalseConflictMarkerAndRemoveItOnRollback() throws Exception {
        String databaseName = "maintenance_premium_checkpoint_"
                + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            createBaseline(connection);
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            Liquibase liquibase = update(database);

            assertTrue(columnExists(connection));
            assertFalse(readConflictMarker(connection));
            assertEquals(128, calculationIdLength(connection));
            assertEquals("original-calculation", readCalculationId(connection, "original_calculation_id"));
            assertEquals("replacement-calculation", readCalculationId(
                    connection, "replacement_calculation_id"));

            liquibase.rollback(1, new Contexts(), new LabelExpression());
            assertFalse(columnExists(connection));
            assertEquals(64, calculationIdLength(connection));
        }
    }

    private void createBaseline(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE t_maintenance_view ("
                    + "maintenance_id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(32) NOT NULL, "
                    + "original_calculation_id VARCHAR(64), replacement_calculation_id VARCHAR(64))");
            statement.execute("CREATE TABLE t_maintenance_workflow_task_view ("
                    + "task_id VARCHAR(191) PRIMARY KEY, maintenance_id VARCHAR(64) NOT NULL, "
                    + "tenant_id VARCHAR(32) NOT NULL, premium_quote_status VARCHAR(32), "
                    + "premium_quote_original_calculation_id VARCHAR(128), "
                    + "premium_quote_replacement_calculation_id VARCHAR(128))");
            statement.execute("INSERT INTO t_maintenance_view (maintenance_id, tenant_id) "
                    + "VALUES ('case-1', 'tenant-1')");
            statement.execute("INSERT INTO t_maintenance_workflow_task_view VALUES ("
                    + "'task-1', 'case-1', 'tenant-1', 'QUOTED', "
                    + "'original-calculation', 'replacement-calculation')");
        }
    }

    private Liquibase update(Database database) throws Exception {
        Liquibase liquibase = new Liquibase(
                CHANGELOG, new ClassLoaderResourceAccessor(), database);
        liquibase.update(new Contexts(), new LabelExpression());
        return liquibase;
    }

    private boolean readConflictMarker(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT " + COLUMN + " FROM t_maintenance_view WHERE maintenance_id = 'case-1'")) {
            resultSet.next();
            return resultSet.getBoolean(1);
        }
    }

    private String readCalculationId(Connection connection, String column) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT " + column + " FROM t_maintenance_view WHERE maintenance_id = 'case-1'")) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private int calculationIdLength(Connection connection) throws SQLException {
        String sql = "SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE LOWER(TABLE_NAME) = LOWER(?) AND LOWER(COLUMN_NAME) = LOWER(?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "t_maintenance_view");
            statement.setString(2, "original_calculation_id");
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private boolean columnExists(Connection connection) throws SQLException {
        String sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE LOWER(TABLE_NAME) = LOWER(?) AND LOWER(COLUMN_NAME) = LOWER(?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "t_maintenance_view");
            statement.setString(2, COLUMN);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) == 1;
            }
        }
    }
}
