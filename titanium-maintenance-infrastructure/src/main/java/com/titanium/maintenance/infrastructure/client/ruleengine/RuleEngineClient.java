package com.titanium.maintenance.infrastructure.client.ruleengine;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.ruleengine.api.request.execution.RuleFacts;
import com.titanium.ruleengine.api.response.execution.ValidationResultResponse;
import com.titanium.ruleengine.api.response.ruleset.RuleSetResponse;

/**
 * 规则引擎域 Feign 客户端（防腐适配，路径与响应类型严格对齐 rule-engine 真实契约）
 * <p>
 * 路径 {@code /api/v1/rules} 与 rule-engine 域 {@link com.titanium.ruleengine.api.RuleEngineApi} 保持一致；
 * 响应类型复用 {@code titanium-rule-engine-api} 公开契约 DTO（{@code RuleSetResponse} /
 * {@code ValidationResultResponse}），避免本域自维护镜像 record 导致字段漂移；
 * 求值入参复用 {@code RuleFacts}（与对端契约同源，其键白名单与扁平 JSON 形态由对端保证）。
 * </p>
 * <p>
 * <b>仅声明本域实际使用的两个只读端点</b>（YAGNI）：{@code listRuleSets} 供保全配置发布校验判定
 * 「规则集存在 + 已激活 + 类型为 MAINTENANCE」（AC-03）；{@code validate} 供字段条件规则求值（AC-04）。
 * 规则集的写端点（创建/激活/追加规则）属规则引擎域自身用例，保全域不得调用。
 * </p>
 * <p>
 * 🔴 <b>{@code validate} 的两个可选业务头</b>：{@code X-Business-Id}（保全案件号）/ {@code X-Business-Type}
 * （固定 {@code MAINTENANCE}）落规则执行审计，使规则引擎侧可按保全案件反查执行记录。
 * 二者对端声明为可选，缺失不影响执行本身。
 * </p>
 */
@FeignClient(name = "titanium-rule-engine-service", contextId = "maintenanceRuleEngineClient",
        path = "/api/v1/rules")
public interface RuleEngineClient {

    /**
     * 按类型查询规则集列表（读模型）。
     *
     * @param type 规则集类型码（本域固定传 {@code MAINTENANCE}）
     * @return 该类型全部规则集（含 DRAFT/INACTIVE，激活与否由调用方按 {@code status} 判定）
     */
    @GetMapping
    ApiResponse<List<RuleSetResponse>> listRuleSets(@RequestParam String type);

    /**
     * 执行校验类规则集（所有条件全真才 {@code valid=true}）。
     *
     * @param ruleSetCode  规则集编码
     * @param variables    输入变量
     * @param businessId   业务单号（可选，保全案件号）
     * @param businessType 业务域类型码（可选，{@code BusinessDomainType} 的 code）
     * @return 校验结果
     */
    @PostMapping("/validate/{ruleSetCode}")
    ApiResponse<ValidationResultResponse> validate(@PathVariable String ruleSetCode,
            @RequestBody RuleFacts variables,
            @RequestHeader(value = "X-Business-Id", required = false) String businessId,
            @RequestHeader(value = "X-Business-Type", required = false) String businessType);
}
