package com.titanium.maintenance.web.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.maintenance.api.request.SettleMaintenancePremiumRequest;
import com.titanium.maintenance.api.request.SettleMaintenanceSurrenderRequest;
import com.titanium.maintenance.api.response.MaintenancePremiumSettlementResponse;
import com.titanium.maintenance.api.response.MaintenanceStatisticsResponse;
import com.titanium.maintenance.api.response.MaintenanceSurrenderSettlementResponse;
import com.titanium.maintenance.application.command.premium.MaintenancePremiumSettlementCommandService;
import com.titanium.maintenance.application.service.MaintenanceApplicationService;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.web.dto.casecreation.ChangeMaintenanceStatusDTO;
import com.titanium.maintenance.web.dto.casecreation.CreateMaintenanceDTO;
import com.titanium.maintenance.web.mapper.MaintenanceStatisticsWebMapper;
import com.titanium.maintenance.web.mapper.MaintenanceWebMapper;
import com.titanium.maintenance.web.response.casecreation.MaintenancePageVO;
import com.titanium.maintenance.web.response.casecreation.MaintenanceVO;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保全控制器（后台/端上 HTTP 入口）
 * <p>
 * 面向管理后台/端上，路径 {@code /web/v1/maintenances}，入参为 web 层 {@code XxxDTO}（web/dto）、出参
 * {@code MaintenanceVO}，<b>不再 implements MaintenanceApi</b>（远程契约由 {@code MaintenanceApiProvider}
 * 承接）。请求体的枚举码值在边界转为领域枚举后交 {@link MaintenanceApplicationService} 编排；读侧查询结果
 * 经 {@link MaintenanceWebMapper} 转 VO 返回。与 {@code MaintenanceApiProvider} 平行收敛到同一应用层门面。
 * </p>
 */
@RestController
@RequestMapping("/web/v1/maintenances")
@Validated
@RequiredArgsConstructor
@Slf4j
public class MaintenanceController {

    private final MaintenanceApplicationService maintenanceApplicationService;
    private final MaintenancePremiumSettlementCommandService premiumSettlementCommandService;
    private final MaintenanceWebMapper          maintenanceWebMapper;
    private final MaintenanceStatisticsWebMapper maintenanceStatisticsWebMapper;

    // 创建保全记录
    @PostMapping
    public ResponseEntity<String> createMaintenance(@Valid @RequestBody CreateMaintenanceDTO request,
                                                    @RequestHeader("X-Tenant-Id") String tenantId) {
        try {
            CompletableFuture<String> future = maintenanceApplicationService.createMaintenanceCase(
                    request.getPolicyId(),
                    request.getCustomerId(),
                    MaintenanceType.fromValue(request.getMaintenanceType()),
                    EffectiveTimeType.fromCode(request.getEffectiveTimeType()),
                    request.getSpecificEffectiveDate(),
                    request.getDescription(),
                    request.getCreatedBy(),
                    tenantId);
            String maintenanceId = future.get();
            return ResponseEntity.status(HttpStatus.CREATED).body(maintenanceId);
        } catch (InterruptedException | ExecutionException e) {
            log.error("创建保全案件失败: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // 变更保全记录状态
    @PutMapping("/{id}/status")
    public ResponseEntity<String> changeMaintenanceStatus(@PathVariable("id") @NotBlank @Size(max = 36) String id,
                                                          @Valid @RequestBody ChangeMaintenanceStatusDTO request,
                                                          @RequestHeader("X-Tenant-Id") String tenantId) {
        try {
            CompletableFuture<String> future = maintenanceApplicationService.changeMaintenanceStatus(
                    id,
                    MaintenanceStatus.fromValue(request.getNewStatus()),
                    request.getChangeReason(),
                    request.getChangedBy(), tenantId);
            future.get();
            return ResponseEntity.ok(id);
        } catch (InterruptedException | ExecutionException e) {
            log.error("变更保全状态失败: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // 根据ID查询保全记录
    @GetMapping("/{id}")
    public ResponseEntity<MaintenanceVO> getMaintenanceById(@PathVariable("id") @NotBlank @Size(max = 36) String id,
                                                            @RequestHeader("X-Tenant-Id") String tenantId) {
        MaintenanceVO vo = maintenanceWebMapper.toVO(maintenanceApplicationService.findMaintenanceById(id, tenantId));
        return ResponseEntity.ok(vo);
    }

    // 根据保单ID查询保全记录
    @GetMapping("/policy/{policyId}")
    public ResponseEntity<List<MaintenanceVO>> getMaintenancesByPolicyId(
            @PathVariable("policyId") @NotBlank @Size(max = 36) String policyId,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        List<MaintenanceVO> vos = maintenanceApplicationService.findMaintenancesByPolicyId(policyId, tenantId).stream()
                .map(maintenanceWebMapper::toVO)
                .toList();
        return ResponseEntity.ok(vos);
    }

    /**
     * 多条件搜索保全案件
     * <p>
     * 路由优先级：policyId → customerId → status 全量 → 返回空。
     * 在候选集基础上按 maintenanceType、status 做内存过滤后分页返回。
     * </p>
     */
    @GetMapping("/search")
    public ResponseEntity<List<MaintenanceVO>> searchMaintenances(
            @RequestParam(required = false) String policyId,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String maintenanceType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(200) int size,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        List<MaintenanceVO> vos = maintenanceApplicationService
                .searchMaintenances(policyId, customerId, maintenanceType, status, page, size, tenantId)
                .stream()
                .map(maintenanceWebMapper::toVO)
                .toList();
        return ResponseEntity.ok(vos);
    }

    /** 保全后台真实分页查询；旧 search 裸列表契约继续保留兼容。 */
    @GetMapping("/search-page")
    public ResponseEntity<MaintenancePageVO> searchMaintenancePage(
            @RequestParam(required = false) String policyId,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String maintenanceType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(200) int size,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        var result = maintenanceApplicationService.searchMaintenancePage(
                policyId, customerId, maintenanceType, status, page, size, tenantId);
        return ResponseEntity.ok(new MaintenancePageVO(
                result.list().stream().map(maintenanceWebMapper::toVO).toList(),
                result.total(), result.pageNum(), result.pageSize(), result.totalPages()));
    }

    /**
     * 保全统计（管理后台看板聚合）
     * <p>
     * 返回处理中保全工单数（PENDING/PROCESSING）、今日新增保全数、保全总数，按租户隔离。
     * </p>
     *
     * @param tenantId 租户ID（请求头 X-Tenant-Id）
     * @return 保全统计结果
     */
    @GetMapping("/statistics")
    public ResponseEntity<MaintenanceStatisticsResponse> getStatistics(
            @RequestHeader("X-Tenant-Id") String tenantId) {
        return ResponseEntity.ok(
                maintenanceStatisticsWebMapper.toResponse(maintenanceApplicationService.getStatistics(tenantId)));
    }

    /**
     * 查询待处理保全案件列表（状态为 PENDING）
     */
    @GetMapping("/pending")
    public ResponseEntity<List<MaintenanceVO>> getPendingMaintenances(
            @RequestHeader("X-Tenant-Id") String tenantId) {
        List<MaintenanceVO> vos = maintenanceApplicationService.findPendingMaintenances(tenantId).stream()
                .map(maintenanceWebMapper::toVO)
                .toList();
        return ResponseEntity.ok(vos);
    }

    // 添加保全变更记录
    @PostMapping("/{id}/changes")
    public ResponseEntity<String> addMaintenanceChange(@PathVariable("id") @NotBlank @Size(max = 36) String id,
                                                       @RequestParam("changeType") @NotBlank String changeType,
                                                       @RequestParam("fieldName") @NotBlank String fieldName,
                                                       @RequestParam("oldValue") String oldValue,
                                                       @RequestParam("newValue") String newValue,
                                                       @RequestParam("createdBy") @NotBlank String createdBy,
                                                       @RequestHeader("X-Tenant-Id") String tenantId) {
        try {
            CompletableFuture<String> future = maintenanceApplicationService.addMaintenanceChange(
                    id, changeType, fieldName, oldValue, newValue, createdBy, tenantId);
            future.get();
            return ResponseEntity.ok(id);
        } catch (InterruptedException | ExecutionException e) {
            log.error("添加保全变更记录失败: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // 计算保全保费
    @PostMapping("/{id}/calculate-premium")
    public ResponseEntity<String> calculateMaintenancePremium(@PathVariable("id") @NotBlank @Size(max = 36) String id,
                                                              @RequestParam("totalAmount") BigDecimal totalAmount,
                                                              @RequestParam("refundAmount") BigDecimal refundAmount,
                                                              @RequestParam("calculationDetails") String calculationDetails,
                                                              @RequestParam("updatedBy") @NotBlank String updatedBy,
                                                              @RequestHeader("X-Tenant-Id") String tenantId) {
        try {
            CompletableFuture<String> future = maintenanceApplicationService.calculateMaintenancePremium(
                    id, totalAmount, refundAmount, calculationDetails, updatedBy, tenantId);
            future.get();
            return ResponseEntity.ok(id);
        } catch (InterruptedException | ExecutionException e) {
            log.error("计算保全保费失败: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /** 新权威入口：不接收人工金额，由 Maintenance 编排 Product 与 Billing。 */
    @PostMapping("/{id}/premium-settlements")
    public ResponseEntity<MaintenancePremiumSettlementResponse> settlePremium(
            @PathVariable("id") @NotBlank @Size(max = 36) String id,
            @Valid @RequestBody SettleMaintenancePremiumRequest request,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        return ResponseEntity.ok(maintenanceWebMapper.toSettlementResponse(
                premiumSettlementCommandService.settle(
                        id, tenantId, maintenanceWebMapper.toSettlementInput(request))));
    }

    /** 退保权威入口：现金价值由 Product 已发布策略计算，客户端不提交金额。 */
    @PostMapping("/{id}/surrender-settlements")
    public ResponseEntity<MaintenanceSurrenderSettlementResponse> settleSurrender(
            @PathVariable("id") @NotBlank @Size(max = 36) String id,
            @Valid @RequestBody SettleMaintenanceSurrenderRequest request,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        return ResponseEntity.ok(maintenanceWebMapper.toSurrenderSettlementResponse(
                premiumSettlementCommandService.settleSurrender(
                        id, tenantId, maintenanceWebMapper.toSurrenderSettlementInput(request))));
    }

    // 执行保全
    @PostMapping("/{id}/execute")
    public ResponseEntity<String> executeMaintenance(@PathVariable("id") @NotBlank @Size(max = 36) String id,
                                                     @RequestParam("effectiveTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime effectiveTime,
                                                     @RequestParam("executionDetails") String executionDetails,
                                                     @RequestParam("updatedBy") @NotBlank String updatedBy,
                                                     @RequestHeader("X-Tenant-Id") String tenantId) {
        try {
            CompletableFuture<String> future = maintenanceApplicationService.executeMaintenance(
                    id, effectiveTime, executionDetails, updatedBy, tenantId);
            future.get();
            return ResponseEntity.ok(id);
        } catch (InterruptedException | ExecutionException e) {
            log.error("执行保全失败: {}", e.getMessage(), e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
            while (cause.getCause() != null && !(cause instanceof RuntimeException)) {
                cause = cause.getCause();
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
