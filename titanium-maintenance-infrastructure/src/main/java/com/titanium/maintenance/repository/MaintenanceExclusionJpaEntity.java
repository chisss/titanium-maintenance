package com.titanium.maintenance.repository;

import com.titanium.maintenance.enums.MaintenanceType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "maintenance_exclusion", indexes = {
        @Index(name = "idx_maintenance_exclusion_tenant_id", columnList = "tenant_id")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MaintenanceExclusionJpaEntity {
    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "maintenance_type_1", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private MaintenanceType maintenanceType1;

    @Column(name = "maintenance_type_2", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private MaintenanceType maintenanceType2;

    @Column(name = "description", length = 200)
    private String description;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;
}
