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

class MaintenanceRetroactiveImpactLiquibaseRollbackTest {

    private static final String CHANGELOG =
            "liquibase/ddl/maintenance_retroactive_impact_m5_05b_202608251930_weisun_ddl.sql";
    private static final List<String> COLUMNS = List.of(
            "retroactive_impact_analysis_id", "retroactive_impact_analysis_version",
            "retroactive_impact_operation_id", "retroactive_impact_request_hash",
            "retroactive_impact_scope_from", "retroactive_impact_scope_to", "retroactive_impact_status",
            "retroactive_impact_covered_domains", "retroactive_impact_item_count",
            "retroactive_impact_blocking_count", "retroactive_impact_pending_count",
            "retroactive_impact_evidence_version", "retroactive_impact_result_hash",
            "retroactive_impact_failure_code", "retroactive_impact_failure_message",
            "retroactive_impact_started_at", "retroactive_impact_completed_at", "retroactive_impact_updated_at");

    @Test
    void shouldAddAndRollbackRetroactiveImpactProjection() throws Exception {
        String databaseName = "maintenance_retroactive_impact_"
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
            assertTrue(tableExists(connection, "T_MAINTENANCE_RETROACTIVE_IMPACT_VIEW"));

            liquibase.rollback(1, new Contexts(), new LabelExpression());
            assertFalse(tableExists(connection, "T_MAINTENANCE_RETROACTIVE_IMPACT_VIEW"));
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
