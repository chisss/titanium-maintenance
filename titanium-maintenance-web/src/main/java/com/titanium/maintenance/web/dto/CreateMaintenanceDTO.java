package com.titanium.maintenance.web.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建保全 DTO（web 前端入参）
 * <p>
 * 面向后台/端上接收创建保全案件的请求参数，保全类型 {@code maintenanceType}、生效时间类型
 * {@code effectiveTimeType} 以 String 码值承载，由 Controller 在边界转换为领域枚举。
 * </p>
 */
@Data
public class CreateMaintenanceDTO {
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
