package com.titanium.maintenance.web.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 保全案件展示 VO（后台/端上出参）
 * <p>
 * 面向管理后台/端上，从应用层响应 DTO 组装；枚举字段以 String 码值承载。
 * </p>
 */
@Data
public class MaintenanceVO {
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
    private String surrenderPolicyCode;
    private String surrenderPolicyVersion;
    private String surrenderPolicyContentHash;
    private Integer surrenderPolicyYear;
    private Integer coolingOffDays;
    private String surrenderRefundType;
    private Boolean withinCoolingOff;
    private BigDecimal cashValueRate;
    private BigDecimal retainedCustomerAmount;
    private BigDecimal internalCostRetentionRate;
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
