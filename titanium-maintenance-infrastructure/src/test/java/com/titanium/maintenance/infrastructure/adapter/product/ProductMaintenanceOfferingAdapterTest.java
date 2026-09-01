package com.titanium.maintenance.infrastructure.adapter.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.enums.ProductMaintenanceOfferingFailureReason;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.exception.ProductMaintenanceOfferingException;
import com.titanium.maintenance.infrastructure.client.product.ProductMaintenanceOfferingClient;
import com.titanium.maintenance.port.product.ProductMaintenanceOfferingPort.ProductMaintenanceOfferingEvidence;
import com.titanium.maintenance.port.product.ProductMaintenanceOfferingPort.ProductMaintenanceOfferingRequest;
import com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.product.api.response.maintenance.ProductMaintenanceOfferingResolutionResponse;

class ProductMaintenanceOfferingAdapterTest {

    private final ProductMaintenanceOfferingClient client = mock(ProductMaintenanceOfferingClient.class);
    private ProductMaintenanceOfferingAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ProductMaintenanceOfferingAdapter(client);
    }

    @Test
    void shouldMapFormalProductOfferingResponse() {
        whenResolve(ApiResponse.success(response("product-v3", "plan-v2", "b".repeat(64))));

        ProductMaintenanceOfferingEvidence evidence = adapter.resolve(request());

        assertEquals("offering-1", evidence.offeringId());
        assertEquals(Set.of("POLICY_INFO_CHANGE"), evidence.allowedItemCodes());
    }

    @Test
    void shouldRejectVersionEchoMismatch() {
        whenResolve(ApiResponse.success(response("product-v4", "plan-v2", "b".repeat(64))));

        ProductMaintenanceOfferingException exception = assertThrows(
                ProductMaintenanceOfferingException.class, () -> adapter.resolve(request()));

        assertEquals(ProductMaintenanceOfferingFailureReason.VERSION_MISMATCH, exception.getReason());
    }

    @Test
    void shouldRejectInvalidContentHash() {
        whenResolve(ApiResponse.success(response("product-v3", "plan-v2", "invalid")));

        ProductMaintenanceOfferingException exception = assertThrows(
                ProductMaintenanceOfferingException.class, () -> adapter.resolve(request()));

        assertEquals(ProductMaintenanceOfferingFailureReason.CONTRACT_INVALID, exception.getReason());
    }

    @Test
    void shouldMapProductNotApplicableFailure() {
        whenResolve(new ApiResponse<>("60000203", "Offering不适用", null));

        ProductMaintenanceOfferingException exception = assertThrows(
                ProductMaintenanceOfferingException.class, () -> adapter.resolve(request()));

        assertEquals(ProductMaintenanceOfferingFailureReason.NOT_APPLICABLE, exception.getReason());
    }

    private void whenResolve(ApiResponse<ProductMaintenanceOfferingResolutionResponse> response) {
        ProductMaintenanceOfferingRequest request = request();
        when(client.resolve(
                request.productId(), request.productVersion(), request.planVersion(),
                request.policyStatus().getCode(), request.source().getCode(),
                request.businessEffectiveAt(), request.tenantId()))
                .thenReturn(response);
    }

    private ProductMaintenanceOfferingRequest request() {
        return new ProductMaintenanceOfferingRequest(
                "tenant-1", "product-1", "product-v3", "plan-v2", PolicyStatus.EFFECTIVE,
                MaintenanceChannel.API, OffsetDateTime.parse("2026-08-01T00:00:00+08:00"));
    }

    private ProductMaintenanceOfferingResolutionResponse response(
            String productVersion, String planVersion, String hash) {
        return new ProductMaintenanceOfferingResolutionResponse(
                "tenant-1", "product-1", productVersion, planVersion, "offering-1", "offering-v1",
                hash, OffsetDateTime.parse("2026-08-24T08:01:00Z"), Set.of("POLICY_INFO_CHANGE"));
    }
}
