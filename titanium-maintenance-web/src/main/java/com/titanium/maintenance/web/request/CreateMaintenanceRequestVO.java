package com.titanium.maintenance.web.request;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建保全请求 VO（后台/端上入参）
 * <p>
 * 面向管理后台/端上 HTTP 入口；保全类型 {@code maintenanceType}、生效时间类型 {@code effectiveTimeType}
 * 以 String 码值承载，由 {@code MaintenanceWebMapper} 翻译为应用层入参。
 * </p>
 */
@Data
public class CreateMaintenanceRequestVO {
    @NotBlank(message = "保单ID不能为空")
    @Size(max = 36, message = "保单ID长度不能超过36个字符")
    private String policyId;

    @NotBlank(message = "客户ID不能为空")
    @Size(max = 36, message = "客户ID长度不能超过36个字符")
    private String customerId;

    @NotBlank(message = "保全类型不能为空")
    private String maintenanceType;

    @NotBlank(message = "生效时间类型不能为空")
    private String effectiveTimeType;

    private LocalDateTime specificEffectiveDate;

    @Size(max = 500, message = "描述长度不能超过500个字符")
    private String description;

    @NotBlank(message = "创建人不能为空")
    @Size(max = 50, message = "创建人长度不能超过50个字符")
    private String createdBy;
}
