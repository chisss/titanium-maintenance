package com.titanium.maintenance.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

/**
 * 保全案件应用层响应 DTO
 * <p>
 * 读用例出参：由应用层从领域聚合根组装，避免领域模型泄漏到 HTTP 边界，也令应用层门面不依赖 api 契约
 * （api 的 DTO→Command/Response 翻译在 web 层完成）。枚举字段以枚举名 {@code name()} 的 String 承载，
 * web 层再分别组装为展示 VO 与对外 DTO。
 * </p>
 */
@Data
@Builder
public class MaintenanceResponseDTO {
    private String id;
    private String policyId;
    private String customerId;
    private String maintenanceType;
    private BigDecimal totalAmount;
    private BigDecimal refundAmount;
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
