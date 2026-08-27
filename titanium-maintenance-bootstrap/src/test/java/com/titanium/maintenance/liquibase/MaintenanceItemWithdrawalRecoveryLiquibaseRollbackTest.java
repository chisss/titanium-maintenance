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

class MaintenanceItemWithdrawalRecoveryLiquibaseRollbackTest {

    private static final String CHANGELOG =
            "liquibase/ddl/maintenance_item_withdrawal_recovery_m5_07a_202608262215_weisun_ddl.sql";
    private static final List<String> COLUMNS = List.of(
            "withdrawal_payment_method", "withdrawal_recovery_configured_at", "withdrawal_recovery_lease_owner",
            "withdrawal_recovery_lease_until");

    @Test
    void shouldAddAndRollbackWithdrawalRecoveryLeaseProjection() throws Exception {
        String databaseName = "maintenance_withdrawal_recovery_"
                + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE t_maintenance_case_item_view (
                            item_view_id VARCHAR(191) PRIMARY KEY,
                            withdrawal_status VARCHAR(32),
                            update_time TIMESTAMP)
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
                    null, null, "T_MAINTENANCE_CASE_ITEM_VIEW", column.toUpperCase())) {
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
