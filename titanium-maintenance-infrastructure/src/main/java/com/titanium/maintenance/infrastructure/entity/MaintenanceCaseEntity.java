package com.titanium.maintenance.infrastructure.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "maintenance_case", indexes = {
        @Index(name = "idx_maintenance_case_policy_id", columnList = "policy_id"),
        @Index(name = "idx_maintenance_case_status", columnList = "status"),
        @Index(name = "idx_maintenance_case_tenant_id", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
public class MaintenanceCaseEntity {
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

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private MaintenanceStatus status;

    @Column(name = "effective_time_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private EffectiveTimeType effectiveTimeType;

    @Column(name = "specific_effective_date")
    private LocalDateTime specificEffectiveDate;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "refund_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "description", length = 500)
    private String description;

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
