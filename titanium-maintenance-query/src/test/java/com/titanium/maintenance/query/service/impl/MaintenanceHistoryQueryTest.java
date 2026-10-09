package com.titanium.maintenance.query.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.common.enums.change.MaintenanceFieldConflictStatus;
import com.titanium.maintenance.common.enums.change.PolicyFieldDataType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceEffectStatus;
import com.titanium.maintenance.query.query.FindMaintenanceHistoryQuery;
import com.titanium.maintenance.query.query.MaintenanceCaseSearchCriteria;
import com.titanium.maintenance.query.repository.MaintenanceCaseItemViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceFieldChangeViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceRetroactiveImpactItemViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceRetroactivePeriodAdjustmentViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceSnapshotViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceWorkflowTaskViewRepository;
import com.titanium.maintenance.query.result.MaintenanceHistoryQueryResult;
import com.titanium.maintenance.query.view.MaintenanceFieldChangeView;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.metadata.enums.maintenance.MaintenanceType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;

/**
 * G05/AC-07 跨案保全历史查询测试。
 *
 * <p>
 * 判据：
 * <ol>
 *   <li><b>计数下界</b>：同一保单 ≥2 个案件时返回 ≥2 条（字段级跨案历史一次取回）；</li>
 *   <li><b>三要素</b>：生效区间（起止）+ 费用方向 + 外部单证号在每个案件上齐备；</li>
 *   <li><b>多值不择一</b>：案件内出现多个不同批单号时案件级留空（不取第一个），行级如实呈现；</li>
 *   <li><b>可见性对称</b>：历史查询的可见性开关与 {@code /cases} 列表**逐项一致**——否则会出现
 *       「历史里查得到的案件，点进详情报资源不存在」（R8-03 的题面症状）；两侧均**真跑**各自的
 *       Specification 取真值再互比，不比对写死清单（写死清单会把两侧一致改动误判成漂移）。</li>
 * </ol>
 * </p>
 */
class MaintenanceHistoryQueryTest {

    private static final String TENANT = "tenant-1";
    private static final String POLICY_NUMBER = "P202608240001";
    private static final LocalDateTime EFFECTIVE_START = LocalDateTime.parse("2026-08-24T10:00:00");

    private MaintenanceViewRepository maintenanceViewRepository;
    private MaintenanceFieldChangeViewRepository fieldChangeViewRepository;
    private MaintenanceHistoryQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        maintenanceViewRepository = mock(MaintenanceViewRepository.class);
        fieldChangeViewRepository = mock(MaintenanceFieldChangeViewRepository.class);
        service = new MaintenanceHistoryQueryServiceImpl(
                maintenanceViewRepository, fieldChangeViewRepository);
    }

    @Test
    void shouldReturnEveryCaseOfSamePolicyWithItsOwnFieldChanges() {
        when(maintenanceViewRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(caseView("case-1", "MNT202608240000001", "END-1"),
                        caseView("case-2", "MNT202608240000002", "END-2")));
        when(fieldChangeViewRepository
                .findByTenantIdAndMaintenanceIdInOrderByMaintenanceIdAscItemCodeAscFieldCodeAscObjectIdAsc(
                        anyString(), anyList()))
                .thenReturn(List.of(fieldView("case-1", "POLICY_INFO_CHANGE", "holderName", "END-1"),
                        fieldView("case-2", "POLICY_INFO_CHANGE", "holderMobile", "END-2")));

        List<MaintenanceHistoryQueryResult> history = service.findHistory(
                new FindMaintenanceHistoryQuery(TENANT, null, POLICY_NUMBER, null, null, null, null));

        assertEquals(2, history.size(), "同一保单 ≥2 个案件时必须返回 ≥2 条（AC-07 计数下界）");
        MaintenanceHistoryQueryResult first = history.getFirst();
        assertEquals("case-1", first.maintenanceId());
        assertEquals(POLICY_NUMBER, first.policyNumber());
        assertEquals("2026-08-24T10:00:00", first.effectiveStart(), "生效区间起必须以 ISO-8601 秒位齐全的形态对外");
        assertEquals("2026-08-24T02:00:00+00:00", first.effectiveEnd(), "生效区间止原样承接业务生效时点（含偏移）");
        assertEquals(MaintenanceBalanceDirection.CREDIT, first.feeDirection(), "费用方向为三要素之一");
        assertEquals("END-1", first.endorsementNo(), "外部单证号为三要素之一");
        assertEquals(List.of("holderName"),
                first.fieldChanges().stream().map(MaintenanceHistoryQueryResult.FieldChangeHistory::fieldCode).toList(),
                "字段级行必须按案件归属，不得串案");
        assertEquals(List.of("holderMobile"),
                history.get(1).fieldChanges().stream()
                        .map(MaintenanceHistoryQueryResult.FieldChangeHistory::fieldCode).toList());
    }

    @Test
    void shouldReturnEmptyHistoryWithoutTouchingFieldRowsWhenPolicyHasNoCase() {
        when(maintenanceViewRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of());

        List<MaintenanceHistoryQueryResult> history = service.findHistory(
                new FindMaintenanceHistoryQuery(TENANT, "policy-1", null, null, null, null, null));

        assertTrue(history.isEmpty(), "无命中案件时返回空列表（本查询语义是集合，不返回 null）");
        verifyNoInteractions(fieldChangeViewRepository);
    }

    @Test
    void shouldLeaveCaseLevelEndorsementBlankWhenCaseCarriesSeveral() {
        when(maintenanceViewRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(caseView("case-1", "MNT202608240000001", null)));
        when(fieldChangeViewRepository
                .findByTenantIdAndMaintenanceIdInOrderByMaintenanceIdAscItemCodeAscFieldCodeAscObjectIdAsc(
                        anyString(), anyList()))
                .thenReturn(List.of(fieldView("case-1", "POLICY_INFO_CHANGE", "holderName", "END-1"),
                        fieldView("case-1", "COVERAGE_AMOUNT_CHANGE", "sumInsured", "END-2")));

        MaintenanceHistoryQueryResult history = service.findHistory(
                new FindMaintenanceHistoryQuery(TENANT, null, POLICY_NUMBER, null, null, null, null)).getFirst();

        assertNull(history.endorsementNo(),
                "一案多项各自拿回执 ⇒ 案件级不存在唯一批单号，不得静默择一（项目 CLAUDE.md §7.20）");
        assertEquals(List.of("END-1", "END-2"),
                history.fieldChanges().stream()
                        .map(MaintenanceHistoryQueryResult.FieldChangeHistory::endorsementNo).toList(),
                "行级批单号必须原样保留，供展示与对账消歧");
    }

    @Test
    void shouldNarrowBothCaseAndFieldRowsByItemCode() {
        when(maintenanceViewRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(caseView("case-1", "MNT202608240000001", "END-1")));
        when(fieldChangeViewRepository
                .findByTenantIdAndMaintenanceIdInOrderByMaintenanceIdAscItemCodeAscFieldCodeAscObjectIdAsc(
                        anyString(), anyList()))
                .thenReturn(List.of(fieldView("case-1", "POLICY_INFO_CHANGE", "holderName", "END-1"),
                        fieldView("case-1", "COVERAGE_AMOUNT_CHANGE", "sumInsured", "END-1")));

        MaintenanceHistoryQueryResult history = service.findHistory(new FindMaintenanceHistoryQuery(
                TENANT, null, POLICY_NUMBER, "POLICY_INFO_CHANGE", null, null, null)).getFirst();

        assertEquals(1, history.fieldChanges().size(), "保全项收窄必须同时作用于字段级行");
        assertEquals("holderName", history.fieldChanges().getFirst().fieldCode());
    }

    /**
     * 历史查询与案件列表的可见性开关必须逐项一致。
     *
     * <p>
     * 两侧均真跑各自的 {@code Specification}、用 mock 的 {@link CriteriaBuilder} 捕获下发的
     * {@code isTrue} 字段名再互比：只比对写死清单会把「两侧一起改」误判成漂移，且证明不了出货物里
     * 确实有那两项（与 {@code MaintenanceCaseVisibilitySymmetryTest} 同法）。
     * </p>
     */
    @Test
    void historyAndCaseListMustShareTheSameVisibilitySwitch() {
        Set<String> historySide = visibilityFields(historySpecification());
        Set<String> listSide = visibilityFields(caseListSpecification());

        assertTrue(listSide.size() >= 2,
                "列表侧可见性条件不足 2 项（实际 " + listSide + "）——捕获口径或列表实现已变，本用例需同步");
        assertEquals(listSide, historySide,
                "历史查询的可见性开关必须与 /cases 列表逐项一致（实际：" + historySide + " vs " + listSide + "）——"
                        + "仅改一侧 ⇒ 历史里查得到的案件点进详情报「资源不存在」");
    }

    @SuppressWarnings("unchecked")
    private Specification<MaintenanceView> historySpecification() {
        AtomicReference<Specification<MaintenanceView>> captured = new AtomicReference<>();
        when(maintenanceViewRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenAnswer(invocation -> {
                    captured.set(invocation.getArgument(0));
                    return List.of();
                });
        service.findHistory(new FindMaintenanceHistoryQuery(TENANT, null, POLICY_NUMBER, null, null, null, null));
        Specification<MaintenanceView> specification = captured.get();
        assertTrue(specification != null, "历史查询未按 Specification 取案件——守卫捕获口径已失效，请同步更新本用例");
        return specification;
    }

    @SuppressWarnings("unchecked")
    private Specification<MaintenanceView> caseListSpecification() {
        MaintenanceViewRepository repository = mock(MaintenanceViewRepository.class);
        AtomicReference<Specification<MaintenanceView>> captured = new AtomicReference<>();
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    captured.set(invocation.getArgument(0));
                    return new org.springframework.data.domain.PageImpl<MaintenanceView>(List.of());
                });
        new MaintenanceCaseQueryServiceImpl(repository,
                mock(MaintenanceCaseItemViewRepository.class),
                mock(MaintenanceFieldChangeViewRepository.class),
                mock(MaintenanceSnapshotViewRepository.class),
                mock(MaintenanceWorkflowTaskViewRepository.class),
                mock(MaintenanceRetroactiveImpactItemViewRepository.class),
                mock(MaintenanceRetroactivePeriodAdjustmentViewRepository.class))
                .search(TENANT, new MaintenanceCaseSearchCriteria(
                        null, null, null, null, null, null, null, null, null, 0, 20));
        Specification<MaintenanceView> specification = captured.get();
        assertTrue(specification != null, "案件列表未按 Specification 分页——守卫捕获口径已失效，请同步更新本用例");
        return specification;
    }

    /** 真跑 Specification，收集它下发的 {@code isTrue} 谓词所作用的字段名。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Set<String> visibilityFields(Specification<MaintenanceView> specification) {
        Set<String> fields = new LinkedHashSet<>();
        Map<String, Path<?>> pathsByField = new HashMap<>();
        Root<MaintenanceView> root = mock(Root.class, invocation ->
                "get".equals(invocation.getMethod().getName())
                        ? pathsByField.computeIfAbsent(invocation.getArgument(0), field -> mock(Path.class))
                        : Answers.RETURNS_DEFAULTS.answer(invocation));
        CriteriaBuilder builder = mock(CriteriaBuilder.class, invocation -> {
            if ("isTrue".equals(invocation.getMethod().getName())) {
                Path<?> argument = invocation.getArgument(0);
                pathsByField.entrySet().stream()
                        .filter(entry -> entry.getValue() == argument)
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .ifPresent(fields::add);
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });

        specification.toPredicate(root, mock(CriteriaQuery.class), builder);
        assertTrue(!fields.isEmpty(), "未捕获到任何 isTrue 谓词——守卫捕获口径已失效，请同步更新本用例");
        return fields;
    }

    private MaintenanceView caseView(String maintenanceId, String maintenanceNo, String endorsementNo) {
        MaintenanceView view = new MaintenanceView();
        view.setMaintenanceId(maintenanceId);
        view.setMaintenanceNo(maintenanceNo);
        view.setPolicyId("policy-1");
        view.setPolicyNumber(POLICY_NUMBER);
        view.setMaintenanceType(MaintenanceType.POLICY_INFO_CHANGE);
        view.setStatus(MaintenanceStatus.COMPLETED);
        view.setEffectStatus(MaintenanceEffectStatus.APPLIED);
        view.setEffectiveTimeType(EffectiveTimeType.SPECIFIED_DATE);
        view.setSpecificEffectiveDate(EFFECTIVE_START);
        view.setBusinessEffectiveAt("2026-08-24T02:00:00+00:00");
        view.setBalanceDirection(MaintenanceBalanceDirection.CREDIT);
        view.setTenantId(TENANT);
        view.setCreateTime(EFFECTIVE_START);
        view.setUpdateTime(EFFECTIVE_START);
        return view;
    }

    private MaintenanceFieldChangeView fieldView(
            String maintenanceId, String itemCode, String fieldCode, String endorsementNo) {
        MaintenanceFieldChangeView view = new MaintenanceFieldChangeView();
        view.setFieldChangeId(maintenanceId + "-" + itemCode + "-" + fieldCode);
        view.setMaintenanceId(maintenanceId);
        view.setItemCode(itemCode);
        view.setObjectId("object-1");
        view.setFieldCode(fieldCode);
        view.setLabelKey("policy.field." + fieldCode);
        view.setDataType(PolicyFieldDataType.TEXT);
        view.setBaseValue("旧值");
        view.setProposedValue("新值");
        view.setAppliedValue("新值");
        view.setConflictStatus(MaintenanceFieldConflictStatus.NONE);
        view.setSensitivity(PolicyFieldSensitivityLevel.INTERNAL);
        view.setMaskingPolicy(PolicyFieldMaskingPolicy.NONE);
        view.setPolicyId("policy-1");
        view.setPolicyNumber(POLICY_NUMBER);
        view.setEndorsementNo(endorsementNo);
        view.setSpecificEffectiveDate(EFFECTIVE_START);
        view.setBusinessEffectiveAt("2026-08-24T02:00:00+00:00");
        view.setFeeDirection(MaintenanceBalanceDirection.CREDIT);
        view.setTenantId(TENANT);
        view.setCreateTime(EFFECTIVE_START);
        view.setUpdateTime(EFFECTIVE_START);
        return view;
    }
}
