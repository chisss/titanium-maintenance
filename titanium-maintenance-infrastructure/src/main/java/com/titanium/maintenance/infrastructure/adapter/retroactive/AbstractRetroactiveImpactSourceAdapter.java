package com.titanium.maintenance.infrastructure.adapter.retroactive;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.port.maintenance.MaintenanceRetroactiveImpactSourcePort;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveImpactItem;
import com.titanium.metadata.response.ApiResponse;

/** 四域追溯取证适配器的响应校验与证据版本模板。 */
abstract class AbstractRetroactiveImpactSourceAdapter<T> implements MaintenanceRetroactiveImpactSourcePort {

    @Override
    public final SourceEvidence collect(ImpactRequest request) {
        ApiResponse<List<T>> response = query(request);
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw invalid("response", sourceDomain().getCode() + "权威查询未返回成功的结构化响应");
        }
        List<MaintenanceRetroactiveImpactItem> items = response.getData().stream()
                .map(item -> Objects.requireNonNull(item, "权威查询不能返回空元素"))
                .flatMap(item -> toItems(request, item).stream())
                .filter(item -> inScope(request, item.occurredAt()))
                .sorted(Comparator.comparing(MaintenanceRetroactiveImpactItem::itemId))
                .toList();
        String evidenceVersion = MaintenanceRetroactiveImpactSourcePort.itemHash(
                sourceDomain().getCode(), request.requestHash(),
                String.join(",", items.stream().map(MaintenanceRetroactiveImpactItem::evidenceHash).toList()));
        return new SourceEvidence(sourceDomain(), evidenceVersion, items);
    }

    protected abstract ApiResponse<List<T>> query(ImpactRequest request);

    protected abstract List<MaintenanceRetroactiveImpactItem> toItems(ImpactRequest request, T source);

    protected final void requirePolicy(ImpactRequest request, String policyId) {
        if (!Objects.equals(request.policyId(), policyId)) {
            throw invalid("policyId", sourceDomain().getCode() + "权威响应保单回显不一致");
        }
    }

    protected final void requireTenant(ImpactRequest request, String tenantId) {
        if (!Objects.equals(request.tenantId(), tenantId)) {
            throw invalid("tenantId", sourceDomain().getCode() + "权威响应租户回显不一致");
        }
    }

    protected final String requireText(String field, String value) {
        if (value == null || value.isBlank()) {
            throw invalid(field, sourceDomain().getCode() + "权威响应字段不能为空");
        }
        return value.trim();
    }

    protected final String value(Object value) {
        return value == null ? "" : value.toString();
    }

    private boolean inScope(ImpactRequest request, LocalDateTime occurredAt) {
        return occurredAt != null && occurredAt.isAfter(request.scopeFrom())
                && !occurredAt.isAfter(request.scopeTo());
    }

    private MaintenanceValidationException invalid(String field, String message) {
        return new MaintenanceValidationException(getClass().getSimpleName(), field, message);
    }
}
