package com.titanium.maintenance.web.dto.withdrawal;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 单个保全项目撤销请求；财务金额与方向只允许从权威费用事实推导。 */
public record WithdrawMaintenanceItemDTO(
        @NotBlank @Size(max = 128) String operationId,
        @NotBlank @Size(max = 500) String reason,
        @Size(max = 32) String paymentMethod) {

    /** 拒绝客户端伪造费用、冲正或资金结果。 */
    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object ignoredValue) {
        throw new IllegalArgumentException("项目撤销请求不支持字段: " + fieldName);
    }
}
