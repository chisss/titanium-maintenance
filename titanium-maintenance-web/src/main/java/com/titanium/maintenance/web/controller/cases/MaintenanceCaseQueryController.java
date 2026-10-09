package com.titanium.maintenance.web.controller.cases;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.common.context.RequestContextHolder;
import com.titanium.maintenance.application.query.MaintenanceCaseQueryApplicationService;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.query.query.FindMaintenanceHistoryQuery;
import com.titanium.maintenance.query.query.MaintenanceCaseSearchCriteria;
import com.titanium.maintenance.web.mapper.MaintenanceCaseQueryWebMapper;
import com.titanium.maintenance.web.response.casecreation.MaintenanceCaseDetailVO;
import com.titanium.maintenance.web.response.casecreation.MaintenanceCasePageVO;
import com.titanium.maintenance.web.response.casecreation.MaintenanceHistoryVO;
import com.titanium.maintenance.web.security.MaintenanceCaseQueryAccessResolver;
import com.titanium.metadata.response.ApiResponse;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * 保全案件查询入口：案件分页检索与案件详情。
 *
 * <p>由原 {@code MaintenanceCaseController} 按业务主题拆分而来，端点 URL 与拆分前逐字一致。</p>
 */

@RestController
@Validated
@RequiredArgsConstructor
public class MaintenanceCaseQueryController extends MaintenanceCaseControllerSupport {

    private final MaintenanceCaseQueryApplicationService caseQueryService;
    private final MaintenanceCaseQueryWebMapper caseQueryWebMapper;
    private final MaintenanceCaseQueryAccessResolver queryAccessResolver;

    /** 独立案件列表；初始化未完成案件在查询层不可见。 */
    @GetMapping({"/api/v1/maintenance/cases", "/web/v1/maintenance/cases"})
    public ApiResponse<MaintenanceCasePageVO> search(
         @RequestParam(required = false) @Size(max = 64) String caseId,
         @RequestParam(required = false) @Size(max = 64) String policyNumber,
         @RequestParam(required = false) @Size(max = 64) String customerId,
         @RequestParam(required = false) @Size(max = 64) String itemCode,
         @RequestParam(required = false) MaintenanceChannel source,
         @RequestParam(required = false) MaintenanceStatus status,
         @RequestParam(required = false) @Size(max = 64) String operatorId,
         @RequestParam(required = false)
         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
         @RequestParam(required = false)
         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
         @RequestParam(defaultValue = "0") @Min(0) int page,
         @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        String tenantId = RequestContextHolder.requireTenantId();
        MaintenanceCaseSearchCriteria criteria = new MaintenanceCaseSearchCriteria(
                caseId, policyNumber, customerId, itemCode, source, status, operatorId,
                createdFrom, createdTo, page, size);
        return ApiResponse.success(caseQueryWebMapper.toPageVO(caseQueryService.search(tenantId, criteria)));
    }

    /** 独立案件详情；敏感字段默认脱敏。 */
    @GetMapping({"/api/v1/maintenance/cases/{caseId}", "/web/v1/maintenance/cases/{caseId}"})
    public ApiResponse<MaintenanceCaseDetailVO> detail(
         @PathVariable @NotBlank @Size(max = 64) String caseId) {
        String tenantId = RequestContextHolder.requireTenantId();
        boolean sensitiveDetailsVisible = queryAccessResolver.sensitiveDetailsVisible();
        return ApiResponse.success(caseQueryWebMapper.toDetailVO(
                caseQueryService.findDetail(tenantId, caseId, sensitiveDetailsVisible)));
    }

    /**
     * 跨案保全历史：按保单一次取回全部案件及其字段级变更明细（G05/AC-07）。
     *
     * <p>
     * 检索主键是保单（{@code policyId} 或 {@code policyNumber} 至少一个），其余条件为可选收窄。
     * {@code history} 是字面量路径段，Spring 的 {@code PathPattern} 以字面量优先于变量段，故不会被
     * 同前缀的 {@code /cases/{caseId}} 详情端点吞掉。
     * </p>
     */
    @GetMapping({"/api/v1/maintenance/cases/history", "/web/v1/maintenance/cases/history"})
    public ApiResponse<List<MaintenanceHistoryVO>> history(
         @RequestParam(required = false) @Size(max = 64) String policyId,
         @RequestParam(required = false) @Size(max = 64) String policyNumber,
         @RequestParam(required = false) @Size(max = 64) String itemCode,
         @RequestParam(required = false) MaintenanceBalanceDirection feeDirection,
         @RequestParam(required = false)
         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime effectiveFrom,
         @RequestParam(required = false)
         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime effectiveTo) {
        String tenantId = RequestContextHolder.requireTenantId();
        boolean sensitiveDetailsVisible = queryAccessResolver.sensitiveDetailsVisible();
        FindMaintenanceHistoryQuery query = new FindMaintenanceHistoryQuery(
                tenantId, policyId, policyNumber, itemCode, feeDirection, effectiveFrom, effectiveTo);
        return ApiResponse.success(caseQueryService.findHistory(query, sensitiveDetailsVisible).stream()
                .map(caseQueryWebMapper::toHistoryVO)
                .toList());
    }
}
