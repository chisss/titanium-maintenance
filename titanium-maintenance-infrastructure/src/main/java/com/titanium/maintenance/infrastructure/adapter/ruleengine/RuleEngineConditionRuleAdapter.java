package com.titanium.maintenance.infrastructure.adapter.ruleengine;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.infrastructure.client.ruleengine.RuleEngineClient;
import com.titanium.maintenance.port.ruleengine.MaintenanceConditionRulePort;
import com.titanium.metadata.enums.BusinessDomainType;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.ruleengine.api.request.execution.RuleFacts;
import com.titanium.ruleengine.api.response.execution.ValidationResultResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 字段条件规则求值适配器（六边形架构 driven adapter，AC-04）
 * <p>
 * {@link MaintenanceConditionRulePort} 的真实实现，调规则引擎校验类执行端点
 * {@code POST /api/v1/rules/validate/{ruleSetCode}}——对端语义是「所有条件全真才 valid」，
 * 恰好表达「该字段在本案此刻是否被条件规则接纳」。<b>不</b>用 {@code execute}
 * （首命中即生效的决策语义，非全部条件成立）也不用 {@code compute}（数值累加语义）。
 * </p>
 * <p>
 * 🔴 <b>失败关闭，禁降级</b>：规则集不存在/未激活、服务不可达、应答非成功码或载荷无效，
 * 一律抛 {@link MaintenanceRemoteCallException}（错误码 {@code MAINTENANCE_RULE_ENGINE_REMOTE_ERROR}
 * 71006017，Web 层映射 HTTP 502）。<b>不得</b>降级为「视为通过」（条件规则静默失效）或
 * 「视为拒绝」（服务抖动被伪装成业务拒绝、用户看到的是"该字段不允许"而实为故障）。
 * </p>
 * <p>
 * 🔴 <b>变量集固定三项</b>（{@link MaintenanceConditionRulePort#FACT_ITEM_CODE} /
 * {@code FACT_FIELD_CODE} / {@code FACT_VALUE}）：规则表达式引用未供给变量时 SpEL 求值<b>显式失败</b>
 * （对未知变量抛异常，不静默取 null）⇒ 规则作家若误用别的变量名，得到的是明确报错而非"条件恒不成立"。
 * 三个键均为标识符语法，含点号的字段码作为<b>值</b>传递（{@code RuleFacts} 键白名单要求）。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleEngineConditionRuleAdapter implements MaintenanceConditionRulePort {

    private final RuleEngineClient ruleEngineClient;

    @Override
    public boolean isAccepted(ConditionRuleRequest request) {
        ValidationResultResponse result = requireEvaluation(request);
        if (!result.isValid()) {
            log.info("字段条件规则未通过, maintenanceId={}, ruleSetCode={}, itemCode={}, fieldCode={}, errors={}",
                    request.maintenanceId(), request.ruleSetCode(), request.itemCode(), request.fieldCode(),
                    result.getErrors());
        }
        return result.isValid();
    }

    /** 求值并校验应答完整性；任一环节无效即失败关闭 */
    private ValidationResultResponse requireEvaluation(ConditionRuleRequest request) {
        RuleFacts facts = toFacts(request);
        ApiResponse<ValidationResultResponse> response;
        try {
            response = ruleEngineClient.validate(request.ruleSetCode(), facts, request.maintenanceId(),
                    BusinessDomainType.MAINTENANCE.getCode());
        } catch (Exception exception) {
            log.warn("规则引擎字段条件规则求值不可达, maintenanceId={}, ruleSetCode={}, 原因={}",
                    request.maintenanceId(), request.ruleSetCode(), exception.getMessage(), exception);
            throw new MaintenanceRemoteCallException(
                    "规则引擎字段条件规则求值不可达（服务不可达）, ruleSetCode=" + request.ruleSetCode(),
                    MaintenanceErrorCode.MAINTENANCE_RULE_ENGINE_REMOTE_ERROR, exception);
        }
        ValidationResultResponse result = response == null ? null : response.getData();
        if (response == null || !response.isSuccess() || result == null) {
            throw new MaintenanceRemoteCallException(
                    "规则引擎字段条件规则求值未返回有效结果, ruleSetCode=" + request.ruleSetCode() + ", code="
                            + (response == null ? "null" : response.getCode()),
                    MaintenanceErrorCode.MAINTENANCE_RULE_ENGINE_REMOTE_ERROR);
        }
        return result;
    }

    /** 求值变量：三项固定键（值可为 null——清空类提案本就没有值，规则引用它时自会判不为真） */
    private RuleFacts toFacts(ConditionRuleRequest request) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(FACT_ITEM_CODE, request.itemCode());
        values.put(FACT_FIELD_CODE, request.fieldCode());
        values.put(FACT_VALUE, request.value());
        return RuleFacts.of(values);
    }
}
