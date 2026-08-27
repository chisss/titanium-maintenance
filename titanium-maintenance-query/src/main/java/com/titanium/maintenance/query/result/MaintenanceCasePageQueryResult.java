package com.titanium.maintenance.query.result;

import java.time.LocalDateTime;
import java.util.List;

import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.workflow.MaintenanceEffectStatus;

/** 独立保全案件分页查询结果。 */
public record MaintenanceCasePageQueryResult(
        List<MaintenanceCaseSummaryQueryResult> list,
        long total,
        int page,
        int size,
        int totalPages) {

    public MaintenanceCasePageQueryResult {
        list = List.copyOf(list);
    }

    /** 保全管理列表单行数据。 */
    public record MaintenanceCaseSummaryQueryResult(
            String maintenanceId,
            String policyId,
            String policyNumber,
            String customerId,
            List<String> itemCodes,
            MaintenanceChannel source,
            MaintenanceStatus status,
            MaintenanceEffectStatus effectStatus,
            String operatorId,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {

        public MaintenanceCaseSummaryQueryResult {
            itemCodes = List.copyOf(itemCodes);
        }

        /** 兼容 M5-01 之前不含生效状态的内部列表构造。 */
        public MaintenanceCaseSummaryQueryResult(
                String maintenanceId,
                String policyId,
                String policyNumber,
                String customerId,
                List<String> itemCodes,
                MaintenanceChannel source,
                MaintenanceStatus status,
                String operatorId,
                LocalDateTime createdAt,
                LocalDateTime updatedAt) {
            this(maintenanceId, policyId, policyNumber, customerId, itemCodes, source, status,
                    MaintenanceEffectStatus.NOT_STARTED, operatorId, createdAt, updatedAt);
        }
    }
}
