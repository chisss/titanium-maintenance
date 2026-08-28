package com.titanium.maintenance.liquibase;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

class MaintenanceConfigurationAuditRetentionLiquibaseRollbackTest {

    private static final String BASE_CHANGELOG =
            "liquibase/ddl/maintenance_item_configuration_v2c_202608241230_weisun_ddl.sql";
    private static final String RETENTION_CHANGELOG =
            "liquibase/ddl/maintenance_configuration_audit_retention_202608281305_weisun_ddl.sql";
    private static final String FOREIGN_KEY = "fk_maintenance_config_audit_configuration";

    @Test
    void shouldDetachAuditHistoryAndRestoreConstraintOnRollback() throws Exception {
        String databaseName = "maintenance_configuration_audit_"
                + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            update(BASE_CHANGELOG, database);
            assertTrue(foreignKeyExists(connection));

            Liquibase retention = update(RETENTION_CHANGELOG, database);
            assertFalse(foreignKeyExists(connection));

            retention.rollback(1, new Contexts(), new LabelExpression());
            assertTrue(foreignKeyExists(connection));
        }
    }

    private Liquibase update(String changelog, Database database) throws Exception {
        Liquibase liquibase = new Liquibase(
                changelog, new ClassLoaderResourceAccessor(), database);
        liquibase.update(new Contexts(), new LabelExpression());
        return liquibase;
    }

    private boolean foreignKeyExists(Connection connection) throws Exception {
        String sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                + "WHERE LOWER(CONSTRAINT_NAME) = LOWER(?) AND CONSTRAINT_TYPE = 'FOREIGN KEY'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, FOREIGN_KEY);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) == 1;
            }
        }
    }
}
