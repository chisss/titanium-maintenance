package com.titanium.maintenance.api;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import com.titanium.maintenance.api.request.ChangeMaintenanceStatusRequest;
import com.titanium.maintenance.api.request.CreateMaintenanceRequest;
import com.titanium.maintenance.api.response.MaintenanceResponse;

import jakarta.validation.Valid;

/**
 * 保全聚合对外契约（Feign）
 * <p>
 * 命名主键为聚合根 {@code Maintenance}（非 maintenance 域），承载保全案件的跨服务远程调用。契约路径遵从
 * 内部服务远程调用规约 {@code /api/v1/maintenances}，由 web 层 {@code MaintenanceApiProvider} 实现，
 * 路径不得篡改。所有方法透传 {@code X-Tenant-Id} 请求头贯穿多租户上下文，入出参一律使用 api 层 DTO
 * （领域枚举以 String 承载，api 自包含、不耦合领域内核）。
 * </p>
 * <p>
 * 同域多个 {@code @FeignClient} 的 {@code name} 相同，必须各配唯一 {@code contextId}，否则 Spring
 * 启动报「Multiple @FeignClient with the same name」Bean 冲突。原 {@code MaintenanceApiClient}
 * （api/client）为 Client 后缀的老式命名，已重命名为本接口（命名主键=聚合根 Maintenance）。
 * </p>
 */
@FeignClient(name = "titanium-maintenance", contextId = "maintenanceApi")
@RequestMapping("/api/v1/maintenances")
public interface MaintenanceApi {

    /**
     * 创建保全案件
     *
     * @param request 创建保全请求 DTO
     * @param tenantId 租户ID
     * @return 保全案件ID
     */
    @PostMapping
    String createMaintenance(@RequestBody @Valid CreateMaintenanceRequest request,
                             @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 变更保全案件状态
     *
     * @param id 保全案件ID
     * @param request 状态变更请求 DTO
     * @param tenantId 租户ID
     * @return 保全案件ID
     */
    @PutMapping("/{id}/status")
    String changeMaintenanceStatus(@PathVariable("id") String id,
                                   @RequestBody @Valid ChangeMaintenanceStatusRequest request,
                                   @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 根据ID查询保全案件（跨域集成用）
     *
     * @param id 保全案件ID
     * @param tenantId 租户ID
     * @return 保全案件详情
     */
    @GetMapping("/{id}")
    MaintenanceResponse getMaintenanceById(@PathVariable("id") String id,
                                           @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 根据保单ID查询保全案件列表
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 保全案件列表
     */
    @GetMapping("/policy/{policyId}")
    List<MaintenanceResponse> getMaintenancesByPolicyId(@PathVariable("policyId") String policyId,
                                                        @RequestHeader("X-Tenant-Id") String tenantId);
}
