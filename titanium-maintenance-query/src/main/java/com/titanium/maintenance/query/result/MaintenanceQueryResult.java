package com.titanium.maintenance.query.result;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

import lombok.Data;

/**
 * 保全案件查询结果（CQRS 读侧稳定返回契约）
 */
@Data
public class MaintenanceQueryResult {
    private String            maintenanceId;
    private String            policyId;
    private String            customerId;
    private MaintenanceType   maintenanceType;
    private MaintenanceStatus status;
    private EffectiveTimeType effectiveTimeType;
    private LocalDateTime     specificEffectiveDate;
    private BigDecimal        totalAmount;
    private BigDecimal        refundAmount;
    private String            description;
    private LocalDateTime     createdAt;
    private LocalDateTime     updatedAt;
    private String            tenantId;
}
