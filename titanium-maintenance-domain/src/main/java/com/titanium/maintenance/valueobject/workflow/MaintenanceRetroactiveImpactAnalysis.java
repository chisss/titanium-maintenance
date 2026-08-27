package com.titanium.maintenance.valueobject.workflow;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactAnalysisStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactDomain;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactItemStatus;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 案件级追溯影响分析事实；分析完成不代表可以执行 Policy 生效。 */
public record MaintenanceRetroactiveImpactAnalysis(
        String analysisId,
        int analysisVersion,
        String operationId,
        String requestHash,
        LocalDateTime scopeFrom,
        LocalDateTime scopeTo,
        MaintenanceRetroactiveImpactAnalysisStatus status,
        List<MaintenanceRetroactiveImpactDomain> coveredDomains,
        List<MaintenanceRetroactiveImpactItem> items,
        String evidenceVersion,
        String resultHash,
        String failureCode,
        String failureMessage,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime updatedAt) {

    private static final List<MaintenanceRetroactiveImpactDomain> REQUIRED_DOMAINS = List.of(
            MaintenanceRetroactiveImpactDomain.POLICY,
            MaintenanceRetroactiveImpactDomain.BILLING,
            MaintenanceRetroactiveImpactDomain.PAYMENT,
            MaintenanceRetroactiveImpactDomain.CLAIM);

    public MaintenanceRetroactiveImpactAnalysis {
        analysisId = requireText("analysisId", analysisId);
        operationId = requireText("operationId", operationId);
        requestHash = requireHash("requestHash", requestHash);
        coveredDomains = coveredDomains == null ? List.of() : List.copyOf(coveredDomains);
        items = items == null ? List.of() : List.copyOf(items);
        evidenceVersion = normalize(evidenceVersion);
        resultHash = normalizeHash("resultHash", resultHash);
        failureCode = normalize(failureCode);
        failureMessage = normalize(failureMessage);
        if (analysisVersion < 1 || scopeFrom == null || scopeTo == null || !scopeFrom.isBefore(scopeTo)
                || status == null || startedAt == null || updatedAt == null) {
            throw invalid("analysis", "分析版本、范围、状态和时间不能为空且范围必须递增");
        }
        validateUniqueDomains(coveredDomains);
        validateUniqueItems(items);
        if (status == MaintenanceRetroactiveImpactAnalysisStatus.COMPLETED) {
            validateCoverage(coveredDomains);
            items.forEach(item -> validateItemScope(item, scopeFrom, scopeTo));
        }
        validateState(status, coveredDomains, items, evidenceVersion, resultHash,
                failureCode, failureMessage, completedAt);
    }

    public static MaintenanceRetroactiveImpactAnalysis start(
            String analysisId,
            int analysisVersion,
            String operationId,
            String requestHash,
            LocalDateTime scopeFrom,
            LocalDateTime scopeTo,
            LocalDateTime startedAt) {
        return new MaintenanceRetroactiveImpactAnalysis(
                analysisId, analysisVersion, operationId, requestHash, scopeFrom, scopeTo,
                MaintenanceRetroactiveImpactAnalysisStatus.ANALYZING, List.of(), List.of(),
                null, null, null, null, startedAt, null, startedAt);
    }

    public MaintenanceRetroactiveImpactAnalysis complete(
            List<MaintenanceRetroactiveImpactDomain> domains,
            List<MaintenanceRetroactiveImpactItem> resultItems,
            String resultEvidenceVersion,
            String completedResultHash,
            LocalDateTime completedTime) {
        if (status == MaintenanceRetroactiveImpactAnalysisStatus.COMPLETED) {
            if (requireHash("resultHash", completedResultHash).equals(resultHash)) {
                return this;
            }
            throw invalid("resultHash", "同一分析操作已存在不同结果");
        }
        requireAnalyzing();
        validateCoverage(domains);
        if (resultItems == null) {
            throw invalid("items", "影响项列表不能为空");
        }
        List<MaintenanceRetroactiveImpactItem> normalizedItems = List.copyOf(resultItems);
        normalizedItems.forEach(item -> validateItemScope(item, scopeFrom, scopeTo));
        return new MaintenanceRetroactiveImpactAnalysis(
                analysisId, analysisVersion, operationId, requestHash, scopeFrom, scopeTo,
                MaintenanceRetroactiveImpactAnalysisStatus.COMPLETED, domains, normalizedItems,
                requireText("evidenceVersion", resultEvidenceVersion),
                requireHash("resultHash", completedResultHash), null, null,
                startedAt, requireCompletionTime(completedTime), requireCompletionTime(completedTime));
    }

    public MaintenanceRetroactiveImpactAnalysis fail(
            String code,
            String message,
            LocalDateTime failedAt) {
        if (status == MaintenanceRetroactiveImpactAnalysisStatus.FAILED) {
            if (requireText("failureCode", code).equals(failureCode)
                    && requireText("failureMessage", message).equals(failureMessage)) {
                return this;
            }
            throw invalid("failure", "同一分析操作已存在不同失败结果");
        }
        requireAnalyzing();
        LocalDateTime failureTime = requireCompletionTime(failedAt);
        return new MaintenanceRetroactiveImpactAnalysis(
                analysisId, analysisVersion, operationId, requestHash, scopeFrom, scopeTo,
                MaintenanceRetroactiveImpactAnalysisStatus.FAILED, List.of(), List.of(),
                null, null, requireText("failureCode", code), requireText("failureMessage", message),
                startedAt, failureTime, failureTime);
    }

    public boolean sameStartRequest(String candidateOperationId, String candidateRequestHash) {
        return operationId.equals(candidateOperationId)
                && requestHash.equals(requireHash("requestHash", candidateRequestHash));
    }

    public int blockingItemCount() {
        return (int) items.stream().filter(MaintenanceRetroactiveImpactItem::blocksEffect).count();
    }

    public int pendingItemCount() {
        return (int) items.stream()
                .filter(item -> item.handlingStatus() == MaintenanceRetroactiveImpactItemStatus.PENDING)
                .count();
    }

    public static List<MaintenanceRetroactiveImpactDomain> requiredDomains() {
        return REQUIRED_DOMAINS;
    }

    private static void validateItemScope(
            MaintenanceRetroactiveImpactItem item,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd) {
        if (item == null || !item.occurredAt().isAfter(rangeStart) || item.occurredAt().isAfter(rangeEnd)) {
            throw invalid("items", "影响项必须发生在追溯时点之后且不晚于分析范围终点");
        }
        if (item.handlingStatus() != MaintenanceRetroactiveImpactItemStatus.PENDING) {
            throw invalid("items", "新分析发现的影响项必须处于待处理状态");
        }
    }

    private void requireAnalyzing() {
        if (status != MaintenanceRetroactiveImpactAnalysisStatus.ANALYZING) {
            throw invalid("status", "当前追溯影响分析不能记录结果");
        }
    }

    private LocalDateTime requireCompletionTime(LocalDateTime time) {
        if (time == null || time.isBefore(startedAt)) {
            throw invalid("completedAt", "分析结果时间不能早于开始时间");
        }
        return time;
    }

    private static void validateCoverage(List<MaintenanceRetroactiveImpactDomain> domains) {
        if (domains == null || !new HashSet<>(domains).equals(Set.copyOf(REQUIRED_DOMAINS))) {
            throw invalid("coveredDomains", "Policy、Billing、Payment、Claim 四个权威域必须全部完成取证");
        }
    }

    private static void validateUniqueDomains(List<MaintenanceRetroactiveImpactDomain> domains) {
        if (domains.stream().anyMatch(domain -> domain == null)
                || new HashSet<>(domains).size() != domains.size()) {
            throw invalid("coveredDomains", "取证域不能为空或重复");
        }
    }

    private static void validateUniqueItems(List<MaintenanceRetroactiveImpactItem> resultItems) {
        Set<String> ids = new HashSet<>();
        if (resultItems.stream().anyMatch(item -> item == null || !ids.add(item.itemId()))) {
            throw invalid("items", "影响项不能为空且标识不能重复");
        }
    }

    private static void validateState(
            MaintenanceRetroactiveImpactAnalysisStatus currentStatus,
            List<MaintenanceRetroactiveImpactDomain> domains,
            List<MaintenanceRetroactiveImpactItem> resultItems,
            String currentEvidenceVersion,
            String currentResultHash,
            String currentFailureCode,
            String currentFailureMessage,
            LocalDateTime currentCompletedAt) {
        if (currentStatus == MaintenanceRetroactiveImpactAnalysisStatus.ANALYZING
                && (!domains.isEmpty() || !resultItems.isEmpty() || currentEvidenceVersion != null
                        || currentResultHash != null || currentFailureCode != null
                        || currentFailureMessage != null || currentCompletedAt != null)) {
            throw invalid("status", "分析中状态不能携带结果或失败事实");
        }
        if (currentStatus == MaintenanceRetroactiveImpactAnalysisStatus.COMPLETED
                && (domains.isEmpty() || currentEvidenceVersion == null || currentResultHash == null
                        || currentFailureCode != null || currentFailureMessage != null || currentCompletedAt == null)) {
            throw invalid("status", "分析完成状态缺少覆盖范围、结果摘要或完成时间");
        }
        if (currentStatus == MaintenanceRetroactiveImpactAnalysisStatus.FAILED
                && (!domains.isEmpty() || !resultItems.isEmpty() || currentEvidenceVersion != null
                        || currentResultHash != null || currentFailureCode == null
                        || currentFailureMessage == null || currentCompletedAt == null)) {
            throw invalid("status", "分析失败状态必须只携带失败事实");
        }
    }

    private static String requireHash(String field, String value) {
        value = requireText(field, value).toLowerCase();
        if (!value.matches("[0-9a-f]{64}")) {
            throw invalid(field, "摘要必须是64位SHA-256");
        }
        return value;
    }

    private static String normalizeHash(String field, String value) {
        return value == null || value.isBlank() ? null : requireHash(field, value);
    }

    private static String requireText(String field, String value) {
        if (value == null || value.isBlank()) {
            throw invalid(field, "字段不能为空");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static MaintenanceValidationException invalid(String field, String message) {
        return new MaintenanceValidationException("MaintenanceRetroactiveImpactAnalysis", field, message);
    }
}
