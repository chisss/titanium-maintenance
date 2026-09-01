package com.titanium.maintenance.port.product;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus;

/** Product 域保全 Offering 的权威解析端口。 */
public interface ProductMaintenanceOfferingPort {

    Pattern ITEM_CODE = Pattern.compile("[A-Z][A-Z0-9_]{2,63}");

    /** 按冻结的产品事实和业务时点解析可选保全项。 */
    ProductMaintenanceOfferingEvidence resolve(ProductMaintenanceOfferingRequest request);

    /** Offering 解析请求。 */
    record ProductMaintenanceOfferingRequest(
            String tenantId,
            String productId,
            String productVersion,
            String planVersion,
            PolicyStatus policyStatus,
            MaintenanceChannel source,
            OffsetDateTime businessEffectiveAt) {

        public ProductMaintenanceOfferingRequest {
            tenantId = requireText("tenantId", tenantId);
            productId = requireText("productId", productId);
            productVersion = requireText("productVersion", productVersion);
            planVersion = requireText("planVersion", planVersion);
            if (policyStatus == null || source == null || businessEffectiveAt == null) {
                throw validation("context", "保单状态、受理来源和业务时点不能为空");
            }
        }
    }

    /** 可冻结到案件的 Product Offering 版本证据。 */
    record ProductMaintenanceOfferingEvidence(
            String tenantId,
            String productId,
            String productVersion,
            String planVersion,
            String offeringId,
            String offeringVersion,
            String contentHash,
            OffsetDateTime resolvedAt,
            Set<String> allowedItemCodes) {

        private static final Pattern SHA_256 = Pattern.compile("[a-fA-F0-9]{64}");

        public ProductMaintenanceOfferingEvidence {
            tenantId = requireText("tenantId", tenantId);
            productId = requireText("productId", productId);
            productVersion = requireText("productVersion", productVersion);
            planVersion = requireText("planVersion", planVersion);
            offeringId = requireText("offeringId", offeringId);
            offeringVersion = requireText("offeringVersion", offeringVersion);
            if (contentHash == null || !SHA_256.matcher(contentHash).matches()) {
                throw validation("contentHash", "Offering摘要必须为SHA-256十六进制文本");
            }
            if (resolvedAt == null) {
                throw validation("resolvedAt", "Offering解析时点不能为空");
            }
            if (allowedItemCodes == null || allowedItemCodes.isEmpty()) {
                throw validation("allowedItemCodes", "Offering至少允许一个保全项");
            }
            TreeSet<String> normalizedCodes = new TreeSet<>();
            allowedItemCodes.forEach(code -> {
                String normalizedCode = requireText("allowedItemCodes", code);
                if (!ITEM_CODE.matcher(normalizedCode).matches()) {
                    throw validation("allowedItemCodes", "Offering保全项编码格式非法");
                }
                normalizedCodes.add(normalizedCode);
            });
            contentHash = contentHash.toLowerCase();
            allowedItemCodes = Collections.unmodifiableSet(normalizedCodes);
        }
    }

    private static String requireText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw validation(fieldName, "字段不能为空");
        }
        return value.trim();
    }

    private static MaintenanceValidationException validation(String fieldName, String message) {
        return new MaintenanceValidationException("ProductMaintenanceOfferingPort", fieldName, message);
    }
}
