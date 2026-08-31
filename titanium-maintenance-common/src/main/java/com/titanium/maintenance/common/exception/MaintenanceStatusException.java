package com.titanium.maintenance.common.exception;

import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.IllegalStateTransitionException;

/**
 * 保全状态流转异常
 * <p>
 * 当保全聚合根在非法状态下执行操作或发生非法状态流转时抛出， 如对已完成/已拒绝的保全变更状态、或删除非待处理状态的保全。 继承 metadata
 * 统一异常体系的 {@link IllegalStateTransitionException}， 携带 71 段标准错误码
 * {@link MaintenanceErrorCode#MAINTENANCE_STATUS_TRANSITION_INVALID} 及保全ID、源状态、目标状态等上下文。
 * </p>
 *
 * @author wei.sun
 * @since 2026/6/23
 */
public class MaintenanceStatusException extends IllegalStateTransitionException {

    /** 聚合根类型名（用于异常消息上下文） */
    private static final String AGGREGATE_TYPE = "Maintenance";

    /**
     * 构造保全状态流转异常
     *
     * @param maintenanceId 保全ID
     * @param fromStatus 源状态
     * @param toStatus 目标状态（或尝试执行的操作）
     */
    public MaintenanceStatusException(String maintenanceId, String fromStatus, String toStatus) {
        super(MaintenanceErrorCode.MAINTENANCE_STATUS_TRANSITION_INVALID,
                AGGREGATE_TYPE, maintenanceId, fromStatus, toStatus);
    }

    /**
     * 构造保全状态流转异常（含原因说明）
     *
     * @param maintenanceId 保全ID
     * @param fromStatus 源状态
     * @param toStatus 目标状态（或尝试执行的操作）
     * @param reason 非法原因说明
     */
    public MaintenanceStatusException(String maintenanceId, String fromStatus, String toStatus, String reason) {
        super(MaintenanceErrorCode.MAINTENANCE_STATUS_TRANSITION_INVALID,
                AGGREGATE_TYPE, maintenanceId, fromStatus, toStatus, reason);
    }
}
