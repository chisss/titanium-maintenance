package com.titanium.maintenance.liquibase;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

class MaintenanceEffectTimePrecisionLiquibaseRollbackTest {

    private static final String CHANGELOG =
            "liquibase/ddl/maintenance_effect_time_precision_m5_10_202608261755_weisun_ddl.sql";

    @Test
    void shouldStoreNanosecondTimeAndRestoreOriginalTypeOnRollback() throws Exception {
        String databaseName = "maintenance_effect_time_"
                + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            createBaseline(connection);
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            Liquibase liquibase = update(database);

            assertEquals(40, columnLength(connection));
            String expected = "2026-08-26T17:23:22.171414751";
            writeValue(connection, expected);
            assertEquals(expected, readValue(connection));

            clearValue(connection);
            liquibase.rollback(1, new Contexts(), new LabelExpression());
            assertEquals("TIMESTAMP", columnType(connection));
        }
    }

    private void createBaseline(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE t_maintenance_workflow_task_view ("
                    + "task_id VARCHAR(191) PRIMARY KEY, effect_requested_effective_at DATETIME)");
            statement.execute("INSERT INTO t_maintenance_workflow_task_view (task_id) VALUES ('effect-task')");
        }
    }

    private Liquibase update(Database database) throws Exception {
        Liquibase liquibase = new Liquibase(
                CHANGELOG, new ClassLoaderResourceAccessor(), database);
        liquibase.update(new Contexts(), new LabelExpression());
        return liquibase;
    }

    private void writeValue(Connection connection, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE t_maintenance_workflow_task_view SET effect_requested_effective_at = ?")) {
            statement.setString(1, value);
            statement.executeUpdate();
        }
    }

    private String readValue(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT effect_requested_effective_at FROM t_maintenance_workflow_task_view")) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private void clearValue(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "UPDATE t_maintenance_workflow_task_view SET effect_requested_effective_at = NULL");
        }
    }

    private int columnLength(Connection connection) throws SQLException {
        String sql = "SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE LOWER(TABLE_NAME) = LOWER(?) AND LOWER(COLUMN_NAME) = LOWER(?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindColumn(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private String columnType(Connection connection) throws SQLException {
        String sql = "SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE LOWER(TABLE_NAME) = LOWER(?) AND LOWER(COLUMN_NAME) = LOWER(?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindColumn(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getString(1);
            }
        }
    }

    private void bindColumn(PreparedStatement statement) throws SQLException {
        statement.setString(1, "t_maintenance_workflow_task_view");
        statement.setString(2, "effect_requested_effective_at");
    }
}
