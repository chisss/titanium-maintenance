package com.titanium.maintenance.liquibase;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
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

class MaintenanceCaseAtomicEffectLiquibaseRollbackTest {

    private static final String CHANGELOG =
            "liquibase/ddl/maintenance_case_atomic_effect_m5_03_202608251930_weisun_ddl.sql";
    private static final List<String> COLUMNS = List.of(
            "policy_state_action", "policy_status_before", "policy_status_after");

    @Test
    void shouldAddAndRollbackStateApplicationEvidenceColumns() throws Exception {
        String databaseName = "maintenance_atomic_effect_"
                + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE t_maintenance_workflow_task_view (task_id VARCHAR(128) PRIMARY KEY)");
            }
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            Liquibase liquibase = new Liquibase(
                    CHANGELOG, new ClassLoaderResourceAccessor(), database);

            liquibase.update(new Contexts(), new LabelExpression());
            for (String column : COLUMNS) {
                assertTrue(columnExists(connection, column), column + " 应已创建");
            }

            liquibase.rollback(1, new Contexts(), new LabelExpression());
            for (String column : COLUMNS) {
                assertFalse(columnExists(connection, column), column + " 应已回滚");
            }
        }
    }

    private boolean columnExists(Connection connection, String column) throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet columns = metaData.getColumns(null, null,
                "T_MAINTENANCE_WORKFLOW_TASK_VIEW", column.toUpperCase())) {
            return columns.next();
        }
    }
}
