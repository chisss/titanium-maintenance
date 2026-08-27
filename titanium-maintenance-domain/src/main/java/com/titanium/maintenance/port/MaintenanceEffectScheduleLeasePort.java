package com.titanium.maintenance.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 未来生效调度租约端口；实现必须以数据库条件更新保证多节点互斥。 */
public interface MaintenanceEffectScheduleLeasePort {

    List<ScheduleLease> acquireDue(
            String leaseOwner, LocalDateTime now, LocalDateTime leaseUntil, int batchSize);

    Optional<ScheduleLease> acquireNow(
            String tenantId, String maintenanceId, String leaseOwner,
            LocalDateTime now, LocalDateTime leaseUntil);

    void release(String maintenanceId, String leaseOwner);

    record ScheduleLease(
            String maintenanceId,
            String tenantId,
            String scheduleId,
            String effectTaskId,
            LocalDateTime nextExecutionAt,
            String tenantZoneId,
            int attemptCount) {
    }
}
