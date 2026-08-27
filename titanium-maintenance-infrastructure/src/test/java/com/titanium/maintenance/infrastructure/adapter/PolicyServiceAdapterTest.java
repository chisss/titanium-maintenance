package com.titanium.maintenance.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.titanium.maintenance.infrastructure.client.InsuranceServiceClient;
import com.titanium.maintenance.infrastructure.client.PolicyServiceClient;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.model.Amount;
import com.titanium.policy.api.request.maintenance.ApplyPolicyMaintenanceRequest;
import com.titanium.policy.api.response.InsuranceResponse;
import com.titanium.policy.api.response.PolicyEndorsementResponse;
import com.titanium.policy.api.response.PolicyMaintenanceSnapshotResponse;
import com.titanium.policy.api.response.PolicyResponse;
import com.titanium.policy.api.response.PolicyStatusResponse;
import com.titanium.policy.api.response.maintenance.PolicyMaintenanceApplicationResponse;

class PolicyServiceAdapterTest {

    @Test
    void shouldUnwrapFormalPolicyApiResponses() {
        PolicyResponse policy = new PolicyResponse();
        policy.setPolicyId("policy-1");
        policy.setProductId("product-1");
        policy.setTenantId("tenant-1");
        PolicyStatusResponse status = new PolicyStatusResponse();
        status.setPolicyId("policy-1");
        status.setStatus("EFFECTIVE");
        PolicyServiceAdapter adapter = new PolicyServiceAdapter(
                client(ApiResponse.success(policy), ApiResponse.success(status)), insuranceClient(null));

        assertTrue(adapter.policyExists("policy-1", "tenant-1"));
        assertEquals("product-1", adapter.getPolicyProductId("policy-1", "tenant-1"));
        assertTrue(adapter.getPolicyStatus("policy-1", "tenant-1").active());
    }

    @Test
    void shouldRejectCrossTenantOrFailedPolicyFacts() {
        PolicyResponse policy = new PolicyResponse();
        policy.setPolicyId("policy-1");
        policy.setProductId("product-1");
        policy.setTenantId("another-tenant");
        PolicyServiceAdapter adapter = new PolicyServiceAdapter(
                client(ApiResponse.success(policy), new ApiResponse<>("20001000", "not found", null)),
                insuranceClient(null));

        assertFalse(adapter.policyExists("policy-1", "tenant-1"));
        assertNull(adapter.getPolicyProductId("policy-1", "tenant-1"));
        assertFalse(adapter.getPolicyStatus("policy-1", "tenant-1").active());
    }

    @Test
    void shouldResolveIssuanceBizNoForFinancialSnapshot() {
        Amount premium = new Amount();
        premium.setValue(new BigDecimal("121.20"));
        premium.setCurrency("CNY");
        PolicyResponse policy = new PolicyResponse();
        policy.setPolicyId("policy-1");
        policy.setApplicationId("insurance-1");
        policy.setProductId("product-1");
        policy.setEffectiveDate(LocalDateTime.of(2026, 1, 1, 0, 0));
        policy.setPremium(premium);
        policy.setTenantId("tenant-1");
        InsuranceResponse insurance = new InsuranceResponse();
        insurance.setInsuranceId("insurance-1");
        insurance.setProductId("product-1");
        insurance.setBizNo("issuance-1");
        insurance.setTenantId("tenant-1");
        PolicyServiceAdapter adapter = new PolicyServiceAdapter(
                client(ApiResponse.success(policy), null), insuranceClient(ApiResponse.success(insurance)));

        var snapshot = adapter.getPolicyFinancialSnapshot("policy-1", "tenant-1");

        assertEquals("issuance-1", snapshot.issuanceBizNo());
        assertEquals(new BigDecimal("121.20"), snapshot.premium());
    }

    private PolicyServiceClient client(
            ApiResponse<PolicyResponse> policyResponse,
            ApiResponse<PolicyStatusResponse> statusResponse) {
        return new PolicyServiceClient() {
            @Override
            public ApiResponse<PolicyMaintenanceApplicationResponse> applyMaintenance(
                    String id,
                    ApplyPolicyMaintenanceRequest request,
                    String operatorId,
                    String tenantId) {
                return null;
            }

            @Override
            public ApiResponse<PolicyResponse> getPolicyById(String id, String tenantId) {
                return policyResponse;
            }

            @Override
            public ApiResponse<PolicyStatusResponse> getPolicyStatus(String id, String tenantId) {
                return statusResponse;
            }

            @Override
            public ApiResponse<PolicyMaintenanceSnapshotResponse> getMaintenanceSnapshot(
                    String id,
                    String tenantId) {
                return null;
            }

            @Override
            public ApiResponse<List<PolicyEndorsementResponse>> getEndorsements(
                    String id,
                    String tenantId) {
                return null;
            }
        };
    }

    private InsuranceServiceClient insuranceClient(ApiResponse<InsuranceResponse> response) {
        return (insuranceId, tenantId) -> response;
    }
}
