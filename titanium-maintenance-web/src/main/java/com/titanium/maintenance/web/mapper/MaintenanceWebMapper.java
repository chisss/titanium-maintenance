package com.titanium.maintenance.web.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import com.titanium.maintenance.api.request.SettleMaintenancePremiumRequest;
import com.titanium.maintenance.api.request.SettleMaintenanceReversalRequest;
import com.titanium.maintenance.api.request.SettleMaintenanceSurrenderRequest;
import com.titanium.maintenance.api.response.MaintenancePremiumSettlementResponse;
import com.titanium.maintenance.api.response.MaintenanceResponse;
import com.titanium.maintenance.api.response.MaintenanceSurrenderSettlementResponse;
import com.titanium.maintenance.application.model.casecreation.MaintenanceReadModel;
import com.titanium.maintenance.application.model.premium.MaintenancePremiumSettlementInput;
import com.titanium.maintenance.application.model.premium.MaintenancePremiumSettlementResult;
import com.titanium.maintenance.application.model.settlement.MaintenanceReversalSettlementInput;
import com.titanium.maintenance.application.model.settlement.MaintenanceSurrenderSettlementInput;
import com.titanium.maintenance.application.model.settlement.MaintenanceSurrenderSettlementResult;
import com.titanium.maintenance.web.response.casecreation.MaintenanceVO;

/**
 * 保全 Web 层对象映射器（MapStruct 声明式映射）
 * <p>
 * 边界协议转换枢纽：应用层读模型 {@link MaintenanceReadModel} 分别组装为面向后台/端上的展示
 * {@link MaintenanceVO}（Controller 用）与面向其它微服务的对外 {@link MaintenanceResponse}（Provider 用）。
 * </p>
 * <p>
 * 写用例：保全应用层门面 {@code MaintenanceApplicationService} 承担真实跨域编排（校验保单/客户、在途/互斥
 * 检查、生成 MaintenanceId），入参为标量参数而非领域命令，故 Controller/Provider 直接透传请求字段，
 * 本映射器不承担 Request→Command 翻译。枚举字段全程以 String 码值承载，三者字段同名，按名直映。
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

    /**
     * 结算请求 → 应用输入（声明式映射）
     *
     * <p>核保调整为嵌套记录集合，元素经 {@link #toAdjustmentInput} 声明式映射；
     * policyYear 未传时默认首年（1）。</p>
     *
     * @param request 保费结算请求
     * @return 保费结算应用输入
     */
    @Mapping(target = "underwritingAdjustments", source = "underwritingAdjustments",
            qualifiedByName = "toAdjustmentInputs")
    @Mapping(target = "policyYear", source = "policyYear", defaultValue = "1")
    MaintenancePremiumSettlementInput toSettlementInput(SettleMaintenancePremiumRequest request);

    /**
     * 核保调整集合 → 输入集合（空集合安全）
     *
     * @param adjustments 请求核保调整集合（可为 null）
     * @return 应用输入核保调整集合（null 归一为空集合）
     */
    @Named("toAdjustmentInputs")
    default List<MaintenancePremiumSettlementInput.UnderwritingAdjustmentInput> toAdjustmentInputs(
            List<SettleMaintenancePremiumRequest.UnderwritingAdjustment> adjustments) {
        if (adjustments == null) {
            return List.of();
        }
        return adjustments.stream().map(this::toAdjustmentInput).toList();
    }

    /**
     * 核保调整元素映射（声明式）
     *
     * @param item 请求核保调整元素
     * @return 应用输入核保调整元素
     */
    MaintenancePremiumSettlementInput.UnderwritingAdjustmentInput toAdjustmentInput(
            SettleMaintenancePremiumRequest.UnderwritingAdjustment item);

    /**
     * 保费结算结果 → 对外响应 DTO
     *
     * @param result 保费结算结果
     * @return 对外保费结算响应
     */
    MaintenancePremiumSettlementResponse toSettlementResponse(MaintenancePremiumSettlementResult result);

    /**
     * 退保价值结算请求 → 应用输入
     *
     * @param request 退保价值结算请求
     * @return 退保价值结算应用输入
     */
    MaintenanceSurrenderSettlementInput toSurrenderSettlementInput(SettleMaintenanceSurrenderRequest request);

    /**
     * 冲正结算请求 → 应用输入
     *
     * @param request 冲正结算请求
     * @return 冲正应用输入
     */
    MaintenanceReversalSettlementInput toReversalSettlementInput(SettleMaintenanceReversalRequest request);

    /**
     * 退保价值结算结果 → 对外响应 DTO（内嵌 settlement 复用
     * {@link #toSettlementResponse(MaintenancePremiumSettlementResult)}）
     *
     * @param result 退保价值结算结果
     * @return 对外退保价值结算响应
     */
    MaintenanceSurrenderSettlementResponse toSurrenderSettlementResponse(
            MaintenanceSurrenderSettlementResult result);
}
