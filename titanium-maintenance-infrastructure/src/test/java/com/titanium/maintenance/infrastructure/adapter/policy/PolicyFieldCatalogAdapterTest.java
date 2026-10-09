package com.titanium.maintenance.infrastructure.adapter.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.exception.PolicyFieldCatalogUnavailableException;
import com.titanium.maintenance.infrastructure.client.policy.PolicyFieldCatalogClient;
import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort.PolicyFieldCatalogEvidence;
import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort.PolicyFieldCatalogRequest;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldObjectType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;
import com.titanium.metadata.fieldcatalog.FieldAnalysisCapability;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.response.fieldcatalog.PolicyFieldCapabilityResponse;
import com.titanium.policy.api.response.fieldcatalog.PolicyFieldCatalogResponse;
import com.titanium.policy.api.response.fieldcatalog.PolicyFieldDescriptorResponse;

class PolicyFieldCatalogAdapterTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 24);

    @Test
    void shouldMapTrustedPolicyCatalogIntoMaintenanceEvidence() {
        PolicyFieldCatalogAdapter adapter = adapter(ApiResponse.success(response("tenant-1")));

        PolicyFieldCatalogEvidence catalog = adapter.getCatalog(request());

        assertEquals("2026.08.24.1", catalog.catalogVersion());
        assertFalse(catalog.fallback(), "正常命中不得被标成降级");
        assertEquals(PolicyFieldValueType.TEXT,
                catalog.requireField("policy.holder.mobile").valueType());
        assertEquals(PolicyFieldMaskingPolicy.MOBILE,
                catalog.requireField("policy.holder.mobile").maskingPolicy());
        assertFalse(catalog.requireField("policy.holder.mobile").capability().executionSupported());
    }

    /**
     * 🔴 分析位必须**逐位**透传，不能只断言非空。
     * 期望值取自 policy 真实目录口径（{@code policy.holder.mobile}：TEXT 非集合 ⇒
     * analyzable=true / aggregatable=false / groupable=false / filterable=true，
     * 见种子 DML 的 CASE 表达式），两位为 true 使本断言能分辨「透传了」与
     * 「漏传后退化成默认全 false」——只断言非空对此完全瞎。
     */
    @Test
    void shouldTransmitAnalysisBitsFieldByField() {
        PolicyFieldCatalogAdapter adapter = adapter(ApiResponse.success(response("tenant-1")));

        FieldAnalysisCapability analysis = adapter.getCatalog(request())
                .requireField("policy.holder.mobile")
                .analysis();

        assertTrue(analysis.analyzable(), "analyzable");
        assertFalse(analysis.aggregatable(), "aggregatable");
        assertFalse(analysis.groupable(), "groupable");
        assertTrue(analysis.filterable(), "filterable");
    }

    /**
     * 🔴 降级标记必须**逐值透传**，不得在防腐层被抹平。
     *
     * <p>与上一条配套成对：上游 {@code false} 要求本域 {@code false}，上游 {@code true} 要求本域
     * {@code true}——只断一侧的写法对「写死常量」完全瞎（写死 {@code false} 能过前者，
     * 写死 {@code true} 能过后者）。降级是上游的<b>权威口径</b>：抹平它，则「本域取到的是最早版本
     * 而非该业务时点的真实版本」这一事实在本域彻底不可见。</p>
     */
    @Test
    void shouldPropagateFallbackFlagIntoEvidence() {
        PolicyFieldCatalogAdapter adapter =
                adapter(ApiResponse.success(response("tenant-1", true)));

        PolicyFieldCatalogEvidence catalog = adapter.getCatalog(request());

        assertTrue(catalog.fallback(), "上游降级标记必须透传到本域证据，不得在防腐层被抹平");
        assertEquals("2026.08.24.1", catalog.catalogVersion(), "降级时仍要带回实际取到的版本号");
    }

    /** 分析位缺失（反序列化丢字段/上游契约漂移）必须失败关闭，不得静默退化为可分析。 */
    @Test
    void shouldFailClosedWhenAnalysisBitsAreMissing() {
        PolicyFieldCatalogAdapter adapter = adapter(ApiResponse.success(responseWithoutAnalysis()));

        assertThrows(PolicyFieldCatalogUnavailableException.class, () -> adapter.getCatalog(request()));
    }

    @Test
    void shouldFailClosedWhenTenantEchoDoesNotMatch() {
        PolicyFieldCatalogAdapter adapter = adapter(ApiResponse.success(response("another-tenant")));

        assertThrows(PolicyFieldCatalogUnavailableException.class, () -> adapter.getCatalog(request()));
    }

    @Test
    void shouldFailClosedWhenRemoteResponseFailsOrContractIsInvalid() {
        PolicyFieldCatalogAdapter failedAdapter =
                adapter(ApiResponse.passthrough("20000000", "catalog unavailable"));
        // fallback=false：本夹具校验的是「哈希非法即拒绝」，与降级标记无关
        PolicyFieldCatalogResponse invalidCatalog = new PolicyFieldCatalogResponse(
                "tenant-1", "LIFE", "INDIVIDUAL", BUSINESS_DATE, false, "2026.08.24.1", "bad-hash",
                response("tenant-1").fields());
        PolicyFieldCatalogAdapter invalidAdapter = adapter(ApiResponse.success(invalidCatalog));

        assertThrows(PolicyFieldCatalogUnavailableException.class, () -> failedAdapter.getCatalog(request()));
        assertThrows(PolicyFieldCatalogUnavailableException.class, () -> invalidAdapter.getCatalog(request()));
        assertThrows(PolicyFieldCatalogUnavailableException.class, () -> invalidAdapter.getCatalog(null));
    }

    private PolicyFieldCatalogRequest request() {
        return new PolicyFieldCatalogRequest("tenant-1", "LIFE", "INDIVIDUAL", BUSINESS_DATE);
    }

    private PolicyFieldCatalogAdapter adapter(ApiResponse<PolicyFieldCatalogResponse> response) {
        PolicyFieldCatalogClient client = (productType, policyType, businessDate) -> response;
        return new PolicyFieldCatalogAdapter(client);
    }

    private PolicyFieldCatalogResponse response(String tenantId) {
        return response(tenantId, new FieldAnalysisCapability(true, false, false, true), false);
    }

    /** 上游标记降级的响应：业务日期早于目录最早版本，取到的是最早版本而非该时点真实版本。 */
    private PolicyFieldCatalogResponse response(String tenantId, boolean fallback) {
        return response(tenantId, new FieldAnalysisCapability(true, false, false, true), fallback);
    }

    /** 分析位缺失的响应：模拟上游契约漂移或反序列化丢字段。 */
    private PolicyFieldCatalogResponse responseWithoutAnalysis() {
        return response("tenant-1", null, false);
    }

    private PolicyFieldCatalogResponse response(
            String tenantId, FieldAnalysisCapability analysis, boolean fallback) {
        PolicyFieldCapabilityResponse capability =
                new PolicyFieldCapabilityResponse(true, true, true, false, false, "POLICY_INFO_CHANGE");
        PolicyFieldDescriptorResponse field = new PolicyFieldDescriptorResponse(
                "policy.holder.mobile",
                PolicyFieldObjectType.POLICY_HOLDER,
                PolicyFieldValueType.TEXT,
                "policy.field.holder.mobile",
                false,
                null,
                capability,
                PolicyFieldSensitivityLevel.SENSITIVE,
                PolicyFieldMaskingPolicy.MOBILE,
                null,
                analysis);
        return new PolicyFieldCatalogResponse(
                tenantId,
                "LIFE",
                "INDIVIDUAL",
                BUSINESS_DATE,
                fallback,
                "2026.08.24.1",
                "a".repeat(64),
                List.of(field));
    }
}
