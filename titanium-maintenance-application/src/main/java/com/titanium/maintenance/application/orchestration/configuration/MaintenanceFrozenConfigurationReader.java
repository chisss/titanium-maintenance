package com.titanium.maintenance.application.orchestration.configuration;

import java.util.Objects;

import org.springframework.stereotype.Service;

import com.titanium.maintenance.common.exception.MaintenanceNotFoundException;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.configuration.MaintenanceItemConfiguration;
import com.titanium.maintenance.query.view.MaintenanceCaseItemView;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository;

import lombok.RequiredArgsConstructor;

/**
 * 案件冻结配置读取器：按「案件 + 保全项」取回建案时冻结的配置版本，并做三元一致性核对。
 * <p>
 * 抽出本类的动因（G09-02）：同一段读取在三处需要——工作流审核/核保/保费等步骤取配置，
 * 以及<b>受理环节的字段条件规则求值</b>（AC-04 需要按冻结配置里的 {@code fieldRules} 找到字段的
 * {@code conditionRuleCode}）。读取口径必须<b>唯一</b>：冻结配置的判定依据是
 * 「itemCode + configurationVersion + contentHash」三元组与读模型 item 视图逐项相等，
 * 任一处不一致即拒绝——若由各调用方各写一份，漏比一项就变成「读了另一个版本的配置」，
 * 而两个版本多数字段相同，错误会以「偶发结果不符」的形态出现。
 * </p>
 * <p>
 * 🔴 <b>失败关闭</b>：item 视图缺 configurationId、配置不存在、三元不一致，一律抛异常
 * （前两者 {@code MaintenanceNotFoundException}，后者 {@code MaintenanceValidationException}）。
 * 独立建案在初始化完成前即强制解析生效配置（{@code findEffective} 命中否则建案失败），
 * 故「独立 + 初始化完成」的案件必有 item 视图且 configurationId 非空——此处的失败关闭不产生新的
 * 失败类别，只是把必然的后续失败前移。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class MaintenanceFrozenConfigurationReader {

    private final MaintenanceItemConfigurationRepository configurationRepository;

    /**
     * 读取并核对案件冻结配置。
     *
     * @param caseView 案件读模型（提供租户）
     * @param itemView 保全项读模型（提供冻结配置标识与三元核对依据）
     * @return 冻结配置聚合
     */
    public MaintenanceItemConfiguration requireFrozenConfiguration(
            MaintenanceView caseView, MaintenanceCaseItemView itemView) {
        String configurationId = itemView.getConfigurationId();
        if (configurationId == null || configurationId.isBlank()) {
            throw validation("configurationId", "审核任务缺少冻结配置标识");
        }
        MaintenanceItemConfiguration configuration = configurationRepository
                .findById(caseView.getTenantId(), configurationId)
                .orElseThrow(MaintenanceNotFoundException::new)
                .configuration();
        if (!Objects.equals(itemView.getItemCode(), configuration.getDefinition().itemCode())
                || !Objects.equals(itemView.getConfigurationVersion(),
                        configuration.getDefinition().version())
                || !Objects.equals(itemView.getConfigurationContentHash(),
                        configuration.getContentHash())) {
            throw validation("configurationContentHash", "冻结配置与审核时读取的配置不一致");
        }
        return configuration;
    }

    private MaintenanceValidationException validation(String fieldName, String message) {
        return new MaintenanceValidationException(
                "MaintenanceFrozenConfigurationReader", fieldName, message);
    }
}
