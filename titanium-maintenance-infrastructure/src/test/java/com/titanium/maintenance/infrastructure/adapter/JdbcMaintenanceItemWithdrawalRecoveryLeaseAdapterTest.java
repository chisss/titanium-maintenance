package com.titanium.maintenance.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.titanium.maintenance.port.MaintenanceItemWithdrawalRecoveryLeasePort.WithdrawalRecoveryLease;

class JdbcMaintenanceItemWithdrawalRecoveryLeaseAdapterTest {

    private JdbcTemplate jdbcTemplate;
    private JdbcMaintenanceItemWithdrawalRecoveryLeaseAdapter firstNode;
    private JdbcMaintenanceItemWithdrawalRecoveryLeaseAdapter secondNode;

    @BeforeEach
    void setUp() {
        String database = "maintenance_withdrawal_recovery_"
                + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + database + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE t_maintenance_case_item_view (
                    item_view_id VARCHAR(191) PRIMARY KEY,
                    maintenance_id VARCHAR(64) NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL,
                    item_code VARCHAR(64) NOT NULL,
                    withdrawal_status VARCHAR(32),
                    withdrawal_operation_id VARCHAR(128),
                    withdrawal_reason VARCHAR(500),
                    withdrawal_payment_method VARCHAR(64),
                    withdrawal_recovery_configured_at TIMESTAMP,
                    withdrawal_retry_count INT,
                    withdrawal_recovery_lease_owner VARCHAR(128),
                    withdrawal_recovery_lease_until TIMESTAMP,
                    update_time TIMESTAMP)
                """);
        insert("item-view-1", "FAILED", 1);
        firstNode = new JdbcMaintenanceItemWithdrawalRecoveryLeaseAdapter(jdbcTemplate);
        secondNode = new JdbcMaintenanceItemWithdrawalRecoveryLeaseAdapter(jdbcTemplate);
    }

    @Test
    void shouldAllowOnlyOneNodeToAcquireAndAllowNextNodeAfterRelease() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 15, 0);
        LocalDateTime retryBefore = now.minusMinutes(5);

        List<WithdrawalRecoveryLease> first = firstNode.acquireDue(
                "node-1", now, retryBefore, now.plusMinutes(2), 10, 5);
        List<WithdrawalRecoveryLease> concurrent = secondNode.acquireDue(
                "node-2", now, retryBefore, now.plusMinutes(2), 10, 5);

        assertEquals(1, first.size());
        assertEquals("withdraw-operation-1", first.getFirst().operationId());
        assertEquals("BANK_CARD", first.getFirst().paymentMethod());
        assertTrue(concurrent.isEmpty());

        firstNode.release("item-view-1", "node-1");
        assertEquals(1, secondNode.acquireDue(
                "node-2", now, retryBefore, now.plusMinutes(2), 10, 5).size());
    }

    @Test
    void shouldExcludeFailedWithdrawalAfterMaximumAttempts() {
        jdbcTemplate.update("""
                UPDATE t_maintenance_case_item_view
                   SET withdrawal_retry_count = 5
                 WHERE item_view_id = ?
                """, "item-view-1");
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 15, 0);

        assertTrue(firstNode.acquireDue(
                "node-1", now, now.minusMinutes(5), now.plusMinutes(2), 10, 5).isEmpty());
    }

    @Test
    void shouldAcquireStaleRequestedWithdrawalAfterRecoveryContextWasConfigured() {
        insert("item-view-2", "REQUESTED", 0);
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 15, 0);

        List<WithdrawalRecoveryLease> leases = firstNode.acquireDue(
                "node-1", now, now.minusMinutes(5), now.plusMinutes(2), 10, 5);

        assertEquals(2, leases.size());
    }

    private void insert(String itemViewId, String status, int retryCount) {
        jdbcTemplate.update("""
                INSERT INTO t_maintenance_case_item_view (
                    item_view_id, maintenance_id, tenant_id, item_code, withdrawal_status,
                    withdrawal_operation_id, withdrawal_reason, withdrawal_payment_method,
                    withdrawal_recovery_configured_at, withdrawal_retry_count, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, itemViewId, "case-1", "tenant-1", "ITEM_A", status,
                "withdraw-operation-1", "客户取消项目", "BANK_CARD",
                LocalDateTime.of(2026, 8, 26, 14, 49), retryCount,
                LocalDateTime.of(2026, 8, 26, 14, 50));
    }
}
