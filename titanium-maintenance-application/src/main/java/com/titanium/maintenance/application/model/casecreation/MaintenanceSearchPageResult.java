package com.titanium.maintenance.application.model.casecreation;

import java.util.List;

/** 保全搜索分页结果，total 为过滤后、分页前的租户内总数。 */
public record MaintenanceSearchPageResult(
        List<MaintenanceReadModel> list,
        long total,
        int pageNum,
        int pageSize,
        int totalPages) {

    public static MaintenanceSearchPageResult of(
            List<MaintenanceReadModel> list, long total, int pageNum, int pageSize) {
        int totalPages = pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0;
        return new MaintenanceSearchPageResult(List.copyOf(list), total, pageNum, pageSize, totalPages);
    }
}
