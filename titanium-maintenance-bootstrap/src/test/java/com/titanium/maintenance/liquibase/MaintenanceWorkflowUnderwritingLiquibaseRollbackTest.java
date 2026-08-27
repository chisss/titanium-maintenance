package com.titanium.maintenance.liquibase;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

class MaintenanceWorkflowUnderwritingLiquibaseRollbackTest {

    private static final String BASE_CHANGELOG =
            "liquibase/ddl/maintenance_workflow_task_m4_01_202608251020_weisun_ddl.sql";
    private static final String UNDERWRITING_CHANGELOG =
            "liquibase/ddl/maintenance_workflow_underwriting_m4_04_202608251520_weisun_ddl.sql";

    @Test
    void shouldRollbackUnderwritingColumnsAndKeepWorkflowTable() throws Exception {
        String databaseName = "maintenance_underwriting_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            update(database, BASE_CHANGELOG);
            Liquibase underwriting = update(database, UNDERWRITING_CHANGELOG);

            assertTrue(columnExists(connection, "t_maintenance_workflow_task_view", "underwriting_case_id"));
            assertTrue(columnExists(connection, "t_maintenance_workflow_task_view", "underwriting_conclusion"));

            underwriting.rollback(1, new Contexts(), new LabelExpression());
            assertTrue(columnExists(connection, "t_maintenance_workflow_task_view", "task_status"));
            assertFalse(columnExists(connection, "t_maintenance_workflow_task_view", "underwriting_case_id"));
        }
    }

    private Liquibase update(Database database, String changelog) throws Exception {
        Liquibase liquibase = new Liquibase(changelog, new ClassLoaderResourceAccessor(), database);
        liquibase.update(new Contexts(), new LabelExpression());
        return liquibase;
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
