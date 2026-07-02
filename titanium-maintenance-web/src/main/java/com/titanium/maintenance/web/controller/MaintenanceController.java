package com.titanium.maintenance.web.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

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

import com.titanium.maintenance.api.dto.ChangeMaintenanceStatusRequest;
import com.titanium.maintenance.api.dto.CreateMaintenanceRequest;
import com.titanium.maintenance.api.dto.MaintenanceResponse;
import com.titanium.maintenance.application.service.MaintenanceApplicationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/maintenances")
@Validated
@Slf4j
public class MaintenanceController {
    private final MaintenanceApplicationService maintenanceApplicationService;

    public MaintenanceController(MaintenanceApplicationService maintenanceApplicationService) {
        this.maintenanceApplicationService = maintenanceApplicationService;
    }

    // 创建保全记录
    @PostMapping
    public ResponseEntity<String> createMaintenance(@Valid @RequestBody CreateMaintenanceRequest request,
                                                  @RequestHeader("X-Tenant-ID") String tenantId) {
        try {
            CompletableFuture<String> future = maintenanceApplicationService.createMaintenanceCase(
                    request.getPolicyId(),
                    request.getCustomerId(),
                    request.getMaintenanceType(),
                    request.getEffectiveTimeType(),
                    request.getSpecificEffectiveDate(),
                    request.getDescription(),
                    request.getCreatedBy(),
                    tenantId
            );
            String maintenanceId = future.get();
            return ResponseEntity.status(HttpStatus.CREATED).body(maintenanceId);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to create maintenance: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // 变更保全记录状态
    @PutMapping("/{id}/status")
    public ResponseEntity<String> changeMaintenanceStatus(@PathVariable("id")
                                                         @NotBlank @Size(max = 36) String id,
                                                         @Valid @RequestBody ChangeMaintenanceStatusRequest request,
                                                         @RequestHeader("X-Tenant-ID") String tenantId) {
        try {
            CompletableFuture<String> future = maintenanceApplicationService.changeMaintenanceStatus(
                    id,
                    request.getNewStatus(),
                    request.getChangeReason(),
                    request.getChangedBy()
            );
            future.get();
            return ResponseEntity.ok(id);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to change maintenance status: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // 根据ID查询保全记录
    @GetMapping("/{id}")
    public ResponseEntity<MaintenanceResponse> getMaintenanceById(@PathVariable("id")
                                                                 @NotBlank @Size(max = 36) String id,
                                                                 @RequestHeader("X-Tenant-ID") String tenantId) {
        var maintenance = maintenanceApplicationService.findMaintenanceById(id);
        var response = convertToResponse(maintenance);
        return ResponseEntity.ok(response);
    }

    // 根据保单ID查询保全记录
    @GetMapping("/policy/{policyId}")
    public ResponseEntity<List<MaintenanceResponse>> getMaintenancesByPolicyId(
            @PathVariable("policyId") @NotBlank @Size(max = 36) String policyId,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        var maintenances = maintenanceApplicationService.findMaintenancesByPolicyId(policyId);
        var responses = maintenances.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    // 添加保全变更记录
    @PostMapping("/{id}/changes")
    public ResponseEntity<String> addMaintenanceChange(@PathVariable("id") @NotBlank @Size(max = 36) String id,
                                                     @RequestParam("changeType") @NotBlank String changeType,
                                                     @RequestParam("fieldName") @NotBlank String fieldName,
                                                     @RequestParam("oldValue") String oldValue,
                                                     @RequestParam("newValue") String newValue,
                                                     @RequestParam("createdBy") @NotBlank String createdBy) {
        try {
            CompletableFuture<String> future = maintenanceApplicationService.addMaintenanceChange(
                    id, changeType, fieldName, oldValue, newValue, createdBy);
            future.get();
            return ResponseEntity.ok(id);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to add maintenance change: {}", e.getMessage(), e);
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
                                                           @RequestParam("updatedBy") @NotBlank String updatedBy) {
        try {
            CompletableFuture<String> future = maintenanceApplicationService.calculateMaintenancePremium(
                    id, totalAmount, refundAmount, calculationDetails, updatedBy);
            future.get();
            return ResponseEntity.ok(id);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to calculate maintenance premium: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // 执行保全
    @PostMapping("/{id}/execute")
    public ResponseEntity<String> executeMaintenance(@PathVariable("id") @NotBlank @Size(max = 36) String id,
                                                  @RequestParam("effectiveTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime effectiveTime,
                                                  @RequestParam("executionDetails") String executionDetails,
                                                  @RequestParam("updatedBy") @NotBlank String updatedBy) {
        try {
            CompletableFuture<String> future = maintenanceApplicationService.executeMaintenance(
                    id, effectiveTime, executionDetails, updatedBy);
            future.get();
            return ResponseEntity.ok(id);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to execute maintenance: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // 将领域模型转换为API响应DTO
    private MaintenanceResponse convertToResponse(com.titanium.maintenance.aggregate.Maintenance maintenance) {
        return MaintenanceResponse.builder()
                .id(maintenance.getId().getId())
                .policyId(maintenance.getPolicyId().getId())
                .customerId(maintenance.getCustomerId().getId())
                .maintenanceType(maintenance.getMaintenanceType())
                .totalAmount(maintenance.getTotalAmount())
                .refundAmount(maintenance.getRefundAmount())
                .effectiveTimeType(maintenance.getEffectiveTimeType())
                .specificEffectiveDate(maintenance.getSpecificEffectiveDate())
                .description(maintenance.getDescription())
                .status(maintenance.getStatus())
                .createdAt(maintenance.getCreateTime())
                .createdBy(maintenance.getCreatedBy())
                .updatedAt(maintenance.getUpdateTime())
                .updatedBy(maintenance.getUpdatedBy())
                .tenantId(maintenance.getTenantId())
                .build();
    }
}
