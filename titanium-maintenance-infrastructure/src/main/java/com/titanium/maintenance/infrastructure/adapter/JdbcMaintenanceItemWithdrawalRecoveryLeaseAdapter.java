package com.titanium.maintenance.infrastructure.adapter;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.maintenance.port.MaintenanceItemWithdrawalRecoveryLeasePort;

import lombok.RequiredArgsConstructor;

/** 通过条件更新实现项目撤销自动恢复的多节点互斥租约。 */
@Component
@RequiredArgsConstructor
public class JdbcMaintenanceItemWithdrawalRecoveryLeaseAdapter
        implements MaintenanceItemWithdrawalRecoveryLeasePort {

    private static final String FAILED = "FAILED";
    private static final String REQUESTED = "REQUESTED";
    private static final String WAITING_FUNDS = "WAITING_FUNDS";

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public List<WithdrawalRecoveryLease> acquireDue(
            String leaseOwner,
            LocalDateTime now,
            LocalDateTime retryBefore,
            LocalDateTime leaseUntil,
            int batchSize,
            int maxAttempts) {
        List<String> candidates = jdbcTemplate.queryForList("""
                SELECT item_view_id
                  FROM t_maintenance_case_item_view
                 WHERE withdrawal_status IN (?, ?, ?)
                   AND withdrawal_recovery_configured_at IS NOT NULL
                   AND update_time <= ?
                   AND (withdrawal_status <> ? OR COALESCE(withdrawal_retry_count, 0) < ?)
                   AND (withdrawal_recovery_lease_until IS NULL OR withdrawal_recovery_lease_until < ?)
                 ORDER BY update_time, item_view_id
                 LIMIT ?
                """, String.class, REQUESTED, FAILED, WAITING_FUNDS, retryBefore,
                FAILED, maxAttempts, now, batchSize);
        return candidates.stream()
                .filter(itemViewId -> acquire(itemViewId, leaseOwner, now, retryBefore, leaseUntil, maxAttempts))
                .map(itemViewId -> requireLease(itemViewId, leaseOwner))
                .toList();
    }

    @Override
    public void release(String itemViewId, String leaseOwner) {
        jdbcTemplate.update("""
                UPDATE t_maintenance_case_item_view
                   SET withdrawal_recovery_lease_owner = NULL,
                       withdrawal_recovery_lease_until = NULL
                 WHERE item_view_id = ?
                   AND withdrawal_recovery_lease_owner = ?
                """, itemViewId, leaseOwner);
    }

    private boolean acquire(
            String itemViewId,
            String leaseOwner,
            LocalDateTime now,
            LocalDateTime retryBefore,
            LocalDateTime leaseUntil,
            int maxAttempts) {
        return jdbcTemplate.update("""
                UPDATE t_maintenance_case_item_view
                   SET withdrawal_recovery_lease_owner = ?,
                       withdrawal_recovery_lease_until = ?
                 WHERE item_view_id = ?
                   AND withdrawal_status IN (?, ?, ?)
                   AND withdrawal_recovery_configured_at IS NOT NULL
                   AND update_time <= ?
                   AND (withdrawal_status <> ? OR COALESCE(withdrawal_retry_count, 0) < ?)
                   AND (withdrawal_recovery_lease_until IS NULL OR withdrawal_recovery_lease_until < ?)
                """, leaseOwner, leaseUntil, itemViewId, REQUESTED, FAILED, WAITING_FUNDS,
                retryBefore, FAILED, maxAttempts, now) == 1;
    }

    private WithdrawalRecoveryLease requireLease(String itemViewId, String leaseOwner) {
        return jdbcTemplate.queryForObject("""
                SELECT item_view_id,
                       maintenance_id,
                       tenant_id,
                       item_code,
                       withdrawal_operation_id,
                       withdrawal_reason,
                       withdrawal_payment_method,
                       COALESCE(withdrawal_retry_count, 0) AS withdrawal_retry_count
                  FROM t_maintenance_case_item_view
                 WHERE item_view_id = ?
                   AND withdrawal_recovery_lease_owner = ?
                """, (resultSet, rowNumber) -> new WithdrawalRecoveryLease(
                        resultSet.getString("item_view_id"), resultSet.getString("maintenance_id"),
                        resultSet.getString("tenant_id"), resultSet.getString("item_code"),
                        resultSet.getString("withdrawal_operation_id"), resultSet.getString("withdrawal_reason"),
                        resultSet.getString("withdrawal_payment_method"),
                        resultSet.getInt("withdrawal_retry_count")), itemViewId, leaseOwner);
    }
}
