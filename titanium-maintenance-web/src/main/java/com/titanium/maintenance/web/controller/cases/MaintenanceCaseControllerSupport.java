package com.titanium.maintenance.web.controller.cases;

import com.titanium.maintenance.common.enums.config.MaintenanceChannel;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 保全案件 Controller 家族共享的表现层支撑：把请求来源解析为渠道枚举。
 *
 * <p>来源判定只看请求 URI 的 {@code /api/} 前缀（系统 API 与后台手工两大入口），
 * 属表现层关注点、与具体业务无关，故由抽象基类统一持有，避免在各案件 Controller 中重复实现。</p>
 */

public abstract class MaintenanceCaseControllerSupport {



    protected MaintenanceChannel source(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/")
                ? MaintenanceChannel.API
                : MaintenanceChannel.MANUAL;
    }
}
