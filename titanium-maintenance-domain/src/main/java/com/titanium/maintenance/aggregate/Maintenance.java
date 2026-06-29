package com.titanium.maintenance.aggregate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

import com.titanium.maintenance.command.AddMaintenanceChangeCommand;
import com.titanium.maintenance.command.CalculateMaintenancePremiumCommand;
import com.titanium.maintenance.command.ChangeMaintenanceStatusCommand;
import com.titanium.maintenance.command.CreateMaintenanceCommand;
import com.titanium.maintenance.command.ExecuteMaintenanceCommand;
import com.titanium.maintenance.enums.EffectiveTimeType;
import com.titanium.maintenance.enums.MaintenanceStatus;
import com.titanium.maintenance.enums.MaintenanceType;
import com.titanium.maintenance.event.MaintenanceChangeAddedEvent;
import com.titanium.maintenance.event.MaintenanceCreatedEvent;
import com.titanium.maintenance.event.MaintenanceExecutedEvent;
import com.titanium.maintenance.event.MaintenancePremiumCalculatedEvent;
import com.titanium.maintenance.event.MaintenanceStatusChangedEvent;
import com.titanium.maintenance.exception.MaintenanceStatusException;
import com.titanium.maintenance.valueobject.CustomerId;
import com.titanium.maintenance.valueobject.MaintenanceChange;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.PolicyId;

import lombok.NoArgsConstructor;

@Aggregate
@NoArgsConstructor
public class Maintenance {
    @AggregateIdentifier
    private MaintenanceId           id;
    private PolicyId                policyId;
    private CustomerId              customerId;
    private MaintenanceType         maintenanceType;
    private MaintenanceStatus       status;
    private EffectiveTimeType       effectiveTimeType;
    private LocalDateTime           specificEffectiveDate;
    private BigDecimal              totalAmount;
    private BigDecimal              refundAmount;
    private String                  description;
    private List<MaintenanceChange> changes;
    private LocalDateTime           createdAt;
    private String                  createdBy;
    private LocalDateTime           updatedAt;
    private String                  updatedBy;
    private String                  tenantId;

    // 创建保全记录命令处理器
    @CommandHandler
    public Maintenance(CreateMaintenanceCommand command) {
        AggregateLifecycle.apply(new MaintenanceCreatedEvent(command.getId(), command.getPolicyId(),
                command.getCustomerId(), command.getMaintenanceType(), command.getEffectiveTimeType(),
                command.getSpecificEffectiveDate(), command.getDescription(), LocalDateTime.now(),
                command.getCreatedBy(), command.getTenantId()));
    }

    // 处理创建事件
    @EventSourcingHandler
    public void on(MaintenanceCreatedEvent event) {
        this.id = event.maintenanceId();
        this.policyId = event.policyId();
        this.customerId = event.customerId();
        this.maintenanceType = event.maintenanceType();
        this.status = MaintenanceStatus.PENDING;
        this.effectiveTimeType = event.effectiveTimeType();
        this.specificEffectiveDate = event.specificEffectiveDate();
        this.totalAmount = BigDecimal.ZERO;
        this.refundAmount = BigDecimal.ZERO;
        this.description = event.description();
        this.changes = new ArrayList<>();
        this.createdAt = event.createdAt();
        this.createdBy = event.createdBy();
        this.updatedAt = event.createdAt();
        this.updatedBy = event.createdBy();
        this.tenantId = event.tenantId();
    }

    // 处理变更状态命令
    @CommandHandler
    public void handle(ChangeMaintenanceStatusCommand command) {
        if (this.status == MaintenanceStatus.COMPLETED || this.status == MaintenanceStatus.REJECTED) {
            throw new MaintenanceStatusException(this.id.getId(), this.status.name(), command.getNewStatus().name(),
                    "已完成或已拒绝的保全不允许变更状态");
        }
        if (this.status == command.getNewStatus()) {
            throw new MaintenanceStatusException(this.id.getId(), this.status.name(), command.getNewStatus().name(),
                    "保全已处于该状态");
        }

        AggregateLifecycle.apply(new MaintenanceStatusChangedEvent(command.getId(), this.status, command.getNewStatus(),
                command.getChangeReason(), LocalDateTime.now(), command.getChangedBy(), this.tenantId));
    }

    // 处理添加变更记录命令
    @CommandHandler
    public void handle(AddMaintenanceChangeCommand command) {
        AggregateLifecycle.apply(new MaintenanceChangeAddedEvent(command.getId(), command.getChangeType(),
                command.getFieldName(), command.getOldValue(), command.getNewValue(), LocalDateTime.now(),
                command.getCreatedBy(), this.tenantId));
    }

    // 处理计算保费命令
    @CommandHandler
    public void handle(CalculateMaintenancePremiumCommand command) {
        AggregateLifecycle.apply(new MaintenancePremiumCalculatedEvent(command.getId(), command.getTotalAmount(),
                command.getRefundAmount(), command.getCalculationDetails(), LocalDateTime.now(), command.getUpdatedBy(),
                this.tenantId));
    }

    // 处理执行保全命令
    @CommandHandler
    public void handle(ExecuteMaintenanceCommand command) {
        // enrich policyId 与 maintenanceType 作为跨域上下文，供 policy 域监听回写保单状态
        AggregateLifecycle.apply(new MaintenanceExecutedEvent(command.getId(), this.policyId.getId(),
                this.maintenanceType, command.getEffectiveTime(), command.getExecutionDetails(), LocalDateTime.now(),
                command.getUpdatedBy(), this.tenantId));
    }

    // 处理状态变更事件
    @EventSourcingHandler
    public void on(MaintenanceStatusChangedEvent event) {
        this.status = event.newStatus();
        this.updatedAt = event.changedAt();
        this.updatedBy = event.changedBy();
    }

    // 处理变更记录添加事件
    @EventSourcingHandler
    public void on(MaintenanceChangeAddedEvent event) {
        this.changes.add(new MaintenanceChange(event.changeType(), event.fieldName(), event.oldValue(),
                event.newValue(), event.createdAt()));
        this.updatedAt = event.createdAt();
        this.updatedBy = event.createdBy();
    }

    // 处理保费计算事件
    @EventSourcingHandler
    public void on(MaintenancePremiumCalculatedEvent event) {
        this.totalAmount = event.totalAmount();
        this.refundAmount = event.refundAmount();
        this.updatedAt = event.updatedAt();
        this.updatedBy = event.updatedBy();
    }

    // 处理执行保全事件
    @EventSourcingHandler
    public void on(MaintenanceExecutedEvent event) {
        this.status = MaintenanceStatus.COMPLETED;
        this.updatedAt = event.updatedAt();
        this.updatedBy = event.updatedBy();
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
