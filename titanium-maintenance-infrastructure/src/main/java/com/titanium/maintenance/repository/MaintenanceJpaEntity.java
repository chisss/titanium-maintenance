package com.titanium.maintenance.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.maintenance.enums.MaintenanceStatus;
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
@Table(name = "maintenance", indexes = {
        @Index(name = "idx_maintenance_policy_id", columnList = "policy_id"),
        @Index(name = "idx_maintenance_customer_id", columnList = "customer_id"),
        @Index(name = "idx_maintenance_status", columnList = "status"),
        @Index(name = "idx_maintenance_tenant_id", columnList = "tenant_id")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MaintenanceJpaEntity {
    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "policy_id", nullable = false, length = 36)
    private String policyId;

    @Column(name = "customer_id", nullable = false, length = 36)
    private String customerId;

    @Column(name = "maintenance_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private MaintenanceType maintenanceType;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private MaintenanceStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", nullable = false, length = 50)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", nullable = false, length = 50)
    private String updatedBy;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;
}
