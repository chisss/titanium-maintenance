package com.titanium.maintenance.query.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.common.jpa.BaseView;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 保全案件读模型实体（CQRS Projection）
 * <p>
 * 对应读模型表 {@code t_maintenance_view}，与写侧事件存储物理隔离。 由
 * {@link com.titanium.maintenance.query.handler.projection.MaintenanceProjectionEventHandler} 订阅领域事件投影而来。
 * </p>
 * <p>
 * 继承 {@link BaseView}，复用租户ID、创建/更新时间（投影时间）、乐观锁版本等读模型公共字段。
 * </p>
 */
@Entity
@Table(name = "t_maintenance_view")
@Getter
@Setter
public class MaintenanceView extends BaseView {

    /** 保全案件ID（聚合根ID，读模型主键） */
    @Id
    @Column(name = "maintenance_id", nullable = false, length = 36)
    private String            maintenanceId;

    /** 保单ID */
    @Column(name = "policy_id", length = 36)
    private String            policyId;

    /** 客户ID */
    @Column(name = "customer_id", length = 36)
    private String            customerId;

    /** 保全类型 */
    @Enumerated(EnumType.STRING)
    @Column(name = "maintenance_type", length = 50)
    private MaintenanceType   maintenanceType;

    /** 保全状态 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private MaintenanceStatus status;

    /** 生效时间类型 */
    @Enumerated(EnumType.STRING)
    @Column(name = "effective_time_type", length = 20)
    private EffectiveTimeType effectiveTimeType;

    /** 指定生效日期 */
    @Column(name = "specific_effective_date")
    private LocalDateTime     specificEffectiveDate;

    /** 保全总金额 */
    @Column(name = "total_amount", precision = 15, scale = 2)
    private BigDecimal        totalAmount;

    /** 退费金额 */
    @Column(name = "refund_amount", precision = 15, scale = 2)
    private BigDecimal        refundAmount;

    /** 保全描述 */
    @Column(name = "description", length = 500)
    private String            description;
}
