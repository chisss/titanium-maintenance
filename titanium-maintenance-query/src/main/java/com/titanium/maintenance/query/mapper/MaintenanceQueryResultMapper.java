package com.titanium.maintenance.query.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.titanium.maintenance.query.result.MaintenanceQueryResult;
import com.titanium.maintenance.query.view.MaintenanceView;

/**
 * 保全读模型 → 查询结果映射器（MapStruct 声明式）
 * <p>
 * 读模型实体 {@link MaintenanceView} 与稳定返回契约 {@link MaintenanceQueryResult} 的纯结构映射，
 * 替代查询服务内逐字段 set；禁止直接返回读模型实体（泄漏持久化细节）。
 * </p>
 */
@Mapper(componentModel = "spring")
public interface MaintenanceQueryResultMapper {

    /** 读模型 → 查询结果（审计字段 createTime/updateTime 映射为 createdAt/updatedAt，其余同名自动映射） */
    @Mapping(target = "createdAt", source = "createTime")
    @Mapping(target = "updatedAt", source = "updateTime")
    MaintenanceQueryResult toResult(MaintenanceView view);
}
