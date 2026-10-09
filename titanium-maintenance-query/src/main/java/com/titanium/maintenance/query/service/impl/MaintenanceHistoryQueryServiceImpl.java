package com.titanium.maintenance.query.service.impl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.maintenance.query.query.FindMaintenanceHistoryQuery;
import com.titanium.maintenance.query.repository.MaintenanceFieldChangeViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.result.MaintenanceHistoryQueryResult;
import com.titanium.maintenance.query.result.MaintenanceHistoryQueryResult.FieldChangeHistory;
import com.titanium.maintenance.query.service.MaintenanceHistoryQueryService;
import com.titanium.maintenance.query.view.MaintenanceCaseItemView;
import com.titanium.maintenance.query.view.MaintenanceFieldChangeView;
import com.titanium.maintenance.query.view.MaintenanceView;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;

/**
 * 跨案保全历史查询实现（G05/AC-07）。
 *
 * <p>
 * 案件级条件（保单、保全项、费用方向、生效区间）以 {@link Specification} 下推到 SQL；字段级行按命中的
 * 案件**批量**取回后在内存分组，不做逐案查询（避免 N+1）。
 * </p>
 * <p>
 * 🔴 字段级行按 {@code maintenanceId} 批量取回，<b>不</b>依赖 g05-06 新增的
 * {@code policy_number} 冗余列——存量行该列为 {@code null}（可空列策略，见 DDL），按冗余列过滤会
 * 静默漏掉全部历史数据。冗余列的用户是「直接按保单号取字段行」与导出自描述，不是本查询的连接键。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class MaintenanceHistoryQueryServiceImpl implements MaintenanceHistoryQueryService {

    /** 生效区间两端统一 ISO-8601；显式加秒位——{@code LocalDateTime.toString()} 在秒为 0 时省略秒位，形态不稳定 */
    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final MaintenanceViewRepository maintenanceViewRepository;
    private final MaintenanceFieldChangeViewRepository fieldChangeViewRepository;

    @Override
    @Transactional(readOnly = true)
    public List<MaintenanceHistoryQueryResult> findHistory(FindMaintenanceHistoryQuery query) {
        List<MaintenanceView> cases = maintenanceViewRepository.findAll(
                caseSpecification(query),
                Sort.by(Sort.Direction.DESC, "createTime").and(Sort.by("maintenanceId")));
        if (cases.isEmpty()) {
            return List.of();
        }
        Map<String, List<MaintenanceFieldChangeView>> fields = fieldChangeViewRepository
                .findByTenantIdAndMaintenanceIdInOrderByMaintenanceIdAscItemCodeAscFieldCodeAscObjectIdAsc(
                        query.tenantId(),
                        cases.stream().map(MaintenanceView::getMaintenanceId).toList())
                .stream()
                .filter(field -> matchesItemCode(query, field))
                .collect(Collectors.groupingBy(
                        MaintenanceFieldChangeView::getMaintenanceId, LinkedHashMap::new, Collectors.toList()));
        return cases.stream()
                .map(view -> toResult(view, fields.getOrDefault(view.getMaintenanceId(), List.of())))
                .toList();
    }

    private Specification<MaintenanceView> caseSpecification(FindMaintenanceHistoryQuery query) {
        return (root, criteriaQuery, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("tenantId"), query.tenantId()));
            // 🔴 可见性开关：须与 /cases 列表 specification 及 /cases/{id} 详情派生方法名逐项一致，
            //    否则历史里查得到的案件，点进详情报「资源不存在」（R8-03 排查的题面症状）。
            predicates.add(builder.isTrue(root.get("independentCase")));
            predicates.add(builder.isTrue(root.get("initializationCompleted")));
            addEqual(predicates, builder, root.get("policyId"), query.policyId());
            addEqual(predicates, builder, root.get("policyNumber"), query.policyNumber());
            addEqual(predicates, builder, root.get("balanceDirection"), query.feeDirection());
            if (query.effectiveFrom() != null) {
                predicates.add(builder.greaterThanOrEqualTo(
                        root.get("specificEffectiveDate"), query.effectiveFrom()));
            }
            if (query.effectiveTo() != null) {
                predicates.add(builder.lessThan(root.get("specificEffectiveDate"), query.effectiveTo()));
            }
            if (hasText(query.itemCode())) {
                Subquery<String> itemSubquery = criteriaQuery.subquery(String.class);
                var item = itemSubquery.from(MaintenanceCaseItemView.class);
                itemSubquery.select(item.get("maintenanceId")).where(
                        builder.equal(item.get("tenantId"), query.tenantId()),
                        builder.equal(item.get("itemCode"), query.itemCode().trim()));
                predicates.add(root.get("maintenanceId").in(itemSubquery));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private MaintenanceHistoryQueryResult toResult(
            MaintenanceView view, List<MaintenanceFieldChangeView> fields) {
        return new MaintenanceHistoryQueryResult(
                view.getMaintenanceId(), view.getMaintenanceNo(), view.getPolicyId(), view.getPolicyNumber(),
                view.getMaintenanceType(), view.getStatus(), view.getEffectStatus(),
                format(view.getSpecificEffectiveDate()), view.getBusinessEffectiveAt(),
                view.getBalanceDirection(), soleEndorsementNo(fields), view.getCreateTime(),
                fields.stream().map(this::toFieldChange).toList());
    }

    private FieldChangeHistory toFieldChange(MaintenanceFieldChangeView field) {
        return new FieldChangeHistory(
                field.getItemCode(), field.getObjectId(), field.getFieldCode(), field.getLabelKey(),
                field.getDataType(), field.getBaseValue(), field.getCurrentValue(), field.getProposedValue(),
                field.getAppliedValue(), field.getConflictStatus(), field.getResolutionCode(),
                format(field.getSpecificEffectiveDate()), field.getBusinessEffectiveAt(),
                field.getFeeDirection(), field.getEndorsementNo(),
                field.getSensitivity(), field.getMaskingPolicy(), field.getChangeTypeCode());
    }

    private boolean matchesItemCode(FindMaintenanceHistoryQuery query, MaintenanceFieldChangeView field) {
        return !hasText(query.itemCode()) || query.itemCode().trim().equals(field.getItemCode());
    }

    /**
     * 案件级外部单证号 = 该案件字段级行上**唯一**出现的批单号。
     *
     * <p>
     * 🔴 批单号的实际粒度是「保全项 / Policy 生效回执」（一案多项各自拿回执），案件级的单个值仅在
     * 各行同号时成立。出现多个不同号说明案件被分次施加，此时**不静默择一**（与项目 CLAUDE.md §7.20
     * 「多账户歧义不静默择一」同判据）——案件级留空、由行级如实呈现；该案件是否已生效看
     * {@code effectStatus}，不靠本字段推断。
     * </p>
     */
    private String soleEndorsementNo(List<MaintenanceFieldChangeView> fields) {
        List<String> distinct = fields.stream()
                .map(MaintenanceFieldChangeView::getEndorsementNo)
                .filter(this::hasText)
                .distinct()
                .toList();
        return distinct.size() == 1 ? distinct.get(0) : null;
    }

    private String format(LocalDateTime value) {
        return value == null ? null : ISO_DATE_TIME.format(value);
    }

    private <T> void addEqual(
            List<Predicate> predicates, CriteriaBuilder builder, Path<T> path, T value) {
        if (value instanceof String text) {
            if (hasText(text)) {
                predicates.add(builder.equal(path, text.trim()));
            }
        } else if (value != null) {
            predicates.add(builder.equal(path, value));
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
