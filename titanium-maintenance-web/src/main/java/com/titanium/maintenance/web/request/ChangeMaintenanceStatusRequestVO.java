package com.titanium.maintenance.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 保全状态变更请求 VO（后台/端上入参）
 * <p>
 * 目标状态 {@code newStatus} 以 String 码值承载，由 {@code MaintenanceWebMapper} 翻译为领域枚举后
 * 交应用层门面。
 * </p>
 */
@Data
public class ChangeMaintenanceStatusRequestVO {
    @NotBlank(message = "目标状态不能为空")
    private String newStatus;

    @Size(max = 500, message = "变更原因长度不能超过500个字符")
    private String changeReason;

    @NotBlank(message = "变更人不能为空")
    @Size(max = 50, message = "变更人长度不能超过50个字符")
    private String changedBy;
}
