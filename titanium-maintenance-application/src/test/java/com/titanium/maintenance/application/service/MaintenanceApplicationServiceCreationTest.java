package com.titanium.maintenance.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.titanium.maintenance.application.orchestration.casecreation.MaintenanceInFlightAdmissionPolicy;
import com.titanium.maintenance.command.CreateMaintenanceCommand;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.exception.MaintenancePolicyStatusNotAdmittedException;
import com.titanium.maintenance.common.exception.PendingMaintenanceExistsException;
import com.titanium.maintenance.port.customer.CustomerServicePort;
import com.titanium.maintenance.port.maintenance.MaintenanceLegacyCreationFeaturePort;
import com.titanium.maintenance.port.maintenance.MaintenanceLegacyExecutionFeaturePort;
import com.titanium.maintenance.port.maintenance.MaintenanceLegacyPremiumCalculationFeaturePort;
import com.titanium.maintenance.port.policy.PolicyServicePort;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.metadata.enums.maintenance.MaintenanceType;
import com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;

/**
 * 旧版建案入口（{@code createMaintenanceCase}）的状态准入与在途闸口行为测试（g04-07）。
 *
 * <p>
 * 该入口此前<b>没有正向行为用例</b>——既有的 {@code MaintenanceApplicationServiceSearchTest} 只断言
 * 「开关关闭时在触达依赖之前即拒绝」。于是「准入矩阵是否真的接进了这条路径」只能靠源码肉眼确认，
 * 有人把 {@code MaintenanceAdmissionMatrix.require} 或 {@code requireNoConflict} 摘掉时不会有任何用例转红。
 * 本类补上两条闸口的<b>行为判据</b>，并锁定「判定次序」与「入参」两个易漂移点。
 * </p>
 *
 * @author wei.sun
 * @since 2026/9/29
 */
@ExtendWith(MockitoExtension.class)
class MaintenanceApplicationServiceCreationTest {

    private static final String POLICY_ID = "policy-1";
    private static final String CUSTOMER_ID = "customer-1";
    private static final String TENANT_ID = "tenant-1";

    @Mock private CommandGateway commandGateway;
    @Mock private MaintenanceViewRepository maintenanceViewRepository;
    @Mock private MaintenanceInFlightAdmissionPolicy inFlightAdmissionPolicy;
    @Mock private PolicyServicePort policyServicePort;
    @Mock private CustomerServicePort customerServicePort;
    @Mock private MaintenanceLegacyCreationFeaturePort legacyCreationFeaturePort;
    @Mock private MaintenanceLegacyPremiumCalculationFeaturePort legacyPremiumCalculationFeaturePort;
    @Mock private MaintenanceLegacyExecutionFeaturePort legacyExecutionFeaturePort;

    private MaintenanceApplicationService service;

    @BeforeEach
    void setUp() {
        service = new MaintenanceApplicationService(
                commandGateway, maintenanceViewRepository, inFlightAdmissionPolicy, policyServicePort,
                customerServicePort, legacyCreationFeaturePort, legacyPremiumCalculationFeaturePort,
                legacyExecutionFeaturePort);
        when(legacyCreationFeaturePort.isEnabled()).thenReturn(true);
        when(policyServicePort.policyExists(POLICY_ID, TENANT_ID)).thenReturn(true);
        when(customerServicePort.customerExists(CUSTOMER_ID)).thenReturn(true);
    }

    @Test
    @DisplayName("保单状态准入且在途无冲突时建案成功，并派发创建命令")
    void shouldCreateCaseWhenAdmittedAndNoConflict() {
        when(policyServicePort.getPolicyStatus(POLICY_ID))
                .thenReturn(new PolicyServicePort.PolicyStatusSnapshot(PolicyStatus.EFFECTIVE));
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));

        service.createMaintenanceCase(POLICY_ID, CUSTOMER_ID, MaintenanceType.POLICY_INFO_CHANGE,
                EffectiveTimeType.IMMEDIATE, null, "联系方式变更", "operator-1", TENANT_ID).join();

        verify(commandGateway).send(any(CreateMaintenanceCommand.class));
        verify(inFlightAdmissionPolicy).requireNoConflict(
                POLICY_ID, TENANT_ID, null, null, List.of(MaintenanceType.POLICY_INFO_CHANGE));
    }

    @Test
    @DisplayName("🔴 保单状态不准入时拒绝，且拒绝发生在派发命令与在途判定之前")
    void shouldRejectWhenStatusNotAdmittedBeforeDispatch() {
        // POLICY_INFO_CHANGE 属字段类，仅 EFFECTIVE 准入（矩阵取值）；SUSPENDED 必须被拒
        when(policyServicePort.getPolicyStatus(POLICY_ID))
                .thenReturn(new PolicyServicePort.PolicyStatusSnapshot(PolicyStatus.SUSPENDED));

        MaintenancePolicyStatusNotAdmittedException error = assertThrows(
                MaintenancePolicyStatusNotAdmittedException.class,
                () -> service.createMaintenanceCase(POLICY_ID, CUSTOMER_ID, MaintenanceType.POLICY_INFO_CHANGE,
                        EffectiveTimeType.IMMEDIATE, null, "联系方式变更", "operator-1", TENANT_ID));

        // 既有字段类沿用原对外契约错误码（不变）
        assertEquals(MaintenanceErrorCode.POLICY_NOT_ACTIVE, error.getErrorCode());
        verifyNoInteractions(commandGateway);
        // 状态不准入即在途判定之前终结：次序锁定，避免「先建在途查询再拒」的无效远程/库访问
        verify(inFlightAdmissionPolicy, never()).requireNoConflict(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("🔴 在途/互斥冲突时拒绝，且拒绝发生在派发命令之前")
    void shouldRejectWhenInFlightConflictBeforeDispatch() {
        when(policyServicePort.getPolicyStatus(POLICY_ID))
                .thenReturn(new PolicyServicePort.PolicyStatusSnapshot(PolicyStatus.EFFECTIVE));
        doThrow(new PendingMaintenanceExistsException())
                .when(inFlightAdmissionPolicy)
                .requireNoConflict(any(), any(), any(), any(), any());

        assertThrows(PendingMaintenanceExistsException.class,
                () -> service.createMaintenanceCase(POLICY_ID, CUSTOMER_ID, MaintenanceType.POLICY_INFO_CHANGE,
                        EffectiveTimeType.IMMEDIATE, null, "联系方式变更", "operator-1", TENANT_ID));

        verifyNoInteractions(commandGateway);
    }

    @Test
    @DisplayName("旧版入口无幂等键与渠道：必须向门面传 null/null，不得凭空豁免既有案件")
    void shouldPassNullIdempotencyKeyAndChannelToFacade() {
        when(policyServicePort.getPolicyStatus(POLICY_ID))
                .thenReturn(new PolicyServicePort.PolicyStatusSnapshot(PolicyStatus.EFFECTIVE));
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));

        service.createMaintenanceCase(POLICY_ID, CUSTOMER_ID, MaintenanceType.BENEFICIARY_CHANGE,
                EffectiveTimeType.IMMEDIATE, null, "受益人变更", "operator-1", TENANT_ID).join();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MaintenanceType>> types = ArgumentCaptor.forClass(List.class);
        // 幂等键/渠道传 null 且仅含本次请求的保全项
        verify(inFlightAdmissionPolicy).requireNoConflict(
                eq(POLICY_ID), eq(TENANT_ID), isNull(), isNull(), types.capture());
        // 幂等键/渠道为 null ⇒ 门面的「本请求自己」豁免不可达 ⇒ 在途案件一律参与冲突判定
        assertEquals(List.of(MaintenanceType.BENEFICIARY_CHANGE), types.getValue());
    }
}
