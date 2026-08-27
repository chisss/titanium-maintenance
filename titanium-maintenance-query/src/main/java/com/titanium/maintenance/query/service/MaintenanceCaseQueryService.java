package com.titanium.maintenance.query.service;

import java.util.Optional;

import com.titanium.maintenance.query.query.MaintenanceCaseSearchCriteria;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCasePageQueryResult;

/** M3-06 独立保全案件读侧服务。 */
public interface MaintenanceCaseQueryService {

    MaintenanceCasePageQueryResult search(String tenantId, MaintenanceCaseSearchCriteria criteria);

    Optional<MaintenanceCaseDetailQueryResult> findDetail(String tenantId, String maintenanceId);
}
