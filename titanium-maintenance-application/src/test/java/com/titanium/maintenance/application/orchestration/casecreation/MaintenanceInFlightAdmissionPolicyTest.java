package com.titanium.maintenance.application.orchestration.casecreation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.exception.MaintenanceTypeExcludedException;
import com.titanium.maintenance.common.exception.PendingMaintenanceExistsException;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.repository.MaintenanceExclusionRepository;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

/**
 * 建案在途冲突门面测试（g04-07）——「同一保单能否再建新案」的准入判定。
 *
 * <p>
 * 两条建案路径共用本门面，故本测试即两条路径的<b>共同</b>准入契约；路径各自是否真的调用了本门面，
 * 由各自的用例与结构守卫另行断言。
 * </p>
 *
 * @author wei.sun
 * @since 2026/9/29
 */
@ExtendWith(MockitoExtension.class)
class MaintenanceInFlightAdmissionPolicyTest {

    @Mock private MaintenanceViewRepository maintenanceViewRepository;
    @Mock private MaintenanceExclusionRepository maintenanceExclusionRepository;

    @InjectMocks private MaintenanceInFlightAdmissionPolicy policy;

    @Test
    @DisplayName("保单无在途案件时可建案")
    void shouldAdmitWhenNoInFlightCase() {
        when(maintenanceViewRepository.findByPolicyIdAndTenantIdAndStatusIn(
                eq("policy-1"), eq("tenant-1"), any())).thenReturn(List.of());

        assertDoesNotThrow(() -> policy.requireNoConflict(
                "policy-1", "tenant-1", "req-1", MaintenanceChannel.API,
                List.of(MaintenanceType.POLICY_INFO_CHANGE)));

        verify(maintenanceExclusionRepository, never()).isMaintenanceTypeExcluded(any(), any(), any());
    }

    /**
     * 🔴 <b>本用例是本次修复的核心判据</b>：互斥判定必须<b>可达</b>。
     *
     * <p>
     * 旧版入口原顺序为「先判在途、后判互斥」，前者一发现任何在途案件即抛出，后者遍历的列表已被证明为空
     * ⇒ {@code maintenance_exclusion} 配置<b>自建立起从未决定过任何一次拒绝</b>。本门面将两者次序颠倒
     * （先互斥、后在途），使互斥分支重新可达——若有人把次序改回去，本用例立即转红。
     * </p>
     */
    @Test
    @DisplayName("在途案件的类型与本请求互斥时，必须给出互斥错误码（证明互斥判定可达）")
    void shouldRejectWithExclusionCodeWhenMutuallyExcluded() {
        when(maintenanceViewRepository.findByPolicyIdAndTenantIdAndStatusIn(
                eq("policy-1"), eq("tenant-1"), any())).thenReturn(List.of(inFlightCase(
                        MaintenanceType.POLICY_TERMINATION, null, MaintenanceChannel.MANUAL, true)));
        when(maintenanceExclusionRepository.isMaintenanceTypeExcluded(
                MaintenanceType.POLICY_TERMINATION, MaintenanceType.POLICY_INFO_CHANGE, "tenant-1"))
                .thenReturn(true);

        assertThrows(MaintenanceTypeExcludedException.class, () -> policy.requireNoConflict(
                "policy-1", "tenant-1", "req-1", MaintenanceChannel.API,
                List.of(MaintenanceType.POLICY_INFO_CHANGE)));
    }

    @Test
    @DisplayName("在途案件与本请求不互斥时，给出『存在在途案件』错误码（对照组）")
    void shouldRejectWithPendingCodeWhenNotExcluded() {
        when(maintenanceViewRepository.findByPolicyIdAndTenantIdAndStatusIn(
                eq("policy-1"), eq("tenant-1"), any())).thenReturn(List.of(inFlightCase(
                        MaintenanceType.POLICY_INFO_CHANGE, null, MaintenanceChannel.MANUAL, true)));
        when(maintenanceExclusionRepository.isMaintenanceTypeExcluded(any(), any(), any())).thenReturn(false);

        assertThrows(PendingMaintenanceExistsException.class, () -> policy.requireNoConflict(
                "policy-1", "tenant-1", "req-1", MaintenanceChannel.API,
                List.of(MaintenanceType.BENEFICIARY_CHANGE)));
    }

    @Test
    @DisplayName("多保全项：任一项命中互斥即拒绝（逐项比对，非只看首项）")
    void shouldCompareEveryRequestedType() {
        when(maintenanceViewRepository.findByPolicyIdAndTenantIdAndStatusIn(
                eq("policy-1"), eq("tenant-1"), any())).thenReturn(List.of(inFlightCase(
                        MaintenanceType.POLICY_TERMINATION, null, MaintenanceChannel.MANUAL, true)));
        when(maintenanceExclusionRepository.isMaintenanceTypeExcluded(any(), any(), any())).thenReturn(false);
        when(maintenanceExclusionRepository.isMaintenanceTypeExcluded(
                any(), eq(MaintenanceType.TOP_UP), eq("tenant-1"))).thenReturn(true);

        assertThrows(MaintenanceTypeExcludedException.class, () -> policy.requireNoConflict(
                "policy-1", "tenant-1", "req-1", MaintenanceChannel.API,
                List.of(MaintenanceType.POLICY_INFO_CHANGE, MaintenanceType.TOP_UP)));
    }

    /**
     * 幂等重试不得被自己拦死：独立建案以 {@code clientRequestKey} 幂等，重试时首次已建成的案件正是在途案件。
     */
    /**
     * 🔴 <b>本用例锁定真机 T2.2 暴露的缺陷</b>：幂等重放必须<b>短路放行</b>，而不是「仅把自身排除出冲突列表」。
     *
     * <p>
     * 两种写法在<b>单案保单</b>上表现完全相同（上一用例即此情形，故它单独存在时无法发现缺陷）；差别只在
     * <b>多案保单</b>上出现——只排除自身则列表非空，重放被 {@code 71001003} 拦死，客户端超时重试拿不到原案件号。
     * 真机实测：探针保单上有 12 件在途案件时，精确重放（同键 + 同渠道 + 同载荷）被拒。
     * </p>
     */
    @Test
    @DisplayName("🔴 幂等重放短路：保单另有其它在途案件时，重放本请求仍须放行")
    void shouldAdmitIdempotentReplayEvenWhenOtherCasesAreInFlight() {
        when(maintenanceViewRepository.findByPolicyIdAndTenantIdAndStatusIn(
                eq("policy-1"), eq("tenant-1"), any())).thenReturn(List.of(
                        inFlightCase(MaintenanceType.POLICY_INFO_CHANGE, "req-1", MaintenanceChannel.API, true),
                        inFlightCase(MaintenanceType.ADDITIONAL_PAYMENT, "other-key", MaintenanceChannel.API, true)));

        assertDoesNotThrow(() -> policy.requireNoConflict(
                "policy-1", "tenant-1", "req-1", MaintenanceChannel.API,
                List.of(MaintenanceType.ADDITIONAL_PAYMENT)));

        // 短路必须发生在互斥判定之前：若走到互斥循环，说明短路失效（此时会抛 PendingMaintenanceExists）
        verify(maintenanceExclusionRepository, never()).isMaintenanceTypeExcluded(any(), any(), any());
    }

    @Test
    @DisplayName("同键同渠道仅当为唯一在途案件时也放行（单案保单对照，防短路被写成「从不短路」）")
    void shouldExcludeTheRequestOwnCaseAsSoleInFlight() {
        when(maintenanceViewRepository.findByPolicyIdAndTenantIdAndStatusIn(
                eq("policy-1"), eq("tenant-1"), any())).thenReturn(List.of(inFlightCase(
                        MaintenanceType.POLICY_INFO_CHANGE, "req-1", MaintenanceChannel.API, true)));

        assertDoesNotThrow(() -> policy.requireNoConflict(
                "policy-1", "tenant-1", "req-1", MaintenanceChannel.API,
                List.of(MaintenanceType.POLICY_INFO_CHANGE)));

        verify(maintenanceExclusionRepository, never()).isMaintenanceTypeExcluded(any(), any(), any());
    }

    @Test
    @DisplayName("幂等键相同但渠道不同仍算冲突（键+渠道共同识别『本请求』，防跨渠道误放行）")
    void shouldNotExcludeCaseFromAnotherChannel() {
        when(maintenanceViewRepository.findByPolicyIdAndTenantIdAndStatusIn(
                eq("policy-1"), eq("tenant-1"), any())).thenReturn(List.of(inFlightCase(
                        MaintenanceType.POLICY_INFO_CHANGE, "req-1", MaintenanceChannel.MANUAL, true)));
        when(maintenanceExclusionRepository.isMaintenanceTypeExcluded(any(), any(), any())).thenReturn(false);

        assertThrows(PendingMaintenanceExistsException.class, () -> policy.requireNoConflict(
                "policy-1", "tenant-1", "req-1", MaintenanceChannel.API,
                List.of(MaintenanceType.POLICY_INFO_CHANGE)));
    }

    @Test
    @DisplayName("旧版入口无幂等键（null）时不得排除任何案件")
    void shouldNotExcludeAnythingWithoutClientRequestKey() {
        when(maintenanceViewRepository.findByPolicyIdAndTenantIdAndStatusIn(
                eq("policy-1"), eq("tenant-1"), any())).thenReturn(List.of(inFlightCase(
                        MaintenanceType.POLICY_INFO_CHANGE, "req-1", MaintenanceChannel.MANUAL, true)));
        when(maintenanceExclusionRepository.isMaintenanceTypeExcluded(any(), any(), any())).thenReturn(false);

        assertThrows(PendingMaintenanceExistsException.class, () -> policy.requireNoConflict(
                "policy-1", "tenant-1", null, null, List.of(MaintenanceType.POLICY_INFO_CHANGE)));
    }

    @Test
    @DisplayName("建案未初始化的独立案件对操作者不可见，不参与冲突判定")
    void shouldIgnoreOperatorInvisibleCase() {
        when(maintenanceViewRepository.findByPolicyIdAndTenantIdAndStatusIn(
                eq("policy-1"), eq("tenant-1"), any())).thenReturn(List.of(inFlightCase(
                        MaintenanceType.POLICY_INFO_CHANGE, null, MaintenanceChannel.MANUAL, false)));

        assertDoesNotThrow(() -> policy.requireNoConflict(
                "policy-1", "tenant-1", "req-1", MaintenanceChannel.API,
                List.of(MaintenanceType.POLICY_INFO_CHANGE)));
    }

    @Test
    @DisplayName("在途状态口径取自 MaintenanceStatus.inFlightStatuses()，且不含终态")
    void shouldQueryWithSharedInFlightStatuses() {
        when(maintenanceViewRepository.findByPolicyIdAndTenantIdAndStatusIn(
                eq("policy-1"), eq("tenant-1"), any())).thenReturn(List.of());

        policy.requireNoConflict("policy-1", "tenant-1", "req-1", MaintenanceChannel.API,
                List.of(MaintenanceType.POLICY_INFO_CHANGE));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MaintenanceStatus>> statuses = ArgumentCaptor.forClass(List.class);
        verify(maintenanceViewRepository).findByPolicyIdAndTenantIdAndStatusIn(
                eq("policy-1"), eq("tenant-1"), statuses.capture());
        // 与举目共享同一常量对象：口径只有一处，改一处即全改
        assertEquals(MaintenanceStatus.inFlightStatuses(), statuses.getValue());
        assertEquals(List.of(MaintenanceStatus.PENDING, MaintenanceStatus.PROCESSING, MaintenanceStatus.APPROVED),
                statuses.getValue());
        assertEquals(0, statuses.getValue().stream()
                .filter(status -> status == MaintenanceStatus.COMPLETED || status == MaintenanceStatus.REJECTED)
                .count(), "终态不得出现在在途口径内");
    }

    private MaintenanceView inFlightCase(
            MaintenanceType type, String clientRequestKey, MaintenanceChannel source, boolean initializationCompleted) {
        MaintenanceView view = new MaintenanceView();
        view.setMaintenanceType(type);
        view.setStatus(MaintenanceStatus.PENDING);
        view.setClientRequestKey(clientRequestKey);
        view.setSource(source);
        view.setIndependentCase(true);
        view.setInitializationCompleted(initializationCompleted);
        return view;
    }
}
