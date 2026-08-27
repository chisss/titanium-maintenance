package com.titanium.maintenance.web.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import com.titanium.maintenance.api.request.SettleMaintenancePremiumRequest;
import com.titanium.maintenance.api.request.SettleMaintenanceReversalRequest;
import com.titanium.maintenance.api.request.SettleMaintenanceSurrenderRequest;
import com.titanium.maintenance.api.response.MaintenancePremiumSettlementResponse;
import com.titanium.maintenance.api.response.MaintenanceResponse;
import com.titanium.maintenance.api.response.MaintenanceSurrenderSettlementResponse;
import com.titanium.maintenance.application.model.MaintenancePremiumSettlementInput;
import com.titanium.maintenance.application.model.MaintenancePremiumSettlementResult;
import com.titanium.maintenance.application.model.MaintenanceReadModel;
import com.titanium.maintenance.application.model.MaintenanceReversalSettlementInput;
import com.titanium.maintenance.application.model.MaintenanceSurrenderSettlementInput;
import com.titanium.maintenance.application.model.MaintenanceSurrenderSettlementResult;
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

    default MaintenancePremiumSettlementInput toSettlementInput(SettleMaintenancePremiumRequest request) {
        return new MaintenancePremiumSettlementInput(
                request.originalCalculationId(), request.productId(), request.productVersion(),
                request.businessTime(), request.currency(), request.sumInsured(), request.age(), request.gender(),
                request.paymentTermYears(), request.coverageTermYears(), request.paymentPeriods(),
                request.requestSnapshot(), request.underwritingAdjustments() == null
                        ? List.of()
                        : request.underwritingAdjustments().stream()
                                .map(item -> new MaintenancePremiumSettlementInput.UnderwritingAdjustmentInput(
                                        item.adjustmentCode(), item.type(), item.value(), item.reason(),
                                        item.ruleVersion()))
                                .toList(),
                request.channelId(), request.policyYear() == null ? 1 : request.policyYear(),
                request.reason(), request.updatedBy());
    }

    default MaintenancePremiumSettlementResponse toSettlementResponse(MaintenancePremiumSettlementResult result) {
        return new MaintenancePremiumSettlementResponse(
                result.maintenanceId(), result.premiumSettlementStatus(), result.originalCalculationId(),
                result.replacementCalculationId(), result.adjustmentId(), result.adjustmentResultHash(),
                result.billingPostingId(), result.billingPostingStatus(), result.direction(), result.amount(),
                result.currency(), result.refundInstructionId(), result.refundOrderId(), result.refundStatus(),
                result.commissionAdjustmentCount());
    }

    default MaintenanceSurrenderSettlementInput toSurrenderSettlementInput(
            SettleMaintenanceSurrenderRequest request) {
        return new MaintenanceSurrenderSettlementInput(
                request.originalCalculationId(), request.surrenderDate(), request.policyYear(),
                request.businessTime(), request.reason(), request.updatedBy());
    }

    default MaintenanceReversalSettlementInput toReversalSettlementInput(
            SettleMaintenanceReversalRequest request) {
        return new MaintenanceReversalSettlementInput(request.sourceAdjustmentId(), request.businessTime(),
                request.reason(), request.updatedBy());
    }

    default MaintenanceSurrenderSettlementResponse toSurrenderSettlementResponse(
            MaintenanceSurrenderSettlementResult result) {
        return new MaintenanceSurrenderSettlementResponse(
                toSettlementResponse(result.settlement()), result.policyCode(), result.policyVersion(),
                result.policyContentHash(), result.policyYear(), result.coolingOffDays(), result.refundType(),
                result.withinCoolingOff(), result.cashValueRate(), result.retainedCustomerAmount(),
                result.internalCostRetentionRate());
    }
}
