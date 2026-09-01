package com.titanium.maintenance.web.mapper;

import org.mapstruct.Mapper;

import com.titanium.maintenance.api.response.MaintenanceStatisticsResponse;
import com.titanium.maintenance.application.model.casecreation.MaintenanceStatisticsResult;

/**
 * 保全统计 Web 层对象映射器（MapStruct）
 * <p>
 * 将应用层读用例出参 {@link MaintenanceStatisticsResult} 声明式映射为对外远程契约
 * {@link MaintenanceStatisticsResponse}。字段同名同类型，由 MapStruct 自动映射，无需手工 set。
 * </p>
 */
@Mapper(componentModel = "spring")
public interface MaintenanceStatisticsWebMapper {

    /** 应用层统计结果 → 对外统计响应（字段同名自动映射） */
    MaintenanceStatisticsResponse toResponse(MaintenanceStatisticsResult result);
}
