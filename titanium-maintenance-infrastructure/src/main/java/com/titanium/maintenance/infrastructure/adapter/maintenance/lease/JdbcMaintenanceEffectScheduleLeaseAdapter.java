package com.titanium.maintenance.infrastructure.adapter.maintenance.lease;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.maintenance.port.maintenance.MaintenanceEffectScheduleLeasePort;

import lombok.RequiredArgsConstructor;

/** 通过条件更新实现多节点互斥的未来生效调度租约。 */
@Component
@RequiredArgsConstructor
public class JdbcMaintenanceEffectScheduleLeaseAdapter implements MaintenanceEffectScheduleLeasePort {

    private static final String ACTIVE = "ACTIVE";

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public List<ScheduleLease> acquireDue(
            String leaseOwner, LocalDateTime now, LocalDateTime leaseUntil, int batchSize) {
        List<String> candidates = jdbcTemplate.queryForList("""
                SELECT maintenance_id
                  FROM t_maintenance_view
                 WHERE effect_schedule_status = ?
                   AND effect_schedule_next_execution_at <= ?
                   AND (effect_schedule_lease_until IS NULL OR effect_schedule_lease_until < ?)
                 ORDER BY effect_schedule_next_execution_at, maintenance_id
                 LIMIT ?
                """, String.class, ACTIVE, now, now, batchSize);
        return candidates.stream()
                .filter(maintenanceId -> acquire(maintenanceId, null, leaseOwner, now, leaseUntil, true))
                .map(maintenanceId -> requireLease(maintenanceId, leaseOwner))
                .toList();
    }

    @Override
    @Transactional
    public Optional<ScheduleLease> acquireNow(
            String tenantId,
            String maintenanceId,
            String leaseOwner,
            LocalDateTime now,
            LocalDateTime leaseUntil) {
        if (!acquire(maintenanceId, tenantId, leaseOwner, now, leaseUntil, false)) {
            return Optional.empty();
        }
        return Optional.of(requireLease(maintenanceId, leaseOwner));
    }

    @Override
    public void release(String maintenanceId, String leaseOwner) {
        jdbcTemplate.update("""
                UPDATE t_maintenance_view
                   SET effect_schedule_lease_owner = NULL,
                       effect_schedule_lease_until = NULL
                 WHERE maintenance_id = ?
                   AND effect_schedule_lease_owner = ?
                """, maintenanceId, leaseOwner);
    }

    private boolean acquire(
            String maintenanceId,
            String tenantId,
            String leaseOwner,
            LocalDateTime now,
            LocalDateTime leaseUntil,
            boolean requireDue) {
        String tenantPredicate = tenantId == null ? "" : " AND tenant_id = ?";
        String duePredicate = requireDue ? " AND effect_schedule_next_execution_at <= ?" : "";
        String sql = """
                UPDATE t_maintenance_view
                   SET effect_schedule_lease_owner = ?,
                       effect_schedule_lease_until = ?
                 WHERE maintenance_id = ?
                   AND effect_schedule_status = ?
                   AND (effect_schedule_lease_until IS NULL OR effect_schedule_lease_until < ?)
                """ + tenantPredicate + duePredicate;
        if (tenantId == null) {
            return jdbcTemplate.update(sql, leaseOwner, leaseUntil, maintenanceId, ACTIVE, now, now) == 1;
        }
        return jdbcTemplate.update(sql, leaseOwner, leaseUntil, maintenanceId, ACTIVE, now, tenantId) == 1;
    }

    private ScheduleLease requireLease(String maintenanceId, String leaseOwner) {
        return jdbcTemplate.queryForObject("""
                SELECT m.maintenance_id,
                       m.tenant_id,
                       m.effect_schedule_id,
                       (SELECT MIN(t.task_id)
                          FROM t_maintenance_workflow_task_view t
                         WHERE t.maintenance_id = m.maintenance_id
                           AND t.tenant_id = m.tenant_id
                           AND t.step_type = 'EFFECT'
                           AND t.task_status <> 'SKIPPED') AS effect_task_id,
                       m.effect_schedule_next_execution_at,
                       m.effect_schedule_tenant_zone_id,
                       m.effect_schedule_attempt_count
                  FROM t_maintenance_view m
                 WHERE m.maintenance_id = ?
                   AND m.effect_schedule_lease_owner = ?
                """, (resultSet, rowNumber) -> new ScheduleLease(
                        resultSet.getString("maintenance_id"), resultSet.getString("tenant_id"),
                        resultSet.getString("effect_schedule_id"), resultSet.getString("effect_task_id"),
                        resultSet.getTimestamp("effect_schedule_next_execution_at").toLocalDateTime(),
                        resultSet.getString("effect_schedule_tenant_zone_id"),
                        resultSet.getInt("effect_schedule_attempt_count")),
                maintenanceId, leaseOwner);
    }
}
