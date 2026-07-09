package com.titanium.maintenance.common.enums;

import com.titanium.maintenance.common.constant.MaintenanceConstants;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 保全案件状态枚举
 * <p>
 * 表示保全案件的生命周期状态，为保全域专用分类。
 * {@code code} 为语言无关稳定标识（与常量名一致，取自 {@link MaintenanceConstants}），
 * {@code enumCode} 为持久化数字码，从 1 顺序编号。
 */
@Getter
public enum MaintenanceStatus implements BaseEnum {
    PENDING(1, MaintenanceConstants.MAINTENANCE_STATUS_PENDING, "待处理"),
    PROCESSING(2, MaintenanceConstants.MAINTENANCE_STATUS_PROCESSING, "处理中"),
    APPROVED(3, MaintenanceConstants.MAINTENANCE_STATUS_APPROVED, "已核准"),
    REJECTED(4, MaintenanceConstants.MAINTENANCE_STATUS_REJECTED, "已拒绝"),
    COMPLETED(5, MaintenanceConstants.MAINTENANCE_STATUS_COMPLETED, "已完成");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    MaintenanceStatus(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }

    /**
     * 按 code 反查保全状态（统一范式入口，委托 {@link BaseEnum}）。
     *
     * @param code 保全状态码
     * @return 匹配的枚举，未匹配返回 null
     */
    public static MaintenanceStatus fromCode(String code) {
        return BaseEnum.fromCode(MaintenanceStatus.class, code);
    }

    /**
     * 保全状态码值（兼容旧调用）。
     *
     * @return 与 {@link #getCode()} 等价的码值
     * @deprecated 请改用 {@link #getCode()}，本方法仅为兼容既有调用点保留
     */
    @Deprecated
    public String getValue() {
        return code;
    }

    /**
     * 按码值反查（兼容旧调用），未匹配抛出保全校验异常。
     *
     * @param value 保全状态码
     * @return 匹配的枚举
     * @deprecated 请改用 {@link #fromCode(String)}，本方法仅为兼容既有调用点保留
     */
    @Deprecated
    public static MaintenanceStatus fromValue(String value) {
        MaintenanceStatus status = fromCode(value);
        if (status == null) {
            throw new MaintenanceValidationException("MaintenanceStatus", "value", "无效的保全状态: " + value);
        }
        return status;
    }
}
