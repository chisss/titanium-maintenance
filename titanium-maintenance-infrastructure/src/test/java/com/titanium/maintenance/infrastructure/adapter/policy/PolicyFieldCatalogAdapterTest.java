package com.titanium.maintenance.infrastructure.adapter.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        assertEquals(PolicyFieldValueType.TEXT,
                catalog.requireField("policy.holder.mobile").valueType());
        assertEquals(PolicyFieldMaskingPolicy.MOBILE,
                catalog.requireField("policy.holder.mobile").maskingPolicy());
        assertFalse(catalog.requireField("policy.holder.mobile").capability().executionSupported());
    }

    @Test
    void shouldFailClosedWhenTenantEchoDoesNotMatch() {
        PolicyFieldCatalogAdapter adapter = adapter(ApiResponse.success(response("another-tenant")));

        assertThrows(PolicyFieldCatalogUnavailableException.class, () -> adapter.getCatalog(request()));
    }

    @Test
    void shouldFailClosedWhenRemoteResponseFailsOrContractIsInvalid() {
        PolicyFieldCatalogAdapter failedAdapter =
                adapter(new ApiResponse<>("20000000", "catalog unavailable", null));
        PolicyFieldCatalogResponse invalidCatalog = new PolicyFieldCatalogResponse(
                "tenant-1", "LIFE", "INDIVIDUAL", BUSINESS_DATE, "2026.08.24.1", "bad-hash",
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
        PolicyFieldCatalogClient client = (tenantId, productType, policyType, businessDate) -> response;
        return new PolicyFieldCatalogAdapter(client);
    }

    private PolicyFieldCatalogResponse response(String tenantId) {
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
                null);
        return new PolicyFieldCatalogResponse(
                tenantId,
                "LIFE",
                "INDIVIDUAL",
                BUSINESS_DATE,
                "2026.08.24.1",
                "a".repeat(64),
                List.of(field));
    }
}
