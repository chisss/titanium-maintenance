package com.titanium.maintenance.liquibase;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

class MaintenanceWorkflowOperationIdCompatibilityLiquibaseRollbackTest {

    private static final String TASK_CHANGELOG =
            "liquibase/ddl/maintenance_workflow_task_m4_01_202608251020_weisun_ddl.sql";
    private static final String TRANSITION_CHANGELOG =
            "liquibase/ddl/maintenance_workflow_transition_m4_02_202608251130_weisun_ddl.sql";
    private static final String COMPATIBILITY_CHANGELOG =
            "liquibase/ddl/maintenance_workflow_operation_id_compatibility_m5_09_202608261745_weisun_ddl.sql";

    @Test
    void shouldWidenLastOperationIdAndRestoreOriginalLengthOnRollback() throws Exception {
        String databaseName = "maintenance_operation_id_"
                + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            update(database, TASK_CHANGELOG);
            update(database, TRANSITION_CHANGELOG);

            Liquibase compatibility = update(database, COMPATIBILITY_CHANGELOG);
            assertEquals(256, columnLength(connection));

            compatibility.rollback(1, new Contexts(), new LabelExpression());
            assertEquals(128, columnLength(connection));
        }
    }

    private Liquibase update(Database database, String changelog) throws Exception {
        Liquibase liquibase = new Liquibase(
                changelog, new ClassLoaderResourceAccessor(), database);
        liquibase.update(new Contexts(), new LabelExpression());
        return liquibase;
    }

    private int columnLength(Connection connection) throws SQLException {
        String sql = "SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE LOWER(TABLE_NAME) = LOWER(?) AND LOWER(COLUMN_NAME) = LOWER(?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "t_maintenance_workflow_task_view");
            statement.setString(2, "last_operation_id");
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }
}
