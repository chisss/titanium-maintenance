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

import com.titanium.maintenance.port.MaintenanceEffectScheduleLeasePort.ScheduleLease;

class JdbcMaintenanceEffectScheduleLeaseAdapterTest {

    private JdbcMaintenanceEffectScheduleLeaseAdapter firstNode;
    private JdbcMaintenanceEffectScheduleLeaseAdapter secondNode;

    @BeforeEach
    void setUp() {
        String database = "maintenance_lease_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + database + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE t_maintenance_view (
                    maintenance_id VARCHAR(64) PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL,
                    effect_schedule_id VARCHAR(128),
                    effect_schedule_status VARCHAR(20),
                    effect_schedule_next_execution_at TIMESTAMP,
                    effect_schedule_tenant_zone_id VARCHAR(64),
                    effect_schedule_attempt_count INT,
                    effect_schedule_lease_owner VARCHAR(128),
                    effect_schedule_lease_until TIMESTAMP)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_maintenance_workflow_task_view (
                    task_id VARCHAR(191) PRIMARY KEY,
                    maintenance_id VARCHAR(64) NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL,
                    step_type VARCHAR(32),
                    task_status VARCHAR(32))
                """);
        jdbcTemplate.update("""
                INSERT INTO t_maintenance_view (
                    maintenance_id, tenant_id, effect_schedule_id, effect_schedule_status,
                    effect_schedule_next_execution_at, effect_schedule_tenant_zone_id,
                    effect_schedule_attempt_count)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, "case-1", "tenant-1", "case-1:effect", "ACTIVE",
                LocalDateTime.of(2026, 8, 25, 1, 0), "Asia/Shanghai", 0);
        jdbcTemplate.update("""
                INSERT INTO t_maintenance_workflow_task_view (
                    task_id, maintenance_id, tenant_id, step_type, task_status)
                VALUES (?, ?, ?, ?, ?)
                """, "case-1:effect-task", "case-1", "tenant-1", "EFFECT", "READY");
        firstNode = new JdbcMaintenanceEffectScheduleLeaseAdapter(jdbcTemplate);
        secondNode = new JdbcMaintenanceEffectScheduleLeaseAdapter(jdbcTemplate);
    }

    @Test
    void shouldAllowOnlyOneNodeToAcquireAndAllowNextNodeAfterRelease() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 25, 1, 1);
        List<ScheduleLease> first = firstNode.acquireDue("node-1", now, now.plusMinutes(2), 10);
        List<ScheduleLease> concurrent = secondNode.acquireDue("node-2", now, now.plusMinutes(2), 10);

        assertEquals(1, first.size());
        assertEquals("case-1:effect-task", first.getFirst().effectTaskId());
        assertTrue(concurrent.isEmpty());

        firstNode.release("case-1", "node-1");
        List<ScheduleLease> afterRelease = secondNode.acquireDue("node-2", now, now.plusMinutes(2), 10);
        assertEquals(1, afterRelease.size());
    }
}
