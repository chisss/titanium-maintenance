package com.titanium.maintenance.api.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 保全统计远程响应契约（Feign 出参）
 * <p>
 * 面向管理后台等跨服务消费者的对外传输契约，承载保全维度聚合统计：处理中保全工单数、今日新增保全数、
 * 保全总数。由 web 层经 MapStruct 从应用层读用例出参 {@code MaintenanceStatisticsResult} 转换而来，
 * 作为稳定协议隔离内部读模型结构。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceStatisticsResponse {

    /** 处理中保全工单数（状态为待处理 PENDING 或处理中 PROCESSING） */
    private long processingMaintenanceCount;

    /** 今日新增保全数（创建时间落在今日） */
    private long todayMaintenanceCount;

    /** 保全总数 */
    private long totalMaintenanceCount;
}
