package com.titanium.maintenance.api;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.titanium.maintenance.api.request.ChangeMaintenanceStatusRequest;
import com.titanium.maintenance.api.request.CreateMaintenanceRequest;
import com.titanium.maintenance.api.request.SettleMaintenancePremiumRequest;
import com.titanium.maintenance.api.request.SettleMaintenanceReversalRequest;
import com.titanium.maintenance.api.request.SettleMaintenanceSurrenderRequest;
import com.titanium.maintenance.api.response.MaintenancePremiumSettlementResponse;
import com.titanium.maintenance.api.response.MaintenanceResponse;
import com.titanium.maintenance.api.response.MaintenanceSurrenderSettlementResponse;
import com.titanium.metadata.response.ApiResponse;

import jakarta.validation.Valid;

/**
 * 保全聚合对外契约（Feign）
 * <p>
 * 命名主键为聚合根 {@code Maintenance}（非 maintenance 域），承载保全案件的跨服务远程调用。契约路径遵从
 * 内部服务远程调用规约 {@code /api/v1/maintenances}，由 web 层 {@code MaintenanceApiProvider} 实现，
 * 路径不得篡改。所有方法透传 {@code X-Tenant-Id} 请求头贯穿多租户上下文，入出参一律使用 api 层
 * {@code XxxRequest} / {@code XxxResponse}（🔴 api 层已弃用 {@code XxxDTO}，§3.4.11 ①；领域枚举以 String
 * 承载，api 自包含、不耦合领域内核）。
 * </p>
 * <p>
 * 同域多个 {@code @FeignClient} 的 {@code name} 相同，必须各配唯一 {@code contextId}，否则 Spring
 * 启动报「Multiple @FeignClient with the same name」Bean 冲突。原 {@code MaintenanceApiClient}
 * （api/client）为 Client 后缀的老式命名，已重命名为本接口（命名主键=聚合根 Maintenance）。
 * </p>
 * <p>
 * 🔴 m24-01a 起统一返回 {@link ApiResponse} 信封：调用方先判 {@code isSuccess()} 再取 {@code getData()}；
 * 失败仍由全局异常处理器以非 2xx 表达（Feign 抛 {@code FeignException}），信封化只改成功体的成形。
 * </p>
 */
@FeignClient(name = "titanium-maintenance", contextId = "maintenanceApi", path = "/api/v1/maintenances")
public interface MaintenanceApi {

    /**
     * 创建保全案件
     *
     * @param request 创建保全请求 DTO
     * @return 统一信封，data 为保全案件ID
     */
    @PostMapping
    ApiResponse<String> createMaintenance(@RequestBody @Valid CreateMaintenanceRequest request);

    /**
     * 变更保全案件状态
     *
     * @param id 保全案件ID
     * @param request 状态变更请求 DTO
     * @return 统一信封，data 为保全案件ID
     */
    @PutMapping("/{id}/status")
    ApiResponse<String> changeMaintenanceStatus(@PathVariable String id,
                                                @RequestBody @Valid ChangeMaintenanceStatusRequest request);

    /** 由 Maintenance 编排 Product 替代计算、差额确认与 Billing 余额登记。 */
    @PostMapping("/{id}/premium-settlements")
    ApiResponse<MaintenancePremiumSettlementResponse> settlePremium(
            @PathVariable String id,
            @RequestBody @Valid SettleMaintenancePremiumRequest request);

    /** 将既有 Product 生命周期差额生成反向事实并登记 Billing。 */
    @PostMapping("/{id}/reversal-settlements")
    ApiResponse<MaintenancePremiumSettlementResponse> settleReversal(
            @PathVariable String id,
            @RequestBody @Valid SettleMaintenanceReversalRequest request);

    /**
     * 退保结算：登记退保价值证据、账务贷项与资金退款事实。
     * <p>
     * 与 web 入口 {@code POST /web/v1/maintenances/{id}/surrender-settlements} 是**同一能力的两个入口**
     * （索引文档 §五 第 11 条「入口通道对称性」）：二者各经映射器转出**同一个**入参、调用**同一个**应用层
     * 门面 {@code MaintenancePremiumSettlementCommandService#settleSurrender}，互不依赖、不得互调。
     * 对称性由 {@code SurrenderSettlementEntrySymmetryTest} 以「两侧交入门面的实参逐字相等」守住。
     * </p>
     */
    @PostMapping("/{id}/surrender-settlements")
    ApiResponse<MaintenanceSurrenderSettlementResponse> settleSurrender(
            @PathVariable String id,
            @RequestBody @Valid SettleMaintenanceSurrenderRequest request);

    /**
     * 根据ID查询保全案件（跨域集成用）
     *
     * @param id 保全案件ID
     * @return 统一信封，data 为保全案件详情
     */
    @GetMapping("/{id}")
    ApiResponse<MaintenanceResponse> getMaintenanceById(@PathVariable String id);

    /**
     * 根据保单ID查询保全案件列表
     *
     * @param policyId 保单ID
     * @return 统一信封，data 为保全案件列表
     */
    @GetMapping("/policy/{policyId}")
    ApiResponse<List<MaintenanceResponse>> getMaintenancesByPolicyId(@PathVariable String policyId);
}
