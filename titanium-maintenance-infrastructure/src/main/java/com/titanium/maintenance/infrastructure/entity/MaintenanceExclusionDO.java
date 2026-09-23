package com.titanium.maintenance.infrastructure.entity;

import com.titanium.metadata.enums.maintenance.MaintenanceType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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
    /**
     * 乐观锁版本（JPA 自动维护）。
     * <p>
     * m20-09（审查报告 PER-01）补齐：原先写侧 DO 无 {@code @Version}，并发更新走无条件 UPDATE，
     * 后写者静默覆盖前写者、数据丢失。加此字段后 JPA 的 UPDATE 会带 {@code WHERE version = ?}，
     * 冲突时抛 {@code ObjectOptimisticLockingFailureException}（由异常处理器映射为 409）。
     * </p>
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

}
