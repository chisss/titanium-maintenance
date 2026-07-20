package com.titanium.maintenance.web.mapper;

import org.mapstruct.Mapper;

import com.titanium.maintenance.api.response.MaintenanceResponse;
import com.titanium.maintenance.application.model.MaintenanceReadModel;
import com.titanium.maintenance.web.response.MaintenanceVO;

/**
 * 保全 Web 层对象映射器（MapStruct）
 * <p>
 * 边界协议转换枢纽：应用层读模型 {@link MaintenanceReadModel} 分别组装为面向后台/端上的展示
 * {@link MaintenanceVO}（Controller 用）与面向其它微服务的对外 {@link MaintenanceResponse}（Provider 用）。
 * </p>
 * <p>
 * 写用例：保全应用层门面 {@code MaintenanceApplicationService} 承担真实跨域编排（校验保单/客户、在途/互斥
 * 检查、生成 MaintenanceId），入参为标量参数而非领域命令，故 Controller/Provider 直接透传请求字段，
 * 本映射器不承担 Request→Command 翻译（参考理赔域先例）。枚举字段全程以 String 码值承载，三者字段同名，
 * MapStruct 按名直映。
 * </p>
 */
@Mapper(componentModel = "spring")
public interface MaintenanceWebMapper {

    /**
     * 应用层读模型 → 展示 VO（Controller 用）
     *
     * @param readModel 应用层读模型
     * @return 保全展示 VO
     */
    MaintenanceVO toVO(MaintenanceReadModel readModel);

    /**
     * 应用层读模型 → 对外响应 DTO（Provider 用）
     *
     * @param readModel 应用层读模型
     * @return 对外保全响应 DTO
     */
    MaintenanceResponse toApiResponse(MaintenanceReadModel readModel);
}
