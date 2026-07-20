package com.titanium.maintenance.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 保全状态变更请求（对外契约，Feign 入参）
 * <p>
 * api 契约自包含：目标状态 {@code newStatus} 以 String 码值承载，避免耦合领域枚举
 * {@code MaintenanceStatus}；由 web/provider 的 {@code MaintenanceApiProvider} 在边界转换为强类型。
 * </p>
 */
@Data
public class ChangeMaintenanceStatusRequest {
    @NotBlank(message = "New status is required")
    private String newStatus;

    @Size(max = 500, message = "Change reason must be less than or equal to 500 characters")
    private String changeReason;

    @NotBlank(message = "Changed by is required")
    @Size(max = 50, message = "Changed by must be less than or equal to 50 characters")
    private String changedBy;
}
