package com.titanium.maintenance.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 保全状态变更 DTO（web 前端入参）
 * <p>
 * 面向后台/端上接收状态变更请求，目标状态 {@code newStatus} 以 String 码值承载，由 Controller 在边界
 * 转换为领域枚举 {@code MaintenanceStatus}。
 * </p>
 */
@Data
public class ChangeMaintenanceStatusDTO {
    @NotBlank(message = "New status is required")
    private String newStatus;

    @Size(max = 500, message = "Change reason must be less than or equal to 500 characters")
    private String changeReason;

    @NotBlank(message = "Changed by is required")
    @Size(max = 50, message = "Changed by must be less than or equal to 50 characters")
    private String changedBy;
}
