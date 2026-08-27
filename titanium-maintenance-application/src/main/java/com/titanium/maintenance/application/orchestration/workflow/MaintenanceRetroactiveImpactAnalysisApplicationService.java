package com.titanium.maintenance.application.orchestration.workflow;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import com.titanium.maintenance.application.command.MaintenanceRetroactiveImpactAnalysisInput;
import com.titanium.maintenance.application.model.MaintenanceRetroactiveImpactAnalysisResult;
import com.titanium.maintenance.command.CompleteMaintenanceRetroactiveImpactAnalysisCommand;
import com.titanium.maintenance.command.FailMaintenanceRetroactiveImpactAnalysisCommand;
import com.titanium.maintenance.command.StartMaintenanceRetroactiveImpactAnalysisCommand;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactAnalysisStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactDomain;
import com.titanium.maintenance.common.exception.MaintenanceNotFoundException;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.port.MaintenanceRetroactiveImpactSourcePort;
import com.titanium.maintenance.port.MaintenanceRetroactiveImpactSourcePort.ImpactRequest;
import com.titanium.maintenance.port.MaintenanceRetroactiveImpactSourcePort.SourceEvidence;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveImpactAnalysis;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveImpactItem;

import lombok.RequiredArgsConstructor;

/** 编排追溯范围冻结、四域权威取证与结果事实落库。 */
@Service
@RequiredArgsConstructor
public class MaintenanceRetroactiveImpactAnalysisApplicationService {

    private static final String FAILURE_CODE = "RETROACTIVE_IMPACT_SOURCE_FAILED";

    private final CommandGateway commandGateway;
    private final MaintenanceViewRepository maintenanceViewRepository;
    private final List<MaintenanceRetroactiveImpactSourcePort> sourcePorts;

    public MaintenanceRetroactiveImpactAnalysisResult analyze(MaintenanceRetroactiveImpactAnalysisInput input) {
        validate(input);
        MaintenanceView view = maintenanceViewRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        input.maintenanceId(), input.tenantId())
                .orElseThrow(MaintenanceNotFoundException::new);
        requireRetroactive(view);
        if (input.operationId().equals(view.getRetroactiveImpactOperationId())) {
            if (view.getRetroactiveImpactStatus() == MaintenanceRetroactiveImpactAnalysisStatus.COMPLETED
                    || view.getRetroactiveImpactStatus() == MaintenanceRetroactiveImpactAnalysisStatus.FAILED) {
                return existingResult(view);
            }
        }

        LocalDateTime scopeFrom = view.getSpecificEffectiveDate();
        LocalDateTime scopeTo = input.operationId().equals(view.getRetroactiveImpactOperationId())
                && view.getRetroactiveImpactScopeTo() != null
                        ? view.getRetroactiveImpactScopeTo() : LocalDateTime.now();
        String analysisId = input.operationId().equals(view.getRetroactiveImpactOperationId())
                && hasText(view.getRetroactiveImpactAnalysisId())
                        ? view.getRetroactiveImpactAnalysisId() : analysisId(input);
        int analysisVersion = input.operationId().equals(view.getRetroactiveImpactOperationId())
                && view.getRetroactiveImpactAnalysisVersion() != null
                        ? view.getRetroactiveImpactAnalysisVersion()
                        : currentVersion(view) + 1;
        ImpactRequest request = new ImpactRequest(
                input.tenantId(), input.maintenanceId(), view.getPolicyId(), scopeFrom, scopeTo);
        commandGateway.sendAndWait(new StartMaintenanceRetroactiveImpactAnalysisCommand(
                MaintenanceId.of(input.maintenanceId()), analysisId, input.operationId(), request.requestHash(),
                scopeFrom, scopeTo, LocalDateTime.now(), input.operatorId()));

        try {
            List<SourceEvidence> evidence = collect(request);
            List<MaintenanceRetroactiveImpactItem> items = evidence.stream()
                    .flatMap(source -> source.items().stream())
                    .sorted(Comparator
                            .comparing(MaintenanceRetroactiveImpactItem::severity).reversed()
                            .thenComparing(item -> item.sourceDomain().getCode())
                            .thenComparing(MaintenanceRetroactiveImpactItem::occurredAt)
                            .thenComparing(MaintenanceRetroactiveImpactItem::itemId))
                    .toList();
            List<MaintenanceRetroactiveImpactDomain> domains = evidence.stream()
                    .map(SourceEvidence::sourceDomain)
                    .sorted(Comparator.comparing(MaintenanceRetroactiveImpactDomain::getCode))
                    .toList();
            String evidenceVersion = evidenceVersion(evidence);
            String resultHash = MaintenanceRetroactiveImpactSourcePort.resultHash(evidence);
            LocalDateTime completedAt = LocalDateTime.now();
            commandGateway.sendAndWait(new CompleteMaintenanceRetroactiveImpactAnalysisCommand(
                    MaintenanceId.of(input.maintenanceId()), analysisId, input.operationId(), domains, items,
                    evidenceVersion, resultHash, completedAt, input.operatorId()));
            return completedResult(
                    analysisId, analysisVersion, input.operationId(), request, items, resultHash, completedAt);
        } catch (RuntimeException exception) {
            String message = safeMessage(exception);
            LocalDateTime failedAt = LocalDateTime.now();
            commandGateway.sendAndWait(new FailMaintenanceRetroactiveImpactAnalysisCommand(
                    MaintenanceId.of(input.maintenanceId()), analysisId, input.operationId(),
                    FAILURE_CODE, message, failedAt, input.operatorId()));
            return new MaintenanceRetroactiveImpactAnalysisResult(
                    analysisId, analysisVersion, input.operationId(),
                    MaintenanceRetroactiveImpactAnalysisStatus.FAILED, scopeFrom, scopeTo,
                    0, 0, 0, null, FAILURE_CODE, message, failedAt);
        }
    }

    private List<SourceEvidence> collect(ImpactRequest request) {
        Map<MaintenanceRetroactiveImpactDomain, MaintenanceRetroactiveImpactSourcePort> ports =
                new EnumMap<>(MaintenanceRetroactiveImpactDomain.class);
        for (MaintenanceRetroactiveImpactSourcePort port : sourcePorts) {
            if (port == null || ports.putIfAbsent(port.sourceDomain(), port) != null) {
                throw validation("sourcePorts", "权威取证适配器不能为空或重复");
            }
        }
        if (!ports.keySet().equals(Set.copyOf(MaintenanceRetroactiveImpactAnalysis.requiredDomains()))) {
            throw validation("sourcePorts", "Policy、Billing、Payment、Claim 四个权威取证适配器必须全部注册");
        }
        List<SourceEvidence> evidence = new ArrayList<>();
        for (MaintenanceRetroactiveImpactDomain domain : MaintenanceRetroactiveImpactAnalysis.requiredDomains()) {
            SourceEvidence source = ports.get(domain).collect(request);
            if (source == null || source.sourceDomain() != domain) {
                throw validation("sourceEvidence", "权威取证响应为空或归属域不匹配");
            }
            evidence.add(source);
        }
        return List.copyOf(evidence);
    }

    private String evidenceVersion(List<SourceEvidence> evidence) {
        return MaintenanceRetroactiveImpactSourcePort.itemHash(evidence.stream()
                .sorted(Comparator.comparing(source -> source.sourceDomain().getCode()))
                .map(source -> source.sourceDomain().getCode() + ':' + source.evidenceVersion())
                .toArray(String[]::new));
    }

    private MaintenanceRetroactiveImpactAnalysisResult completedResult(
            String analysisId,
            int analysisVersion,
            String operationId,
            ImpactRequest request,
            List<MaintenanceRetroactiveImpactItem> items,
            String resultHash,
            LocalDateTime completedAt) {
        return new MaintenanceRetroactiveImpactAnalysisResult(
                analysisId, analysisVersion, operationId, MaintenanceRetroactiveImpactAnalysisStatus.COMPLETED,
                request.scopeFrom(), request.scopeTo(), items.size(),
                (int) items.stream().filter(MaintenanceRetroactiveImpactItem::blocksEffect).count(),
                items.size(), resultHash, null, null, completedAt);
    }

    private MaintenanceRetroactiveImpactAnalysisResult existingResult(MaintenanceView view) {
        return new MaintenanceRetroactiveImpactAnalysisResult(
                view.getRetroactiveImpactAnalysisId(), currentVersion(view), view.getRetroactiveImpactOperationId(),
                view.getRetroactiveImpactStatus(), view.getRetroactiveImpactScopeFrom(),
                view.getRetroactiveImpactScopeTo(), view.getRetroactiveImpactItemCount(),
                view.getRetroactiveImpactBlockingCount(), view.getRetroactiveImpactPendingCount(),
                view.getRetroactiveImpactResultHash(), view.getRetroactiveImpactFailureCode(),
                view.getRetroactiveImpactFailureMessage(), view.getRetroactiveImpactCompletedAt());
    }

    private void requireRetroactive(MaintenanceView view) {
        if (view.getEffectiveTimeType() != EffectiveTimeType.RETROACTIVE
                || view.getSpecificEffectiveDate() == null || !hasText(view.getPolicyId())) {
            throw validation("case", "只有包含保单基准的追溯案件可以执行影响分析");
        }
    }

    private void validate(MaintenanceRetroactiveImpactAnalysisInput input) {
        if (input == null || !hasText(input.maintenanceId()) || !hasText(input.operationId())
                || !hasText(input.operatorId()) || !hasText(input.tenantId())) {
            throw validation("input", "案件、操作、操作员和租户标识不能为空");
        }
    }

    private int currentVersion(MaintenanceView view) {
        return view.getRetroactiveImpactAnalysisVersion() == null ? 0 : view.getRetroactiveImpactAnalysisVersion();
    }

    private String analysisId(MaintenanceRetroactiveImpactAnalysisInput input) {
        return "ria-" + MaintenanceRetroactiveImpactSourcePort
                .itemHash(input.tenantId(), input.maintenanceId(), input.operationId()).substring(0, 32);
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        String safe = message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
        return safe.length() <= 500 ? safe : safe.substring(0, 500);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private MaintenanceValidationException validation(String field, String message) {
        return new MaintenanceValidationException(
                "MaintenanceRetroactiveImpactAnalysisApplicationService", field, message);
    }
}
