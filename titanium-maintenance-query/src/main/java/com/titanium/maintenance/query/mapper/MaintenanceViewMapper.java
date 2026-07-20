package com.titanium.maintenance.query.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import com.titanium.maintenance.event.MaintenanceCreatedEvent;
import com.titanium.maintenance.query.view.MaintenanceView;

/**
 * 保全读模型投影映射器（MapStruct，事件 → 读模型字段拷贝）
 * <p>
 * 承担"新建型"投影（保全创建事件）的 event record → View 字段映射，取代投影处理器中逐字段 set。采用
 * {@link MappingTarget} 就地更新既有/新建 View 实例，保留投影的 upsert 语义；
 * {@link NullValuePropertyMappingStrategy#IGNORE} 确保事件缺省字段不覆盖 View 既有值。
 * </p>
 * <p>
 * <b>职责边界</b>：仅做纯字段/值对象结构翻译（值对象 {@code MaintenanceId/PolicyId/CustomerId} 拆为其
 * {@code id} 字符串；创建时 {@code status} 恒为 {@code PENDING}；{@code updatedBy} 首次取 {@code createdBy}）。
 * 审计时间戳（createTime 仅首次、updateTime 每次）含"仅首次"语义与运行时取值，仍由投影处理器控制，
 * 不下沉映射器，故此处对应目标字段 {@code ignore}。
 * </p>
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MaintenanceViewMapper {

    /** 保全创建事件 → 保全读模型（就地 upsert；值对象拆解、状态置 PENDING、updatedBy 取 createdBy） */
    @Mapping(target = "maintenanceId", source = "maintenanceId.id")
    @Mapping(target = "policyId", source = "policyId.id")
    @Mapping(target = "customerId", source = "customerId.id")
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "updatedBy", source = "createdBy")
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    void applyCreated(@MappingTarget MaintenanceView view, MaintenanceCreatedEvent event);
}
