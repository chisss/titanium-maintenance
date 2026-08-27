package com.titanium.maintenance.api.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

/**
 * 保全案件响应（对外契约，Feign 出参）
 * <p>
 * api 契约自包含：保全类型 {@code maintenanceType}、生效时间类型 {@code effectiveTimeType}、状态
 * {@code status} 均以 String 码值承载，避免下游消费方被迫传递依赖领域枚举。由 web/provider 的
 * {@code MaintenanceWebMapper} 从应用层结果组装。
 * </p>
 */
@Data
@Builder
public class MaintenanceResponse {
    private String id;
    private String policyId;
    private String customerId;
    private String maintenanceType;
    private BigDecimal totalAmount;
    private BigDecimal refundAmount;
    private String premiumSettlementStatus;
    private String originalCalculationId;
    private String replacementCalculationId;
    private String premiumAdjustmentId;
    private String premiumAdjustmentResultHash;
    private String billingPostingId;
    private String refundInstructionId;
    private String refundOrderId;
    private String refundStatus;
    private Integer commissionAdjustmentCount;
    private String balanceDirection;
    private BigDecimal balanceAmount;
    private String balanceCurrency;
    private String effectiveTimeType;
    private LocalDateTime specificEffectiveDate;
    private String description;
    private String status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private String tenantId;
}
