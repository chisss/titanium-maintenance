package com.titanium.maintenance.query.service.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.maintenance.query.mapper.MaintenanceQueryResultMapper;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.result.MaintenanceQueryResult;
import com.titanium.maintenance.query.service.MaintenanceQueryService;
import com.titanium.maintenance.query.view.MaintenanceView;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保全查询服务实现（CQRS 读侧）
 * <p>
 * 查询读模型表 {@code t_maintenance_view}（由 {@code MaintenanceProjectionEventHandler} 投影维护），
 * 经 MapStruct 映射器组装为稳定 DTO 返回，禁止直接返回读模型实体。
 * </p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MaintenanceQueryServiceImpl implements MaintenanceQueryService {

    private final MaintenanceViewRepository maintenanceViewRepository;
    private final MaintenanceQueryResultMapper maintenanceQueryResultMapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<MaintenanceQueryResult> getMaintenanceSummary(String maintenanceId, String tenantId) {
        return maintenanceViewRepository.findByMaintenanceIdAndTenantId(maintenanceId, tenantId)
                .filter(MaintenanceView::isOperatorVisible)
                .map(maintenanceQueryResultMapper::toResult);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MaintenanceQueryResult> getMaintenanceSummariesByPolicyId(String policyId, String tenantId) {
        return maintenanceViewRepository.findByPolicyIdAndTenantId(policyId, tenantId).stream()
                .filter(MaintenanceView::isOperatorVisible)
                .map(maintenanceQueryResultMapper::toResult)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MaintenanceQueryResult> getMaintenanceSummariesByCustomerId(String customerId, String tenantId) {
        return maintenanceViewRepository.findByCustomerIdAndTenantId(customerId, tenantId).stream()
                .filter(MaintenanceView::isOperatorVisible)
                .map(maintenanceQueryResultMapper::toResult)
                .toList();
    }
}
