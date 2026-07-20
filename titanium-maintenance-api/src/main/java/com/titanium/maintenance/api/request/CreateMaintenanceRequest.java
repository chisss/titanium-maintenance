package com.titanium.maintenance.api.request;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建保全请求（对外契约，Feign 入参）
 * <p>
 * api 契约自包含：保全类型 {@code maintenanceType}、生效时间类型 {@code effectiveTimeType} 以 String 码值
 * 承载，避免耦合领域枚举；由 web/provider 的 {@code MaintenanceApiProvider} 在边界转换为强类型。
 * </p>
 */
@Data
public class CreateMaintenanceRequest {
    @NotBlank(message = "Policy ID is required")
    @Size(max = 36, message = "Policy ID must be less than or equal to 36 characters")
    private String policyId;

    @NotBlank(message = "Customer ID is required")
    @Size(max = 36, message = "Customer ID must be less than or equal to 36 characters")
    private String customerId;

    @NotBlank(message = "Maintenance type is required")
    private String maintenanceType;

    @NotBlank(message = "Effective time type is required")
    private String effectiveTimeType;

    private LocalDateTime specificEffectiveDate;

    @Size(max = 500, message = "Description must be less than or equal to 500 characters")
    private String description;

    @NotBlank(message = "Created by is required")
    @Size(max = 50, message = "Created by must be less than or equal to 50 characters")
    private String createdBy;
}
