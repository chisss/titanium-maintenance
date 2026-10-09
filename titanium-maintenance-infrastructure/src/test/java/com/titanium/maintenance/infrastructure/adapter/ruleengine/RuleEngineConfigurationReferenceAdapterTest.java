package com.titanium.maintenance.infrastructure.adapter.ruleengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ConnectException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.constant.MaintenanceReferenceCatalog;
import com.titanium.maintenance.common.exception.MaintenanceConfigurationDependencyException;
import com.titanium.maintenance.infrastructure.client.ruleengine.RuleEngineClient;
import com.titanium.maintenance.port.ruleengine.MaintenanceConfigurationReferencePort.ReferenceValidationEvidence;
import com.titanium.maintenance.port.ruleengine.MaintenanceConfigurationReferencePort.ReferenceValidationRequest;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.ruleengine.api.request.execution.RuleFacts;
import com.titanium.ruleengine.api.response.execution.ValidationResultResponse;
import com.titanium.ruleengine.api.response.ruleset.RuleSetResponse;
import com.titanium.ruleengine.common.enums.RuleSetStatus;
import com.titanium.ruleengine.common.enums.RuleSetType;

/**
 * 保全配置引用权威适配器测试（AC-03：规则码权威去硬编码）
 * <p>
 * 替换的旧实现 {@code LocalMaintenanceConfigurationReferenceAdapter} 用硬编码白名单判「规则码是否存在」，
 * 白名单与规则引擎真实规则集必然漂移；本类把新判据钉死成<b>可逐项证伪</b>的四条：
 * 「在册（编码非空） + 已激活（ACTIVE） + 类型为 MAINTENANCE + 按保全类型一次拉取」，
 * 每条各有一个只改动该项的用例（单条失败即定位到该判据，而非笼统报「解析结果不符」）。
 * </p>
 * <p>
 * 🔴 <b>期望值现取，不内联副本</b>：证据版本取自适配器自身常量
 * （{@link RuleEngineConfigurationReferenceAdapter#EVIDENCE_VERSION}）、权限/模板期望值取自本域目录
 * {@link MaintenanceReferenceCatalog}——若在测试里手抄一份字面量，权威源一改测试就会报「数据不符」，
 * 而红的方向指向被测方（该适配器），最自然的误动作是去改本来正确的那份数据。
 * </p>
 * <p>
 * 🔴 <b>失败关闭的证据是「抛哪个异常 + 哪个错误码」</b>：不可达 / 非成功码 / 载荷无效三条路径
 * 必须都落在 71002004（{@code MAINTENANCE_CONFIGURATION_DEPENDENCY_UNAVAILABLE}，Web 层 502），
 * <b>不得</b>降级为「视为存在」（配置带着不存在的规则上线）或「视为不存在」（服务抖动被伪装成业务拒绝）。
 * </p>
 */
class RuleEngineConfigurationReferenceAdapterTest {

    @Test
    @DisplayName("规则码权威取自规则引擎：按保全类型一次拉取，仅解析在册编码")
    void shouldResolveEnrolledRuleCodesWithSingleTypedQuery() {
        RecordingRuleSetClient client = new RecordingRuleSetClient(ok(List.of(
                ruleSet("APPROVAL_STANDARD", RuleSetType.MAINTENANCE, RuleSetStatus.ACTIVE))));
        RuleEngineConfigurationReferenceAdapter adapter = new RuleEngineConfigurationReferenceAdapter(client);

        ReferenceValidationEvidence evidence = adapter.validate(ruleRequest(
                Set.of("APPROVAL_STANDARD", "MAINTENANCE_ABSENT")));

        assertTrue(evidence.authoritative());
        assertEquals(RuleEngineConfigurationReferenceAdapter.EVIDENCE_VERSION, evidence.evidenceVersion());
        assertEquals(Set.of("APPROVAL_STANDARD"), evidence.resolvedRuleCodes(),
                "在册编码解析、不在册编码落空（差集由校验器落成 RULE_NOT_FOUND）");
        assertNull(evidence.unavailableReason());
        assertEquals(RuleSetType.MAINTENANCE.getCode(), client.requestedType,
                "须按保全类型查询，而不是拉全量后本地筛");
        assertEquals(1, client.listCalls, "一次拉全量 + 本地求交，禁逐码 N+1 查询");
    }

    @Test
    @DisplayName("草稿（DRAFT）规则集不算在册：引用它不被解析")
    void shouldExcludeDraftRuleSets() {
        RecordingRuleSetClient client = new RecordingRuleSetClient(ok(List.of(
                ruleSet("ACTIVE_ONE", RuleSetType.MAINTENANCE, RuleSetStatus.ACTIVE),
                ruleSet("DRAFT_ONE", RuleSetType.MAINTENANCE, RuleSetStatus.DRAFT))));

        ReferenceValidationEvidence evidence = new RuleEngineConfigurationReferenceAdapter(client)
                .validate(ruleRequest(Set.of("ACTIVE_ONE", "DRAFT_ONE")));

        assertEquals(Set.of("ACTIVE_ONE"), evidence.resolvedRuleCodes(),
                "未激活的规则集不得被解析（放行等于让配置引用一个永不生效的规则）");
    }

    @Test
    @DisplayName("已停用（INACTIVE）规则集不算在册：引用它不被解析")
    void shouldExcludeInactiveRuleSets() {
        RecordingRuleSetClient client = new RecordingRuleSetClient(ok(List.of(
                ruleSet("ACTIVE_ONE", RuleSetType.MAINTENANCE, RuleSetStatus.ACTIVE),
                ruleSet("INACTIVE_ONE", RuleSetType.MAINTENANCE, RuleSetStatus.INACTIVE))));

        ReferenceValidationEvidence evidence = new RuleEngineConfigurationReferenceAdapter(client)
                .validate(ruleRequest(Set.of("ACTIVE_ONE", "INACTIVE_ONE")));

        assertEquals(Set.of("ACTIVE_ONE"), evidence.resolvedRuleCodes(),
                "已停用规则集不得被解析（停用即业务上不再可用）");
    }

    @Test
    @DisplayName("对端返回非保全类型：按应答自报类型过滤，不信任服务端已按类型筛过")
    void shouldExcludeRuleSetsOfOtherTypes() {
        RecordingRuleSetClient client = new RecordingRuleSetClient(ok(List.of(
                ruleSet("MAINTENANCE_ONE", RuleSetType.MAINTENANCE, RuleSetStatus.ACTIVE),
                ruleSet("PRICING_ONE", RuleSetType.PRICING, RuleSetStatus.ACTIVE),
                ruleSet("UNDERWRITING_ONE", RuleSetType.UNDERWRITING, RuleSetStatus.ACTIVE))));

        ReferenceValidationEvidence evidence = new RuleEngineConfigurationReferenceAdapter(client)
                .validate(ruleRequest(Set.of("MAINTENANCE_ONE", "PRICING_ONE", "UNDERWRITING_ONE")));

        assertEquals(Set.of("MAINTENANCE_ONE"), evidence.resolvedRuleCodes(),
                "权威判定的依据必须是应答里可核对的事实，而不是对服务端过滤逻辑的假设");
    }

    @Test
    @DisplayName("权限码与模板码仍取本域目录：目录内解析、目录外落空")
    void shouldResolvePermissionsAndTemplatesFromLocalCatalog() {
        // 期望值现取目录自身（权威源），不手抄字面量
        Set<String> knownPermissions = new LinkedHashSet<>(MaintenanceReferenceCatalog.PERMISSION_CODES);
        Set<String> knownTemplates = new LinkedHashSet<>(MaintenanceReferenceCatalog.TEMPLATE_CODES);
        Set<String> requestedPermissions = new LinkedHashSet<>(knownPermissions);
        requestedPermissions.add("maintenance:item:unknown");
        Set<String> requestedTemplates = new LinkedHashSet<>(knownTemplates);
        requestedTemplates.add("NO_SUCH_TEMPLATE");

        RecordingRuleSetClient client = new RecordingRuleSetClient(ok(List.of()));
        ReferenceValidationEvidence evidence = new RuleEngineConfigurationReferenceAdapter(client)
                .validate(new ReferenceValidationRequest(
                        "tenant-1", Set.of(), requestedPermissions, requestedTemplates));

        assertEquals(knownPermissions, evidence.resolvedPermissionCodes(),
                "目录内权限码应全解析、目录外应落空（差集落成 PERMISSION_NOT_FOUND）");
        assertEquals(knownTemplates, evidence.resolvedTemplateCodes(),
                "目录内模板码应全解析、目录外应落空（差集落成 TEMPLATE_NOT_FOUND）");
    }

    @Test
    @DisplayName("应答含 null 元素 / 空编码条目：跳过而非抛空指针")
    void shouldSkipMalformedRuleSetEntries() {
        List<RuleSetResponse> payload = new ArrayList<>();
        payload.add(null);
        payload.add(ruleSet(null, RuleSetType.MAINTENANCE, RuleSetStatus.ACTIVE));
        payload.add(new RuleSetResponse());
        payload.add(ruleSet("VALID_ONE", RuleSetType.MAINTENANCE, RuleSetStatus.ACTIVE));

        ReferenceValidationEvidence evidence = new RuleEngineConfigurationReferenceAdapter(
                new RecordingRuleSetClient(ok(payload)))
                .validate(ruleRequest(Set.of("VALID_ONE")));

        assertEquals(Set.of("VALID_ONE"), evidence.resolvedRuleCodes(),
                "畸形条目应被跳过：判据是「在册且已激活」，不是「应答非空」");
    }

    @Test
    @DisplayName("规则引擎不可达：失败关闭，抛依赖不可用异常（71002004）")
    void shouldFailClosedWhenRuleEngineUnreachable() {
        RuleEngineConfigurationReferenceAdapter adapter = new RuleEngineConfigurationReferenceAdapter(
                new RuleEngineClient() {
                    @Override
                    public ApiResponse<List<RuleSetResponse>> listRuleSets(String type) {
                        throw new RuntimeException(new ConnectException("Connection refused: titanium-rule-engine"));
                    }

                    @Override
                    public ApiResponse<ValidationResultResponse> validate(String ruleSetCode, RuleFacts variables,
                            String businessId, String businessType) {
                        throw new IllegalStateException("列表已失败，不得再调求值端点");
                    }
                });

        MaintenanceConfigurationDependencyException error = assertThrows(
                MaintenanceConfigurationDependencyException.class,
                () -> adapter.validate(ruleRequest(Set.of("APPROVAL_STANDARD"))));
        assertEquals(MaintenanceErrorCode.MAINTENANCE_CONFIGURATION_DEPENDENCY_UNAVAILABLE.getCode(),
                error.getErrorCodeValue());
    }

    @Test
    @DisplayName("应答为空 / 非成功码 / 载荷缺失：失败关闭，不得降级为『引用不存在』")
    void shouldFailClosedWhenResponseInvalid() {
        RuleEngineConfigurationReferenceAdapter nullResponse = new RuleEngineConfigurationReferenceAdapter(
                new RecordingRuleSetClient(null));
        RuleEngineConfigurationReferenceAdapter nonSuccess = new RuleEngineConfigurationReferenceAdapter(
                new RecordingRuleSetClient(
                        ApiResponse.error(MaintenanceErrorCode.MAINTENANCE_CONFIGURATION_DEPENDENCY_UNAVAILABLE)));
        RuleEngineConfigurationReferenceAdapter nullData = new RuleEngineConfigurationReferenceAdapter(
                new RecordingRuleSetClient(ApiResponse.success(null)));

        assertEquals(MaintenanceErrorCode.MAINTENANCE_CONFIGURATION_DEPENDENCY_UNAVAILABLE.getCode(),
                assertThrows(MaintenanceConfigurationDependencyException.class,
                        () -> nullResponse.validate(ruleRequest(Set.of("APPROVAL_STANDARD"))))
                        .getErrorCodeValue());
        assertEquals(MaintenanceErrorCode.MAINTENANCE_CONFIGURATION_DEPENDENCY_UNAVAILABLE.getCode(),
                assertThrows(MaintenanceConfigurationDependencyException.class,
                        () -> nonSuccess.validate(ruleRequest(Set.of("APPROVAL_STANDARD"))))
                        .getErrorCodeValue());
        assertEquals(MaintenanceErrorCode.MAINTENANCE_CONFIGURATION_DEPENDENCY_UNAVAILABLE.getCode(),
                assertThrows(MaintenanceConfigurationDependencyException.class,
                        () -> nullData.validate(ruleRequest(Set.of("APPROVAL_STANDARD"))))
                        .getErrorCodeValue());
    }

    private ReferenceValidationRequest ruleRequest(Set<String> ruleCodes) {
        return new ReferenceValidationRequest("tenant-1", ruleCodes, Set.of(), Set.of());
    }

    private ApiResponse<List<RuleSetResponse>> ok(List<RuleSetResponse> ruleSets) {
        return ApiResponse.success(ruleSets);
    }

    private RuleSetResponse ruleSet(String code, RuleSetType type, RuleSetStatus status) {
        return RuleSetResponse.builder()
                .ruleSetId("ruleset-" + code)
                .ruleSetCode(code)
                .ruleSetType(type)
                .status(status)
                .build();
    }

    /** 记录入参的规则集查询桩；求值端点属另一端口职责，被调即失败 */
    private static final class RecordingRuleSetClient implements RuleEngineClient {

        private final ApiResponse<List<RuleSetResponse>> response;
        private       String                             requestedType;
        private       int                                listCalls;

        private RecordingRuleSetClient(ApiResponse<List<RuleSetResponse>> response) {
            this.response = response;
        }

        @Override
        public ApiResponse<List<RuleSetResponse>> listRuleSets(String type) {
            this.requestedType = type;
            this.listCalls++;
            return response;
        }

        @Override
        public ApiResponse<ValidationResultResponse> validate(String ruleSetCode, RuleFacts variables,
                String businessId, String businessType) {
            throw new IllegalStateException("配置引用校验不得调用求值端点 validate");
        }
    }
}
