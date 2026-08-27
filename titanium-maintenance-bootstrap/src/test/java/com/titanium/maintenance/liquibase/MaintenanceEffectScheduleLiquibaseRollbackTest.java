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

class MaintenanceEffectScheduleLiquibaseRollbackTest {

    private static final String CHANGELOG =
            "liquibase/ddl/maintenance_effect_schedule_m5_04_202608252300_weisun_ddl.sql";
    private static final List<String> COLUMNS = List.of(
            "effect_schedule_id", "effect_schedule_status", "effect_schedule_tenant_zone_id",
            "effect_schedule_next_execution_at", "effect_schedule_attempt_count",
            "effect_schedule_last_attempt_id", "effect_schedule_last_attempt_at",
            "effect_schedule_last_error_code", "effect_schedule_last_error_message",
            "effect_schedule_created_at", "effect_schedule_updated_at",
            "effect_schedule_lease_owner", "effect_schedule_lease_until");

    @Test
    void shouldAddAndRollbackFutureEffectScheduleColumns() throws Exception {
        String databaseName = "maintenance_effect_schedule_"
                + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE t_maintenance_view (maintenance_id VARCHAR(36) PRIMARY KEY)");
            }
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            Liquibase liquibase = new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database);

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
        try (ResultSet columns = metaData.getColumns(
                null, null, "T_MAINTENANCE_VIEW", column.toUpperCase())) {
            return columns.next();
        }
    }
}
