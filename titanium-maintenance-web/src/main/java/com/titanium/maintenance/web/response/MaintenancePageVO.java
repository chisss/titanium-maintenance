package com.titanium.maintenance.web.response;

import java.util.List;

/** 管理后台保全分页响应。 */
public record MaintenancePageVO(
        List<MaintenanceVO> list,
        long total,
        int pageNum,
        int pageSize,
        int totalPages) {
}
