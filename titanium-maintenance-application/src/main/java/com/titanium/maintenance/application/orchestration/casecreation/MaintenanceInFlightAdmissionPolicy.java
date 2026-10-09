package com.titanium.maintenance.application.orchestration.casecreation;

import java.util.List;

import org.springframework.stereotype.Service;

import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.exception.MaintenanceTypeExcludedException;
import com.titanium.maintenance.common.exception.PendingMaintenanceExistsException;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.repository.MaintenanceExclusionRepository;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

import lombok.RequiredArgsConstructor;

/**
 * 建案在途冲突准入门面——「同一保单能否再建新案」的<b>单一权威</b>，两条建案路径共用。
 *
 * <p>
 * <b>为何抽出本类</b>：在途判定与互斥判定原先<b>只存在于旧版建案入口</b>
 * （{@code MaintenanceApplicationService}），独立建案入口
 * （{@link MaintenanceCaseCreationApplicationService}）二者皆无——同一保单因此可并存多案
 * （真机实测：单张保单上同时存在 31 件在途探针案件），互斥配置对独立路径完全不生效。
 * 收敛到本门面后，两条路径共享同一准入事实。
 * </p>
 *
 * <p>
 * 🔴 <b>判定顺序是判据可达性的前提</b>：本类<b>先判互斥、后判「存在在途案件」</b>。旧版入口原顺序相反
 * （{@code checkPendingMaintenance} 在前、{@code checkMaintenanceExclusion} 在后），前者一旦发现任何在途案件
 * 即抛出，后者遍历的是<b>已被证明为空</b>的同一列表 ⇒ 互斥判定<b>自建立起从未决定过任何一次拒绝</b>
 * （{@code maintenance_exclusion} 配置成为不可达判据，且全仓无测试覆盖）。反序后两个分支皆可达：
 * 命中互斥配置时给出更具体的 {@code 71000009}，否则给出 {@code 71001003}。
 * </p>
 *
 * <p>
 * 🔴 <b>幂等重放短路</b>：{@code clientRequestKey} 命中本请求自己已建成的在途案件时<b>整段放行</b>
 * （不参与冲突判定，也不因「保单另有其它在途案件」被拦）。是否真的复用既有案件由命令层的幂等裁决给出
 * （同键不同指纹 → {@code MaintenanceConflictException}）。把短路简化成「仅把自身排除出冲突列表」会在
 * 多案保单上错误拒绝幂等重试——真机探针 T2.2 已实证。
 * </p>
 *
 * <p>
 * ⚠️ <b>已知竞态</b>：本门面是<b>前置预检</b>，与并发建案之间存在检查-使用窗口（两条路径同此）。真正的一致性
 * 由合同侧「同保单并发批改」的领域约束承担；本门面把窗口从「无检查」收窄到「近似瞬时的预检」。
 * </p>
 *
 * @author wei.sun
 * @since 2026/9/29
 */
@Service
@RequiredArgsConstructor
public class MaintenanceInFlightAdmissionPolicy {

    private final MaintenanceViewRepository maintenanceViewRepository;
    private final MaintenanceExclusionRepository maintenanceExclusionRepository;

    /**
     * 校验本建案请求与保单在途案件无冲突。
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @param clientRequestKey 本请求幂等键；独立建案必填，旧版入口无幂等键时传 {@code null}
     * @param source 本请求受理渠道；与 {@code clientRequestKey} 一同用于识别「本请求自己已建成的案件」
     * @param requestedTypes 本请求拟办理的保全类型（独立建案可多项，逐项比对互斥）
     * @throws MaintenanceTypeExcludedException 拟办理项与某在途案件的主类型互斥
     * @throws PendingMaintenanceExistsException 保单存在未终结的在途案件
     */
    public void requireNoConflict(String policyId, String tenantId, String clientRequestKey,
                                  MaintenanceChannel source, List<MaintenanceType> requestedTypes) {
        List<MaintenanceView> cases = inFlightCases(policyId, tenantId);
        // 🔴 幂等重放短路（不可省，也**不可退化为「仅把自身排除出冲突列表」**）：
        //    本请求自己已建成的案件已在途 ⇒ 本请求不会新建任何案件，是否放行应由命令层的幂等裁决
        //    （聚合 assertSameCreationRequest：同键不同指纹 → MaintenanceConflictException）决定，
        //    而不是被「保单另有其它在途案件」连带拦死。仅排除自身、不短路的写法在**单案保单**上恰好
        //    表现正确，在多案保单上则把幂等重试变成 71001003，使客户端的超时重试拿不到原案件号。
        //    （真机探针 T2.2 实证：同一张保单上有 12 件在途案件时，精确重放被 71001003 拦死。）
        if (clientRequestKey != null
                && cases.stream().anyMatch(view -> isSameRequest(view, clientRequestKey, source))) {
            return;
        }
        List<MaintenanceView> inFlight = cases.stream()
                .filter(view -> !isSameRequest(view, clientRequestKey, source))
                .toList();
        if (inFlight.isEmpty()) {
            return;
        }
        for (MaintenanceView view : inFlight) {
            for (MaintenanceType requested : requestedTypes) {
                if (maintenanceExclusionRepository.isMaintenanceTypeExcluded(
                        view.getMaintenanceType(), requested, tenantId)) {
                    throw new MaintenanceTypeExcludedException();
                }
            }
        }
        throw new PendingMaintenanceExistsException();
    }

    /**
     * 查询该保单的在途案件（读模型，最终一致）。
     *
     * <p>
     * {@code isOperatorVisible} 过滤与查询侧列表可见性同口径：不可见案件不参与准入判定，
     * 否则用户会收到一个在界面上看不到、也无从处理的「存在在途案件」拒绝。
     * </p>
     */
    private List<MaintenanceView> inFlightCases(String policyId, String tenantId) {
        return maintenanceViewRepository
                .findByPolicyIdAndTenantIdAndStatusIn(policyId, tenantId, MaintenanceStatus.inFlightStatuses()).stream()
                .filter(MaintenanceView::isOperatorVisible)
                .toList();
    }

    /**
     * 是否为「本请求自己已建成的案件」。
     *
     * <p>
     * 🔴 必须排除：独立建案以 {@code clientRequestKey} 幂等，重试（含并发创建恢复）时首次已建成的案件
     * 正是在途案件——不排除会让首次重试即被自己拦死，幂等语义失效。
     * </p>
     *
     * <p>
     * 判定取「键 + 渠道」两个维度：同键换渠道（如后台人工改派 API 建的单）不算本请求自己，
     * 仍参与冲突判定——否则换个入口重放同一键即可绕过在途闸口。
     * </p>
     */
    private boolean isSameRequest(MaintenanceView view, String clientRequestKey, MaintenanceChannel source) {
        return clientRequestKey != null
                && clientRequestKey.equals(view.getClientRequestKey())
                && source == view.getSource();
    }
}
