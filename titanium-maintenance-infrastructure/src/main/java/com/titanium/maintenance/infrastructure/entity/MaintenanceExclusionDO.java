package com.titanium.maintenance.infrastructure.entity;

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

/**
 * 保全类型互斥配置持久化对象（DO）
 * <p>
 * 保全互斥规则为参考/配置数据（纯 JPA CRUD，非事件溯源聚合写状态），故为 {@code *DO} 而非聚合映射 Entity。
 * 对应配置表 {@code maintenance_exclusion}，由 {@code MaintenanceExclusionRepository} 端口的基础设施实现读取。
 * </p>
 */
@Entity
@Table(name = "maintenance_exclusion", indexes = {
        @Index(name = "idx_maintenance_exclusion_tenant_id", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
public class MaintenanceExclusionDO {
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
