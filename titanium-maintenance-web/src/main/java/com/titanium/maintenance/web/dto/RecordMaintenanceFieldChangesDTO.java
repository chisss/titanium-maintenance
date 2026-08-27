package com.titanium.maintenance.web.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import com.titanium.maintenance.common.enums.change.PolicyFieldDataType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 保存一个保全项完整字段草稿的请求。 */
public record RecordMaintenanceFieldChangesDTO(
        @NotEmpty @Size(max = 100) List<@Valid FieldProposalDTO> proposals) {

    /** 独立资源协议拒绝未声明字段，避免客户端继续提交保单等服务端权威上下文。 */
    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object ignoredValue) {
        throw new IllegalArgumentException("字段草稿请求不支持字段: " + fieldName);
    }

    /** 单字段结构化提案；空值使用 canonicalValue=null 表达。 */
    public record FieldProposalDTO(
            @Size(max = 128) String objectId,
            @NotBlank @Size(max = 128) String fieldCode,
            @NotNull PolicyFieldDataType dataType,
            @Size(max = 32768) String canonicalValue) {
    }
}
