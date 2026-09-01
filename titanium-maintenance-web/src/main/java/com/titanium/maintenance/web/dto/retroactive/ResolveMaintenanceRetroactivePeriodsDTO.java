package com.titanium.maintenance.web.dto.retroactive;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 关闭会计期间处理请求；批次、差额和处理凭证均由服务端派生。 */
public record ResolveMaintenanceRetroactivePeriodsDTO(
        @NotBlank @Size(max = 128) String operationId,
        @NotBlank @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])") String targetAccountingPeriod,
        @NotBlank @Size(max = 500) String reason) {
}
