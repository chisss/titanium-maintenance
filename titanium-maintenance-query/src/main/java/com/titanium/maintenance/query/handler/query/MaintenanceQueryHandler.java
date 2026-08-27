package com.titanium.maintenance.query.handler.query;

import java.util.List;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import com.titanium.maintenance.query.query.FindMaintenanceByIdQuery;
import com.titanium.maintenance.query.query.FindMaintenancesByCustomerIdQuery;
import com.titanium.maintenance.query.query.FindMaintenancesByPolicyIdQuery;
import com.titanium.maintenance.query.result.MaintenanceQueryResult;
import com.titanium.maintenance.query.service.MaintenanceQueryService;

import lombok.RequiredArgsConstructor;

/**
 * 保全查询处理器（CQRS 读侧 Axon 查询处理）
 * <p>
 * 接收 {@code FindXxxQuery}，委托 {@link MaintenanceQueryService} 查询读模型并返回 DTO（不存在时返回 {@code null}）。
 * </p>
 */
@Component
@RequiredArgsConstructor
@ProcessingGroup("maintenance-query-group")
public class MaintenanceQueryHandler {

    private final MaintenanceQueryService maintenanceQueryService;

    @QueryHandler
    public MaintenanceQueryResult handle(FindMaintenanceByIdQuery query) {
        return maintenanceQueryService.getMaintenanceSummary(query.maintenanceId(), query.tenantId()).orElse(null);
    }

    @QueryHandler
    public List<MaintenanceQueryResult> handle(FindMaintenancesByPolicyIdQuery query) {
        return maintenanceQueryService.getMaintenanceSummariesByPolicyId(query.policyId(), query.tenantId());
    }

    @QueryHandler
    public List<MaintenanceQueryResult> handle(FindMaintenancesByCustomerIdQuery query) {
        return maintenanceQueryService.getMaintenanceSummariesByCustomerId(query.customerId(), query.tenantId());
    }
}
