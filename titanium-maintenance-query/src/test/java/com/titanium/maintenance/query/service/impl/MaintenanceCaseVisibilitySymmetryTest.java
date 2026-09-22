package com.titanium.maintenance.query.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.titanium.maintenance.query.query.MaintenanceCaseSearchCriteria;
import com.titanium.maintenance.query.repository.MaintenanceCaseItemViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceFieldChangeViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceRetroactiveImpactItemViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceRetroactivePeriodAdjustmentViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceSnapshotViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceWorkflowTaskViewRepository;
import com.titanium.maintenance.query.view.MaintenanceView;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;

/**
 * 保全案件的**列表可见性**与**详情可见性**必须逐项对称。
 *
 * <p>两侧条件漂移的症状是「列表里看得到的案件，点开详情报『资源不存在』」（R8-03 排查的题面），
 * 而这个漂移**全程静默**：
 * <ul>
 *   <li>列表侧条件写在 {@code specification(...)} 里，增删谓词**不破坏编译**；</li>
 *   <li>详情侧条件写在 Spring Data 派生查询方法名里，改它必须同改调用点，**编译期即拦住**——
 *       所以真正会逃逸的漂移方向是「只动列表侧」；</li>
 *   <li>既有用例（{@code MaintenanceCaseQueryServiceImplTest}）各自 stub 掉 repository 方法，
 *       两侧都不真正执行对方那一半，**漂移后它们全部保持绿色**。</li>
 * </ul>
 *
 * <p>🔴 本用例**不以写死的字段清单为真源**，而是两侧各取一次真值再互比：列表侧真跑
 * {@code search} 用 mock 的 {@link CriteriaBuilder} 捕获它下发的 {@code isTrue} 谓词字段名；
 * 详情侧真跑 {@code findDetail} 从**被调用的那个 repository 方法**取方法名再解析其中的
 * {@code True} 段。这样「两侧一起改」不会被误判成漂移（写死清单的用例会把一致改动也判红，
 * 而它同时**证明不了**出货物里确实有那两项——见用户级 lessons「断言里引用了内联副本」）。
 */
class MaintenanceCaseVisibilitySymmetryTest {

    private static final String TRUE_SUFFIX = "True";

    /**
     * 可见性条件数下限。**不是**抄自任何清单，而是「双回写通道按案件代数互斥」这组隔离条件的最小完备形态：
     * {@code independentCase}（独立建案）与 {@code initializationCompleted}（建案初始化完成），
     * 见 {@code MaintenanceViewRepository} 详情方法名与域 CLAUDE.md §7.11。
     *
     * <p>下限存在的必要：详情侧有一条**已存在**的降级目标
     * {@code findByMaintenanceIdAndTenantId(...)}（不带任何可见性条件），把调用点改成它**照常编译**，
     * 并且会让既有用例里的 stub 失配（Mockito 默认返回空 {@code Optional}，而那一条断言的正是
     * {@code isEmpty()}）⇒ 整套断言静默通过。此时仅有「两侧互比」也发现不了（两侧 = 空集，相等）。
     */
    private static final int MIN_VISIBILITY_CONDITIONS = 2;

    @Test
    void listAndDetailVisibilityConditionsMustStaySymmetric() {
        Set<String> listSide = listVisibilityFields();
        String detailQueryMethod = detailLookupMethodName();
        Set<String> detailSide = visibilityFieldsOfDerivedQuery(detailQueryMethod);

        assertTrue(listSide.size() >= MIN_VISIBILITY_CONDITIONS,
                "列表可见性条件不足 " + MIN_VISIBILITY_CONDITIONS + " 项（实际 " + listSide + "）——"
                        + "独立案件与建案完成这两条隔离条件一旦缺失，非独立/未完成的历史案件会进入独立案件列表");
        assertEquals(detailSide, listSide,
                "列表 specification 的可见性条件与详情端 " + detailQueryMethod + " 的 True 段必须逐项一致（实际："
                        + listSide + " vs " + detailSide + "）——仅改一侧 ⇒ 列表里点得到的案件详情报「资源不存在」");
    }

    /**
     * 真跑 {@code search} 的 {@code specification}，捕获它下发的 {@code isTrue} 谓词所作用字段名。
     *
     * <p>口径是 {@code isTrue} 而非 {@code equal}：{@code specification} 里 {@code equal} 同时承担
     * 租户等值与业务过滤（单号/客户/来源/状态…），只有可见性开关用的是 {@code isTrue}。
     * 若将来把某条可见性条件改写成 {@code equal(root.get(x), Boolean.TRUE)}，本捕获口径需同步扩展。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Set<String> listVisibilityFields() {
        MaintenanceViewRepository repository = mock(MaintenanceViewRepository.class);
        AtomicReference<Specification<MaintenanceView>> captured = new AtomicReference<>();
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    captured.set(invocation.getArgument(0));
                    return new PageImpl<>(List.of());
                });
        serviceWith(repository).search("tenant-1", emptyCriteria());

        Specification<MaintenanceView> specification = captured.get();
        assertNotNull(specification, "search 未按 specification 分页——守卫捕获口径已失效，请同步更新本用例");

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

    /**
     * 真跑 {@code findDetail}，取**被调用的那个 repository 方法**的方法名。
     * 不写死方法名：写死即等于内联副本，两侧一致改名会被误判成漂移。
     */
    private String detailLookupMethodName() {
        AtomicReference<String> queryMethod = new AtomicReference<>();
        MaintenanceViewRepository repository = mock(MaintenanceViewRepository.class, invocation -> {
            if (Optional.class.equals(invocation.getMethod().getReturnType())) {
                queryMethod.set(invocation.getMethod().getName());
                return Optional.empty();
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        serviceWith(repository).findDetail("tenant-1", "case-1");

        String method = queryMethod.get();
        assertNotNull(method, "findDetail 未按单条查询读维护视图——守卫捕获口径已失效，请同步更新本用例");
        return method;
    }

    /**
     * 从 Spring Data 派生查询方法名里取出 {@code True} 段并还原为字段名。
     *
     * <p>{@code …AndIndependentCaseTrueAndInitializationCompletedTrue} →
     * {@code independentCase} / {@code initializationCompleted}。不以 {@code True} 收尾的段
     * （主键、租户、来源等等值条件）自然被排除，故**无需**先剥 {@code findBy} 前缀。
     * 字段名自身含 {@code And} 时会被误切——这是派生查询的固有限制，Spring Data 亦按同一规则解析。
     */
    private static Set<String> visibilityFieldsOfDerivedQuery(String queryMethod) {
        return Stream.of(queryMethod.split("And"))
                .filter(segment -> segment.endsWith(TRUE_SUFFIX))
                .map(segment -> segment.substring(0, segment.length() - TRUE_SUFFIX.length()))
                .map(segment -> Character.toLowerCase(segment.charAt(0)) + segment.substring(1))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private MaintenanceCaseQueryServiceImpl serviceWith(MaintenanceViewRepository repository) {
        return new MaintenanceCaseQueryServiceImpl(
                repository,
                mock(MaintenanceCaseItemViewRepository.class),
                mock(MaintenanceFieldChangeViewRepository.class),
                mock(MaintenanceSnapshotViewRepository.class),
                mock(MaintenanceWorkflowTaskViewRepository.class),
                mock(MaintenanceRetroactiveImpactItemViewRepository.class),
                mock(MaintenanceRetroactivePeriodAdjustmentViewRepository.class));
    }

    /** 九个业务过滤条件全空 ⇒ 谓词只剩租户等值与两条可见性开关。 */
    private MaintenanceCaseSearchCriteria emptyCriteria() {
        return new MaintenanceCaseSearchCriteria(
                null, null, null, null, null, null, null, null, null, 0, 20);
    }
}
