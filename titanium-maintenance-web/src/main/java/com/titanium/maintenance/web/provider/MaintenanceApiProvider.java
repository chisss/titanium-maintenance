package com.titanium.maintenance.web.provider;

import java.util.List;
import java.util.concurrent.ExecutionException;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.common.context.RequestContextHolder;
import com.titanium.maintenance.api.MaintenanceApi;
import com.titanium.maintenance.api.request.ChangeMaintenanceStatusRequest;
import com.titanium.maintenance.api.request.CreateMaintenanceRequest;
import com.titanium.maintenance.api.request.SettleMaintenancePremiumRequest;
import com.titanium.maintenance.api.request.SettleMaintenanceReversalRequest;
import com.titanium.maintenance.api.request.SettleMaintenanceSurrenderRequest;
import com.titanium.maintenance.api.response.MaintenancePremiumSettlementResponse;
import com.titanium.maintenance.api.response.MaintenanceResponse;
import com.titanium.maintenance.api.response.MaintenanceSurrenderSettlementResponse;
import com.titanium.maintenance.application.command.premium.MaintenancePremiumSettlementCommandService;
import com.titanium.maintenance.application.service.MaintenanceApplicationService;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.web.mapper.MaintenanceWebMapper;
import com.titanium.metadata.enums.maintenance.MaintenanceType;
import com.titanium.metadata.response.ApiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保全契约实现（Provider）
 * <p>
 * 承接 {@link MaintenanceApi} Feign 契约，面向其它微服务的远程调用。路径由 {@link MaintenanceApi} 的
 * {@code @RequestMapping("/api/v1/maintenances")} 唯一定义，本类通过 {@code implements} 继承，
 * <b>不重复标注、不篡改</b>。职责仅为协议转换（api DTO 码值 → 领域枚举 → 应用层门面入参）+ 组装对外
 * DTO，零业务逻辑。与面向后台/端上的 {@code MaintenanceController} 平行收敛到同一
 * {@link MaintenanceApplicationService}。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/maintenances")
@RequiredArgsConstructor
@Slf4j
public class MaintenanceApiProvider implements MaintenanceApi {

    private final MaintenanceApplicationService maintenanceApplicationService;
    private final MaintenancePremiumSettlementCommandService premiumSettlementCommandService;
    private final MaintenanceWebMapper          maintenanceWebMapper;

    @Override
    public ApiResponse<String> createMaintenance(CreateMaintenanceRequest request) {
        try {
            // 协议转换：DTO 码值 → 领域枚举，收敛到同一应用层门面
            return ApiResponse.success(maintenanceApplicationService.createMaintenanceCase(
                    request.getPolicyId(),
                    request.getCustomerId(),
                    MaintenanceType.fromValue(request.getMaintenanceType()),
                    EffectiveTimeType.fromCode(request.getEffectiveTimeType()),
                    request.getSpecificEffectiveDate(),
                    request.getDescription(),
                    request.getCreatedBy(),
                    RequestContextHolder.requireTenantId()).get());
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            log.error("远程创建保全案件失败: {}", e.getMessage(), e);
            throw new IllegalStateException("创建保全案件失败", e);
        }
    }

    @Override
    public ApiResponse<String> changeMaintenanceStatus(String id, ChangeMaintenanceStatusRequest request) {
        try {
            return ApiResponse.success(maintenanceApplicationService.changeMaintenanceStatus(
                    id,
                    MaintenanceStatus.fromValue(request.getNewStatus()),
                    request.getChangeReason(),
                    request.getChangedBy(), RequestContextHolder.requireTenantId()).get());
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            log.error("远程变更保全状态失败: {}", e.getMessage(), e);
            throw new IllegalStateException("变更保全状态失败", e);
        }
    }

    @Override
    public ApiResponse<MaintenancePremiumSettlementResponse> settlePremium(
            String id, SettleMaintenancePremiumRequest request) {
        return ApiResponse.success(maintenanceWebMapper.toSettlementResponse(
                premiumSettlementCommandService.settle(id, RequestContextHolder.requireTenantId(),
                maintenanceWebMapper.toSettlementInput(request))));
    }

    @Override
    public ApiResponse<MaintenancePremiumSettlementResponse> settleReversal(
            String id, SettleMaintenanceReversalRequest request) {
        return ApiResponse.success(maintenanceWebMapper.toSettlementResponse(
                premiumSettlementCommandService.settleReversal(id, RequestContextHolder.requireTenantId(),
                        maintenanceWebMapper.toReversalSettlementInput(request))));
    }

    /**
     * 退保结算（api 入口）。
     * <p>
     * 与 web 入口 {@code MaintenanceController#settleSurrender} 平行收敛：两侧各经
     * {@link MaintenanceWebMapper} 转出**同一个** {@code MaintenanceSurrenderSettlementInput}，调用**同一个**
     * {@link MaintenancePremiumSettlementCommandService#settleSurrender}。本方法刻意**不**复用 web 控制器的
     * 任何代码路径，两侧互不依赖；其对称性由 {@code SurrenderSettlementEntrySymmetryTest} 以「交入门面的实参
     * 逐字相等」守住。
     * </p>
     */
    @Override
    public ApiResponse<MaintenanceSurrenderSettlementResponse> settleSurrender(
            String id, SettleMaintenanceSurrenderRequest request) {
        return ApiResponse.success(maintenanceWebMapper.toSurrenderSettlementResponse(
                premiumSettlementCommandService.settleSurrender(id, RequestContextHolder.requireTenantId(),
                        maintenanceWebMapper.toSurrenderSettlementInput(request))));
    }

    @Override
    public ApiResponse<MaintenanceResponse> getMaintenanceById(String id) {
        return ApiResponse.success(maintenanceWebMapper.toApiResponse(
                maintenanceApplicationService.findMaintenanceById(id, RequestContextHolder.requireTenantId())));
    }

    @Override
    public ApiResponse<List<MaintenanceResponse>> getMaintenancesByPolicyId(String policyId) {
        return ApiResponse.success(maintenanceApplicationService
                .findMaintenancesByPolicyId(policyId, RequestContextHolder.requireTenantId()).stream()
                .map(maintenanceWebMapper::toApiResponse)
                .toList());
    }
}
