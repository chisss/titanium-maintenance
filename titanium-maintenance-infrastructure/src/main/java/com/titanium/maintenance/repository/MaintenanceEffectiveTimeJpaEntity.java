package com.titanium.maintenance.repository;

import com.titanium.maintenance.enums.EffectiveTimeStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "maintenance_effective_time", indexes = {
        @Index(name = "idx_maintenance_effective_time_case_id", columnList = "maintenance_case_id"),
        @Index(name = "idx_maintenance_effective_time_status", columnList = "status"),
        @Index(name = "idx_maintenance_effective_time_tenant_id", columnList = "tenant_id")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MaintenanceEffectiveTimeJpaEntity {
    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "maintenance_case_id", nullable = false, length = 36)
    private String maintenanceCaseId;

    @Column(name = "effective_time", nullable = false)
    private LocalDateTime effectiveTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EffectiveTimeStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;
}