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

class MaintenanceFieldConflictLiquibaseRollbackTest {

    private static final String CHANGELOG =
            "liquibase/ddl/maintenance_field_conflict_m5_06a_202608261730_weisun_ddl.sql";
    private static final List<String> COLUMNS = List.of(
            "conflict_operation_id", "conflict_detected_at", "conflict_policy_version",
            "conflict_evidence_hash", "resolution_operation_id", "resolution_reason",
            "resolution_evidence_hash", "resolved_by", "resolved_at");

    @Test
    void shouldAddAndRollbackFieldConflictAuditProjection() throws Exception {
        String databaseName = "maintenance_field_conflict_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE t_maintenance_field_change_view (
                            field_change_id VARCHAR(64) PRIMARY KEY,
                            tenant_id VARCHAR(64) NOT NULL,
                            maintenance_id VARCHAR(64) NOT NULL,
                            conflict_status VARCHAR(16) NOT NULL
                        )
                        """);
            }
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            Liquibase liquibase = new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database);

            liquibase.update(new Contexts(), new LabelExpression());
            COLUMNS.forEach(column -> assertColumn(connection, column, true));

            liquibase.rollback(1, new Contexts(), new LabelExpression());
            COLUMNS.forEach(column -> assertColumn(connection, column, false));
        }
    }

    private void assertColumn(Connection connection, String column, boolean expected) {
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet columns = metaData.getColumns(
                    null, null, "T_MAINTENANCE_FIELD_CHANGE_VIEW", column.toUpperCase())) {
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
