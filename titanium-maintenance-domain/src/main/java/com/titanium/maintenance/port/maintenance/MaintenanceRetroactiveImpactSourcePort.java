package com.titanium.maintenance.port.maintenance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactDomain;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveImpactItem;

/** 单个权威域的追溯影响取证端口；由 Application 汇总多个实现。 */
public interface MaintenanceRetroactiveImpactSourcePort {

    /** 当前实现负责的权威归属域。 */
    MaintenanceRetroactiveImpactDomain sourceDomain();

    /** 查询追溯范围内可能受影响的权威事实。 */
    SourceEvidence collect(ImpactRequest request);

    /** 案件冻结的追溯影响查询范围。 */
    record ImpactRequest(
            String tenantId,
            String maintenanceId,
            String policyId,
            LocalDateTime scopeFrom,
            LocalDateTime scopeTo) {

        public ImpactRequest {
            tenantId = requireText("tenantId", tenantId);
            maintenanceId = requireText("maintenanceId", maintenanceId);
            policyId = requireText("policyId", policyId);
            if (scopeFrom == null || scopeTo == null || !scopeFrom.isBefore(scopeTo)) {
                throw invalid("scope", "追溯影响查询范围不能为空且必须递增");
            }
        }

        public String requestHash() {
            return hash(tenantId, maintenanceId, policyId, scopeFrom.toString(), scopeTo.toString());
        }
    }

    /** 单个权威域的完整取证结果，空影响列表仍表示该域已覆盖。 */
    record SourceEvidence(
            MaintenanceRetroactiveImpactDomain sourceDomain,
            String evidenceVersion,
            List<MaintenanceRetroactiveImpactItem> items) {

        public SourceEvidence {
            evidenceVersion = requireText("evidenceVersion", evidenceVersion);
            items = items == null ? List.of() : List.copyOf(items);
            if (sourceDomain == null) {
                throw invalid("sourceDomain", "权威归属域不能为空");
            }
            Set<String> itemIds = new HashSet<>();
            if (items.stream().anyMatch(item -> item == null || item.sourceDomain() != sourceDomain
                    || !itemIds.add(item.itemId()))) {
                throw invalid("items", "影响项不能为空、重复或归属其他权威域");
            }
        }
    }

    /** 对全部权威域结果生成与顺序无关的完成摘要。 */
    static String resultHash(List<SourceEvidence> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            throw invalid("evidence", "权威域证据不能为空");
        }
        List<String> canonical = evidence.stream()
                .sorted(Comparator.comparing(item -> item.sourceDomain().getCode()))
                .flatMap(item -> {
                    List<String> itemHashes = item.items().stream()
                            .sorted(Comparator.comparing(MaintenanceRetroactiveImpactItem::itemId))
                            .map(MaintenanceRetroactiveImpactItem::evidenceHash)
                            .toList();
                    return Stream.concat(
                            Stream.of(item.sourceDomain().getCode(), item.evidenceVersion()),
                            itemHashes.stream());
                })
                .toList();
        return hash(canonical.toArray(String[]::new));
    }

    /** 为单条跨域事实生成不含敏感明文的稳定摘要。 */
    static String itemHash(String... values) {
        return hash(values);
    }

    private static String hash(String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
                digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) ':');
                digest.update(bytes);
                digest.update((byte) '\n');
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256不可用", exception);
        }
    }

    private static String requireText(String field, String value) {
        if (value == null || value.isBlank()) {
            throw invalid(field, "字段不能为空");
        }
        return value.trim();
    }

    private static MaintenanceValidationException invalid(String field, String message) {
        return new MaintenanceValidationException("MaintenanceRetroactiveImpactSourcePort", field, message);
    }
}
