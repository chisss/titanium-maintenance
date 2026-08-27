package com.titanium.maintenance.web.response;

import java.time.LocalDateTime;
import java.util.List;

import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.workflow.MaintenanceEffectStatus;

/** 独立保全案件分页响应。 */
public record MaintenanceCasePageVO(
        List<MaintenanceCaseSummaryVO> list,
        long total,
        int page,
        int size,
        int totalPages) {

    public MaintenanceCasePageVO {
        list = List.copyOf(list);
    }

    /** 保全管理列表行。 */
    public record MaintenanceCaseSummaryVO(
            String caseId,
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

        public MaintenanceCaseSummaryVO {
            itemCodes = List.copyOf(itemCodes);
        }
    }
}
