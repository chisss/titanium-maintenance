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

class MaintenanceRetroactivePeriodResolutionLiquibaseRollbackTest {

    private static final String CHANGELOG =
            "liquibase/ddl/maintenance_retroactive_period_resolution_m5_05d_202608261430_weisun_ddl.sql";
    private static final List<String> CASE_COLUMNS = List.of(
            "retroactive_period_resolution_id", "retroactive_period_resolution_operation_id",
            "retroactive_period_resolution_request_hash", "retroactive_period_resolution_status",
            "retroactive_billing_resolution_id", "retroactive_period_resolution_source_batch_hash",
            "retroactive_period_resolution_target_period",
            "retroactive_period_resolution_resolved_line_count",
            "retroactive_period_resolution_result_hash", "retroactive_period_resolution_reason",
            "retroactive_period_resolution_failure_code", "retroactive_period_resolution_failure_message",
            "retroactive_period_resolution_started_at", "retroactive_period_resolution_completed_at",
            "retroactive_period_resolution_updated_at");
    private static final List<String> PERIOD_COLUMNS = List.of(
            "target_accounting_period", "resolution_status", "posting_reference",
            "resolution_result_hash");

    @Test
    void shouldAddAndRollbackClosedPeriodResolutionProjection() throws Exception {
        String databaseName = "maintenance_retroactive_resolution_"
                + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE t_maintenance_view (maintenance_id VARCHAR(36) PRIMARY KEY)");
                statement.execute("""
                        CREATE TABLE t_maintenance_retroactive_period_adjustment_view (
                            period_record_id VARCHAR(191) PRIMARY KEY,
                            tenant_id VARCHAR(32) NOT NULL
                        )
                        """);
            }
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            Liquibase liquibase = new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database);

            liquibase.update(new Contexts(), new LabelExpression());
            CASE_COLUMNS.forEach(column -> assertColumn(connection, "T_MAINTENANCE_VIEW", column, true));
            PERIOD_COLUMNS.forEach(column -> assertColumn(
                    connection, "T_MAINTENANCE_RETROACTIVE_PERIOD_ADJUSTMENT_VIEW", column, true));

            liquibase.rollback(1, new Contexts(), new LabelExpression());
            CASE_COLUMNS.forEach(column -> assertColumn(connection, "T_MAINTENANCE_VIEW", column, false));
            PERIOD_COLUMNS.forEach(column -> assertColumn(
                    connection, "T_MAINTENANCE_RETROACTIVE_PERIOD_ADJUSTMENT_VIEW", column, false));
        }
    }

    private void assertColumn(Connection connection, String table, String column, boolean expected) {
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet columns = metaData.getColumns(null, null, table, column.toUpperCase())) {
                if (expected) {
                    assertTrue(columns.next(), column + " 应已创建");
                } else {
                    assertFalse(columns.next(), column + " 应已回滚");
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
