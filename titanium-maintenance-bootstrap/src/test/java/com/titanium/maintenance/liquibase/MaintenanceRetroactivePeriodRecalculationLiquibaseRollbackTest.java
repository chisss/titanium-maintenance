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

class MaintenanceRetroactivePeriodRecalculationLiquibaseRollbackTest {

    private static final String CHANGELOG =
            "liquibase/ddl/maintenance_retroactive_period_recalculation_m5_05c_202608261130_weisun_ddl.sql";
    private static final List<String> COLUMNS = List.of(
            "retroactive_period_recalculation_id", "retroactive_period_recalculation_version",
            "retroactive_period_recalculation_operation_id", "retroactive_period_recalculation_request_hash",
            "retroactive_period_analysis_id", "retroactive_period_analysis_version",
            "retroactive_period_analysis_result_hash", "retroactive_period_recalculation_status",
            "retroactive_product_recalculation_id", "retroactive_product_result_hash",
            "retroactive_period_count", "retroactive_billing_batch_id", "retroactive_billing_status",
            "retroactive_billing_review_count", "retroactive_period_failure_code",
            "retroactive_period_started_at", "retroactive_period_completed_at",
            "retroactive_period_updated_at");

    @Test
    void shouldAddAndRollbackRetroactivePeriodProjection() throws Exception {
        String databaseName = "maintenance_retroactive_period_"
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
                assertTrue(columnExists(connection, "T_MAINTENANCE_VIEW", column), column + " 应已创建");
            }
            assertTrue(tableExists(connection, "T_MAINTENANCE_RETROACTIVE_PERIOD_ADJUSTMENT_VIEW"));

            liquibase.rollback(1, new Contexts(), new LabelExpression());
            assertFalse(tableExists(connection, "T_MAINTENANCE_RETROACTIVE_PERIOD_ADJUSTMENT_VIEW"));
            for (String column : COLUMNS) {
                assertFalse(columnExists(connection, "T_MAINTENANCE_VIEW", column), column + " 应已回滚");
            }
        }
    }

    private boolean columnExists(Connection connection, String table, String column) throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet columns = metaData.getColumns(null, null, table, column.toUpperCase())) {
            return columns.next();
        }
    }

    private boolean tableExists(Connection connection, String table) throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet tables = metaData.getTables(null, null, table, new String[] {"TABLE"})) {
            return tables.next();
        }
    }
}
