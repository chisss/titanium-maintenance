package com.titanium.maintenance.application.orchestration.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.titanium.maintenance.application.command.MaintenanceItemWithdrawalInput;
import com.titanium.maintenance.application.configuration.MaintenanceWithdrawalRecoveryProperties;
import com.titanium.maintenance.port.MaintenanceItemWithdrawalRecoveryLeasePort;
import com.titanium.maintenance.port.MaintenanceItemWithdrawalRecoveryLeasePort.WithdrawalRecoveryLease;

class MaintenanceItemWithdrawalRecoveryApplicationServiceTest {

    @Test
    void shouldRecoverWithFrozenOperationAndAlwaysReleaseLease() {
        MaintenanceItemWithdrawalApplicationService withdrawalService =
                mock(MaintenanceItemWithdrawalApplicationService.class);
        MaintenanceItemWithdrawalRecoveryLeasePort leasePort =
                mock(MaintenanceItemWithdrawalRecoveryLeasePort.class);
        MaintenanceWithdrawalRecoveryProperties properties = new MaintenanceWithdrawalRecoveryProperties();
        properties.setRetryDelay(Duration.ofMinutes(3));
        properties.setLeaseDuration(Duration.ofMinutes(1));
        properties.setBatchSize(10);
        properties.setMaxAttempts(4);
        WithdrawalRecoveryLease lease = new WithdrawalRecoveryLease(
                "item-view-1", "case-1", "tenant-1", "ITEM_A", "withdraw-operation-1",
                "客户取消项目", "BANK_CARD", 2);
        when(leasePort.acquireDue(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(lease));
        when(withdrawalService.withdraw(any())).thenReturn(CompletableFuture.completedFuture(null));
        MaintenanceItemWithdrawalRecoveryApplicationService service =
                new MaintenanceItemWithdrawalRecoveryApplicationService(withdrawalService, leasePort, properties);

        service.executeDue();

        ArgumentCaptor<MaintenanceItemWithdrawalInput> inputCaptor =
                ArgumentCaptor.forClass(MaintenanceItemWithdrawalInput.class);
        verify(withdrawalService).withdraw(inputCaptor.capture());
        assertEquals("withdraw-operation-1", inputCaptor.getValue().operationId());
        assertEquals("BANK_CARD", inputCaptor.getValue().paymentMethod());
        assertEquals("maintenance-withdrawal-recovery", inputCaptor.getValue().operatorId());
        ArgumentCaptor<String> ownerCaptor = ArgumentCaptor.forClass(String.class);
        verify(leasePort).acquireDue(
                ownerCaptor.capture(), any(), any(), any(), anyInt(), anyInt());
        verify(leasePort).release("item-view-1", ownerCaptor.getValue());
    }
}
