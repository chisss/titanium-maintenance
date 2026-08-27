package com.titanium.maintenance.liquibase;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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

class MaintenanceWorkflowSettlementLiquibaseRollbackTest {

    private static final String BASE_CHANGELOG =
            "liquibase/ddl/maintenance_workflow_task_m4_01_202608251020_weisun_ddl.sql";
    private static final String SETTLEMENT_CHANGELOG =
            "liquibase/ddl/maintenance_workflow_settlement_m4_06_202608252140_weisun_ddl.sql";
    private static final String TABLE_NAME = "t_maintenance_workflow_task_view";
    private static final List<String> SETTLEMENT_COLUMNS = List.of(
            "billing_posting_id",
            "billing_adjustment_id",
            "billing_result_hash",
            "billing_posting_direction",
            "billing_posting_amount",
            "billing_posting_currency",
            "billing_posting_status",
            "billing_commission_adjustment_count",
            "billing_posted_at",
            "fund_settlement_type",
            "fund_settlement_status",
            "fund_source_posting_id",
            "fund_instruction_id",
            "fund_order_id",
            "fund_external_status",
            "fund_amount",
            "fund_currency",
            "fund_failure_code",
            "fund_failure_message",
            "fund_recorded_at");

    @Test
    void shouldRollbackSettlementColumnsAndKeepWorkflowTable() throws Exception {
        String databaseName = "maintenance_settlement_"
                + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            update(database, BASE_CHANGELOG);
            Liquibase settlement = update(database, SETTLEMENT_CHANGELOG);

            for (String column : SETTLEMENT_COLUMNS) {
                assertTrue(columnExists(connection, TABLE_NAME, column), column + " 应已创建");
            }

            settlement.rollback(1, new Contexts(), new LabelExpression());

            assertTrue(columnExists(connection, TABLE_NAME, "task_status"));
            for (String column : SETTLEMENT_COLUMNS) {
                assertFalse(columnExists(connection, TABLE_NAME, column), column + " 应已回滚");
            }
        }
    }

    private Liquibase update(Database database, String changelog) throws Exception {
        Liquibase liquibase = new Liquibase(changelog, new ClassLoaderResourceAccessor(), database);
        liquibase.update(new Contexts(), new LabelExpression());
        return liquibase;
    }

    private boolean columnExists(Connection connection, String tableName, String columnName)
            throws SQLException {
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
