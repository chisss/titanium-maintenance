package com.titanium.maintenance.configuration.control;

import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 保全项在单一受理渠道上的能力。 */
public record MaintenanceChannelCapability(MaintenanceChannel channel, boolean autoApprovalAllowed) {

    public MaintenanceChannelCapability {
        if (channel == null) {
            throw new MaintenanceValidationException("MaintenanceChannelCapability", "channel", "渠道不能为空");
        }
        if (autoApprovalAllowed && channel != MaintenanceChannel.API) {
            throw new MaintenanceValidationException(
                    "MaintenanceChannelCapability", "autoApprovalAllowed", "只有 API 渠道可以启用自动审核");
        }
    }

    /** 创建不允许自动审核的渠道能力。 */
    public static MaintenanceChannelCapability manualApproval(MaintenanceChannel channel) {
        return new MaintenanceChannelCapability(channel, false);
    }
}
