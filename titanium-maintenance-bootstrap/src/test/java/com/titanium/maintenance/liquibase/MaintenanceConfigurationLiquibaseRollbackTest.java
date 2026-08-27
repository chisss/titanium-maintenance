package com.titanium.maintenance.liquibase;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

class MaintenanceConfigurationLiquibaseRollbackTest {

    private static final String CHANGELOG =
            "liquibase/ddl/maintenance_item_configuration_v2c_202608241230_weisun_ddl.sql";

    @Test
    void shouldRollbackOnlyPhaseTwoConfigurationTables() throws Exception {
        String databaseName = "maintenance_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE t_existing_maintenance_case (id VARCHAR(64) PRIMARY KEY)");
            }
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            Liquibase liquibase = new Liquibase(
                    CHANGELOG, new ClassLoaderResourceAccessor(), database);

            liquibase.update(new Contexts(), new LabelExpression());
            assertTrue(tableExists(connection, "t_maintenance_item_configuration"));
            assertTrue(tableExists(connection, "t_maintenance_item_configuration_audit"));

            liquibase.rollback(1, new Contexts(), new LabelExpression());
            assertFalse(tableExists(connection, "t_maintenance_item_configuration"));
            assertFalse(tableExists(connection, "t_maintenance_item_configuration_audit"));
            assertTrue(tableExists(connection, "t_existing_maintenance_case"));
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws Exception {
        String sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_NAME) = LOWER(?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) == 1;
            }
        }
    }
}
