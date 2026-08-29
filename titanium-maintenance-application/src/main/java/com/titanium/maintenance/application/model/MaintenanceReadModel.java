package com.titanium.maintenance.application.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

/**
 * 保全案件应用层读模型
 * <p>
 * application 读用例出参：由应用服务从 CQRS 读模型 {@code MaintenanceView} 组装、屏蔽 domain 聚合根，
 * 供表现层（web mapper）映射为对外响应/展示对象。<b>非对外远程契约</b>（对外契约是 api 层
 * {@code MaintenanceResponse}，位于 api/response），故不带 DTO 后缀、不置于 api 层，避免与"DTO=对外契约"语义混淆。
 * </p>
 * <p>
 * 枚举字段以枚举名 {@code name()}/码值的 String 承载，web 层再分别组装为展示 VO 与对外 DTO。
 * </p>
 */
@Data
@Builder
public class MaintenanceReadModel {
    /** 系统生成的保全号 */
    private String        maintenanceNo;
    /** 保全案件ID */
    private String        id;
    /** 保单ID */
    private String        policyId;
    /** 客户ID */
    private String        customerId;
    /** 保全类型码值 */
    private String        maintenanceType;
    /** 保全总金额 */
    private BigDecimal    totalAmount;
    /** 退费金额 */
    private BigDecimal    refundAmount;
    /** Product/Billing 费用事实及资金结算检查点状态 */
    private String        premiumSettlementStatus;
    private String        originalCalculationId;
    private String        replacementCalculationId;
    private String        premiumAdjustmentId;
    private String        premiumAdjustmentResultHash;
    private String        billingPostingId;
    private String        refundInstructionId;
    private String        refundOrderId;
    private String        refundStatus;
    private Integer       commissionAdjustmentCount;
    private String        balanceDirection;
    private BigDecimal    balanceAmount;
    private String        balanceCurrency;
    private String        surrenderPolicyCode;
    private String        surrenderPolicyVersion;
    private String        surrenderPolicyContentHash;
    private Integer       surrenderPolicyYear;
    private Integer       coolingOffDays;
    private String        surrenderRefundType;
    private Boolean       withinCoolingOff;
    private BigDecimal    cashValueRate;
    private BigDecimal    retainedCustomerAmount;
    private BigDecimal    internalCostRetentionRate;
    /** 生效时间类型码值 */
    private String        effectiveTimeType;
    /** 指定生效日期 */
    private LocalDateTime specificEffectiveDate;
    /** 保全描述 */
    private String        description;
    /** 保全状态码值 */
    private String        status;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 创建人 */
    private String        createdBy;
    /** 更新时间 */
    private LocalDateTime updatedAt;
    /** 更新人 */
    private String        updatedBy;
    /** 租户ID */
    private String        tenantId;
}
