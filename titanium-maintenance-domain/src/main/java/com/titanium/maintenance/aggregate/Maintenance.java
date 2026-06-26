package com.titanium.maintenance.aggregate;

import com.titanium.maintenance.command.*;
import com.titanium.maintenance.enums.EffectiveTimeType;
import com.titanium.maintenance.enums.MaintenanceStatus;
import com.titanium.maintenance.enums.MaintenanceType;
import com.titanium.maintenance.event.*;
import com.titanium.maintenance.exception.MaintenanceStatusException;
import com.titanium.maintenance.valueobject.*;
import lombok.NoArgsConstructor;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Aggregate
@NoArgsConstructor
public class Maintenance {
    @AggregateIdentifier
    private MaintenanceId id;
    private PolicyId policyId;
    private CustomerId customerId;
    private MaintenanceType maintenanceType;
    private MaintenanceStatus status;
    private EffectiveTimeType effectiveTimeType;
    private LocalDateTime specificEffectiveDate;
    private BigDecimal totalAmount;
    private BigDecimal refundAmount;
    private String description;
    private List<MaintenanceChange> changes;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private String tenantId;

    // 创建保全记录命令处理器
    @CommandHandler
    public Maintenance(CreateMaintenanceCommand command) {
        AggregateLifecycle.apply(new MaintenanceCreatedEvent(
                command.getId(),
                command.getPolicyId(),
                command.getCustomerId(),
                command.getMaintenanceType(),
                command.getEffectiveTimeType(),
                command.getSpecificEffectiveDate(),
                command.getDescription(),
                LocalDateTime.now(),
                command.getCreatedBy(),
                command.getTenantId()
        ));
    }

    // 处理创建事件
    @EventSourcingHandler
    public void on(MaintenanceCreatedEvent event) {
        this.id = event.getMaintenanceId();
        this.policyId = event.getPolicyId();
        this.customerId = event.getCustomerId();
        this.maintenanceType = event.getMaintenanceType();
        this.status = MaintenanceStatus.PENDING;
        this.effectiveTimeType = event.getEffectiveTimeType();
        this.specificEffectiveDate = event.getSpecificEffectiveDate();
        this.totalAmount = BigDecimal.ZERO;
        this.refundAmount = BigDecimal.ZERO;
        this.description = event.getDescription();
        this.changes = new ArrayList<>();
        this.createdAt = event.getCreatedAt();
        this.createdBy = event.getCreatedBy();
        this.updatedAt = event.getCreatedAt();
        this.updatedBy = event.getCreatedBy();
        this.tenantId = event.getTenantId();
    }

    // 处理变更状态命令
    @CommandHandler
    public void handle(ChangeMaintenanceStatusCommand command) {
        if (this.status == MaintenanceStatus.COMPLETED || this.status == MaintenanceStatus.REJECTED) {
            throw new MaintenanceStatusException(this.id.getId(), this.status.name(),
                    command.getNewStatus().name(), "已完成或已拒绝的保全不允许变更状态");
        }
        if (this.status == command.getNewStatus()) {
            throw new MaintenanceStatusException(this.id.getId(), this.status.name(),
                    command.getNewStatus().name(), "保全已处于该状态");
        }

        AggregateLifecycle.apply(new MaintenanceStatusChangedEvent(
                command.getId(),
                this.status,
                command.getNewStatus(),
                command.getChangeReason(),
                LocalDateTime.now(),
                command.getChangedBy(),
                this.tenantId
        ));
    }

    // 处理添加变更记录命令
    @CommandHandler
    public void handle(AddMaintenanceChangeCommand command) {
        AggregateLifecycle.apply(new MaintenanceChangeAddedEvent(
                command.getId(),
                command.getChangeType(),
                command.getFieldName(),
                command.getOldValue(),
                command.getNewValue(),
                LocalDateTime.now(),
                command.getCreatedBy(),
                this.tenantId
        ));
    }

    // 处理计算保费命令
    @CommandHandler
    public void handle(CalculateMaintenancePremiumCommand command) {
        AggregateLifecycle.apply(new MaintenancePremiumCalculatedEvent(
                command.getId(),
                command.getTotalAmount(),
                command.getRefundAmount(),
                command.getCalculationDetails(),
                LocalDateTime.now(),
                command.getUpdatedBy(),
                this.tenantId
        ));
    }

    // 处理执行保全命令
    @CommandHandler
    public void handle(ExecuteMaintenanceCommand command) {
        AggregateLifecycle.apply(new MaintenanceExecutedEvent(
                command.getId(),
                command.getEffectiveTime(),
                command.getExecutionDetails(),
                LocalDateTime.now(),
                command.getUpdatedBy(),
                this.tenantId
        ));
    }

    // 处理状态变更事件
    @EventSourcingHandler
    public void on(MaintenanceStatusChangedEvent event) {
        this.status = event.getNewStatus();
        this.updatedAt = event.getChangedAt();
        this.updatedBy = event.getChangedBy();
    }

    // 处理变更记录添加事件
    @EventSourcingHandler
    public void on(MaintenanceChangeAddedEvent event) {
        this.changes.add(new MaintenanceChange(
                event.getChangeType(),
                event.getFieldName(),
                event.getOldValue(),
                event.getNewValue(),
                event.getCreatedAt()
        ));
        this.updatedAt = event.getCreatedAt();
        this.updatedBy = event.getCreatedBy();
    }

    // 处理保费计算事件
    @EventSourcingHandler
    public void on(MaintenancePremiumCalculatedEvent event) {
        this.totalAmount = event.getTotalAmount();
        this.refundAmount = event.getRefundAmount();
        this.updatedAt = event.getUpdatedAt();
        this.updatedBy = event.getUpdatedBy();
    }

    // 处理执行保全事件
    @EventSourcingHandler
    public void on(MaintenanceExecutedEvent event) {
        this.status = MaintenanceStatus.COMPLETED;
        this.updatedAt = event.getUpdatedAt();
        this.updatedBy = event.getUpdatedBy();
    }

    // Getters (for query purposes)
    public MaintenanceId getId() {
        return id;
    }

    public PolicyId getPolicyId() {
        return policyId;
    }

    public CustomerId getCustomerId() {
        return customerId;
    }

    public MaintenanceType getMaintenanceType() {
        return maintenanceType;
    }

    public MaintenanceStatus getStatus() {
        return status;
    }

    public EffectiveTimeType getEffectiveTimeType() {
        return effectiveTimeType;
    }

    public LocalDateTime getSpecificEffectiveDate() {
        return specificEffectiveDate;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public String getDescription() {
        return description;
    }

    public List<MaintenanceChange> getChanges() {
        return changes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public String getTenantId() {
        return tenantId;
    }
}