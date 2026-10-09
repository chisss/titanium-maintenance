package com.titanium.maintenance.infrastructure.adapter.ruleengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ConnectException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.infrastructure.client.ruleengine.RuleEngineClient;
import com.titanium.maintenance.port.ruleengine.MaintenanceConditionRulePort;
import com.titanium.maintenance.port.ruleengine.MaintenanceConditionRulePort.ConditionRuleRequest;
import com.titanium.metadata.enums.BusinessDomainType;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.ruleengine.api.request.execution.RuleFacts;
import com.titanium.ruleengine.api.response.execution.ValidationResultResponse;
import com.titanium.ruleengine.api.response.ruleset.RuleSetResponse;

/**
 * 字段条件规则求值适配器测试（AC-04：{@code conditionRuleCode} 真执行）
 * <p>
 * 三态必须彼此可分，任一被压成同形即整条链静默失效：
 * <ul>
 *   <li><b>条件成立</b> ⇒ {@code true}（放行）；</li>
 *   <li><b>条件不成立</b> ⇒ {@code false}（业务拒绝，<b>不是</b>异常）；</li>
 *   <li><b>问不到</b>（不可达 / 非成功码 / 载荷无效） ⇒ 抛 71006017（P
 *       {@code MAINTENANCE_RULE_ENGINE_REMOTE_ERROR}，Web 层 502），<b>禁</b>降级为前两者
 *       ——「视为通过」让条件规则静默失效，「视为拒绝」把服务抖动伪装成业务拒绝。</li>
 * </ul>
 * </p>
 * <p>
 * 🔴 <b>变量集是契约而非实现细节</b>：固定供给三项（{@link MaintenanceConditionRulePort#FACT_ITEM_CODE} /
 * {@code FACT_FIELD_CODE} / {@code FACT_VALUE}），键名取自端口常量（期望值现取，不手抄）。
 * 供多了污染规则变量命名空间，供少了规则表达式引用时会在求值期显式失败——两个方向都要钉住。
 * </p>
 */
class RuleEngineConditionRuleAdapterTest {

    @Test
    @DisplayName("条件全真：字段放行（校验端点 valid=true）")
    void shouldAcceptWhenConditionsHold() {
        RuleEngineConditionRuleAdapter adapter = new RuleEngineConditionRuleAdapter(
                new RecordingValidateClient(validation(true)));

        assertTrue(adapter.isAccepted(request("13900000000")));
    }

    @Test
    @DisplayName("条件不成立：返回 false（业务拒绝），不得升级为异常")
    void shouldRejectFieldWhenConditionsDoNotHold() {
        RuleEngineConditionRuleAdapter adapter = new RuleEngineConditionRuleAdapter(
                new RecordingValidateClient(validation(false, "手机号不在可变更白名单内")));

        assertFalse(adapter.isAccepted(request("13900000000")),
                "条件不成立是业务结论，抛异常会把「不允许」与「问不到」压成同形");
    }

    @Test
    @DisplayName("求值入参：三项固定变量 + 保全案件号与保全域类型两个业务头")
    void shouldSendThreeFixedFactsWithBusinessHeaders() {
        RecordingValidateClient client = new RecordingValidateClient(validation(true));

        new RuleEngineConditionRuleAdapter(client).isAccepted(request("13900000000"));

        assertEquals("RULE_MOBILE_ALLOWED", client.ruleSetCode, "规则集编码须原样透传冻结配置里声明的码");
        assertNotNull(client.facts, "事实不得为空");
        Map<String, Object> facts = client.facts.values();
        assertEquals(Set.of(MaintenanceConditionRulePort.FACT_ITEM_CODE,
                MaintenanceConditionRulePort.FACT_FIELD_CODE,
                MaintenanceConditionRulePort.FACT_VALUE),
                facts.keySet(), "变量集固定三项：多供污染命名空间，少供会让规则表达式求值期显式失败");
        assertEquals("POLICY_INFO_CHANGE", facts.get(MaintenanceConditionRulePort.FACT_ITEM_CODE));
        assertEquals("policy.holder.mobile", facts.get(MaintenanceConditionRulePort.FACT_FIELD_CODE),
                "含点号的字段码只能作为值传递（键须为标识符语法）");
        assertEquals("13900000000", facts.get(MaintenanceConditionRulePort.FACT_VALUE));
        assertEquals("maintenance-1", client.businessId, "X-Business-Id 须为保全案件号，供规则引擎按案件反查");
        assertEquals(BusinessDomainType.MAINTENANCE.getCode(), client.businessType,
                "X-Business-Type 须为保全域码（端口固有属性，非调用方逐次传入）");
    }

    @Test
    @DisplayName("清空类提案：value 为 null 仍须占位传递，不得整键丢弃")
    void shouldKeepNullValuedFactForClearingProposal() {
        RecordingValidateClient client = new RecordingValidateClient(validation(true));

        new RuleEngineConditionRuleAdapter(client).isAccepted(request(null));

        Map<String, Object> facts = client.facts.values();
        assertTrue(facts.containsKey(MaintenanceConditionRulePort.FACT_VALUE),
                "键缺失会让引用 value 的规则表达式在求值期报错，与「值就是空」是两种语义");
        assertNull(facts.get(MaintenanceConditionRulePort.FACT_VALUE));
    }

    @Test
    @DisplayName("规则引擎不可达：失败关闭，抛远程调用异常（71006017）并保留根因")
    void shouldFailClosedWhenUnreachable() {
        RuleEngineConditionRuleAdapter adapter = new RuleEngineConditionRuleAdapter(new RuleEngineClient() {
            @Override
            public ApiResponse<List<RuleSetResponse>> listRuleSets(String type) {
                throw new IllegalStateException("条件规则求值不得调用规则集列表端点");
            }

            @Override
            public ApiResponse<ValidationResultResponse> validate(String ruleSetCode, RuleFacts variables,
                    String businessId, String businessType) {
                throw new RuntimeException(new ConnectException("Connection refused: titanium-rule-engine"));
            }
        });

        MaintenanceRemoteCallException error = assertThrows(MaintenanceRemoteCallException.class,
                () -> adapter.isAccepted(request("13900000000")));
        assertEquals(MaintenanceErrorCode.MAINTENANCE_RULE_ENGINE_REMOTE_ERROR.getCode(), error.getErrorCodeValue());
        assertNotNull(error.getCause(), "根因不得在适配器边界丢失");
    }

    @Test
    @DisplayName("应答为空 / 非成功码 / 载荷缺失：失败关闭，不得当作『条件不成立』")
    void shouldFailClosedWhenResponseInvalid() {
        RuleEngineConditionRuleAdapter nullResponse = new RuleEngineConditionRuleAdapter(
                new RecordingValidateClient(null));
        RuleEngineConditionRuleAdapter nonSuccess = new RuleEngineConditionRuleAdapter(
                new RecordingValidateClient(ApiResponse.error(MaintenanceErrorCode.MAINTENANCE_RULE_ENGINE_REMOTE_ERROR)));
        RuleEngineConditionRuleAdapter nullData = new RuleEngineConditionRuleAdapter(
                new RecordingValidateClient(ApiResponse.success(null)));

        assertEquals(MaintenanceErrorCode.MAINTENANCE_RULE_ENGINE_REMOTE_ERROR.getCode(),
                assertThrows(MaintenanceRemoteCallException.class,
                        () -> nullResponse.isAccepted(request("13900000000")))
                        .getErrorCodeValue());
        assertEquals(MaintenanceErrorCode.MAINTENANCE_RULE_ENGINE_REMOTE_ERROR.getCode(),
                assertThrows(MaintenanceRemoteCallException.class,
                        () -> nonSuccess.isAccepted(request("13900000000")))
                        .getErrorCodeValue());
        assertEquals(MaintenanceErrorCode.MAINTENANCE_RULE_ENGINE_REMOTE_ERROR.getCode(),
                assertThrows(MaintenanceRemoteCallException.class,
                        () -> nullData.isAccepted(request("13900000000")))
                        .getErrorCodeValue());
    }

    private ConditionRuleRequest request(String value) {
        return new ConditionRuleRequest("tenant-1", "maintenance-1", "RULE_MOBILE_ALLOWED",
                "POLICY_INFO_CHANGE", "policy.holder.mobile", value);
    }

    private ApiResponse<ValidationResultResponse> validation(boolean valid, String... errors) {
        return ApiResponse.success(ValidationResultResponse.builder()
                .valid(valid)
                .errors(List.of(errors))
                .build());
    }

    /** 记录入参的求值桩；规则集列表端点属另一端口职责，被调即失败 */
    private static final class RecordingValidateClient implements RuleEngineClient {

        private final ApiResponse<ValidationResultResponse> response;
        private       String                                ruleSetCode;
        private       RuleFacts                             facts;
        private       String                                businessId;
        private       String                                businessType;

        private RecordingValidateClient(ApiResponse<ValidationResultResponse> response) {
            this.response = response;
        }

        @Override
        public ApiResponse<List<RuleSetResponse>> listRuleSets(String type) {
            throw new IllegalStateException("条件规则求值不得调用规则集列表端点");
        }

        @Override
        public ApiResponse<ValidationResultResponse> validate(String ruleSetCode, RuleFacts variables,
                String businessId, String businessType) {
            this.ruleSetCode = ruleSetCode;
            this.facts = variables;
            this.businessId = businessId;
            this.businessType = businessType;
            return response;
        }
    }
}
