package com.titanium.maintenance.query.handler.projection;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.maintenance.event.MaintenanceSurrenderValueRecordedEvent;
import com.titanium.maintenance.port.product.ProductSurrenderValuePort.SurrenderFactorFact;
import com.titanium.maintenance.query.repository.MaintenanceSurrenderFactorViewRepository;
import com.titanium.maintenance.query.view.MaintenanceSurrenderFactorView;

import lombok.RequiredArgsConstructor;

/**
 * 退费因子明细投影：Product 退费明细（防腐镜像）→ {@code t_maintenance_surrender_factor_view}。
 *
 * <p>
 * 🔴 <b>只做「事件 → 读模型」写入</b>：不发命令、不持有 {@code CommandGateway}（项目 CLAUDE.md §3.4.9 ⑤）；
 * 明细的权威在 Product 域，本投影只冻结事实，不做金额校验、重算或状态推进。
 * </p>
 * <p>
 * 🔴 <b>重投影幂等</b>：按 {@code (tenantId, maintenanceId)} 先删后插（与案件字段差异投影同范式），
 * 因子行主键由「案件 + 序号 + 因子类型」稳定派生 ⇒ 重放不产生重复行。
 * </p>
 * <p>
 * 🔴 <b>历史事件兼容</b>：早于 {@code factors} 分量写入的事件反序列化后为 {@code null}，
 * 与空列表同等看待（都表示「该次记录无明细」）——不得抛异常，否则存量案件的投影重建整批失败。
 * </p>
 */
@Component
@ProcessingGroup("maintenance-query-group")
@RequiredArgsConstructor
public class MaintenanceSurrenderFactorProjectionEventHandler {

    private final MaintenanceSurrenderFactorViewRepository surrenderFactorViewRepository;

    /** 投影 Product 退费因子明细（null 与空列表同义：清空该案件的明细行）。 */
    @EventHandler
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(MaintenanceSurrenderValueRecordedEvent event) {
        String maintenanceId = event.maintenanceId().id();
        List<SurrenderFactorFact> factors = event.factors() == null ? List.of() : event.factors();
        surrenderFactorViewRepository.deleteByTenantIdAndMaintenanceId(event.tenantId(), maintenanceId);
        List<MaintenanceSurrenderFactorView> views = new ArrayList<>(factors.size());
        for (int order = 0; order < factors.size(); order++) {
            views.add(toView(event, maintenanceId, factors.get(order), order));
        }
        surrenderFactorViewRepository.saveAll(views);
    }

    private MaintenanceSurrenderFactorView toView(
            MaintenanceSurrenderValueRecordedEvent event,
            String maintenanceId,
            SurrenderFactorFact factor,
            int order) {
        String tenantId = event.tenantId();
        MaintenanceSurrenderFactorView view = new MaintenanceSurrenderFactorView();
        view.setFactorViewId(factorViewId(tenantId, maintenanceId, order, factor.factorType()));
        view.setMaintenanceId(maintenanceId);
        view.setFactorOrder(order);
        view.setFactorType(factor.factorType());
        view.setDirection(factor.direction());
        view.setAmount(factor.amount());
        view.setExplainKey(factor.explainKey());
        view.setTenantId(tenantId);
        view.setCreateTime(event.recordedAt());
        view.setUpdateTime(event.recordedAt());
        return view;
    }

    private String factorViewId(String tenantId, String maintenanceId, int order, String factorType) {
        String canonical = String.join("\u001f",
                tenantId, maintenanceId, Integer.toString(order), String.valueOf(factorType));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256 实现", exception);
        }
    }
}
