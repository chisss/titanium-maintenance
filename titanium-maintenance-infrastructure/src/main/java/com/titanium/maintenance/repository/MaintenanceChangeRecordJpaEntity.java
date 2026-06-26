package com.titanium.maintenance.repository;

import com.titanium.maintenance.enums.MaintenanceChangeType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "maintenance_change_record", indexes = {
        @Index(name = "idx_maintenance_change_record_case_id", columnList = "maintenance_case_id"),
        @Index(name = "idx_maintenance_change_record_tenant_id", columnList = "tenant_id")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MaintenanceChangeRecordJpaEntity {
    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "maintenance_case_id", nullable = false, length = 36)
    private String maintenanceCaseId;

    @Column(name = "change_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private MaintenanceChangeType changeType;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;
}