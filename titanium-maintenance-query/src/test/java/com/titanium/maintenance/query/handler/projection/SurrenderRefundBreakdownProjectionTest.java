package com.titanium.maintenance.query.handler.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.titanium.maintenance.event.MaintenancePremiumCalculatedEvent;
import com.titanium.maintenance.event.MaintenanceSurrenderValueRecordedEvent;
import com.titanium.maintenance.port.product.ProductSurrenderValuePort.SurrenderFactorFact;
import com.titanium.maintenance.query.mapper.MaintenanceViewMapper;
import com.titanium.maintenance.query.repository.MaintenanceSurrenderFactorViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.view.MaintenanceSurrenderFactorView;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.valueobject.MaintenanceId;

/**
 * G05/AC-06 退费明细冻结读模型投影测试。
 *
 * <p>
 * 判据（对应 AC-06「列存在 + 值非空 + 加总一致」）：
 * <ol>
 *   <li><b>落库可查</b>：事件的每个因子 → 一行读模型，序号为原始顺序（不重排、不合并）；</li>
 *   <li><b>加总一致</b>：从**落库行**复算的 {@code ΣCREDIT − ΣDEBIT} 等于权威净额，且与案件读模型
 *       的 {@code refund_amount} 相等（两个投影各自写入，本断言是两份产物之间的一致性对照）；</li>
 *   <li><b>策略版本/内容哈希非空</b>：沿用 {@code MaintenanceView:453-460} 冻结范式；</li>
 *   <li><b>重投影幂等</b>：先删后插，重放不产生重复行且行ID稳定；</li>
 *   <li><b>历史事件兼容</b>：{@code factors == null}（分量引入前的事件）与空列表同义，不抛异常。</li>
 * </ol>
 * </p>
 */
class SurrenderRefundBreakdownProjectionTest {

    private static final MaintenanceId ID = MaintenanceId.of("case-surrender-1");
    private static final String TENANT = "tenant-1";
    private static final LocalDateTime RECORDED_AT = LocalDateTime.parse("2026-09-30T10:15:00");

    /** 权威净额：由 g05 需求钉死的示例口径 —— 三笔 CREDIT 减三笔 DEBIT。 */
    private static final BigDecimal AUTHORITATIVE_NET = new BigDecimal("102795.50000000");

    private MaintenanceSurrenderFactorViewRepository factorRepository;
    private MaintenanceViewRepository caseRepository;
    private MaintenanceSurrenderFactorProjectionEventHandler factorHandler;
    private MaintenanceProjectionEventHandler caseHandler;

    /**
     * 逐批记录投影落库的行 + 逐条记录对仓储的操作序列。
     *
     * <p>
     * 🔴 用 {@code thenAnswer} 收集而非 {@code ArgumentCaptor} + {@code verify}：{@code verify} 会把命中的
     * 调用标记为「已核验」，同一次测试里再做 {@code InOrder} 校验时只剩未核验的调用——重复投影用例
     * 需要两轮落库都算数，verify 型捕获会让第二轮被吃掉（实测症状：{@code Wanted 2 times but was 1}）。
     * </p>
     * <p>
     * 🔴 交错顺序**不用 {@code InOrder} 断言**：{@code InOrder} 逐条核验时会跨轮配到尚未核验的同类调用
     * （实测：把处理器改成「先插后删」，{@code inOrder.verify(delete); verify(save); verify(delete);
     * verify(save)} 四条断言仍全绿 ⇒ 零承重）。改为在这里记录操作序列、再用**序列精确相等**断言，
     * 判据只依赖被测代码真实调用的先后，不依赖 mock 框架的核验游标语义。
     * </p>
     */
    private final List<List<MaintenanceSurrenderFactorView>> savedBatches = new ArrayList<>();

    /** 对因子仓储的操作序列，元素为 {@code DELETE} / {@code SAVE}。 */
    private final List<String> operations = new ArrayList<>();

    @BeforeEach
    void setUp() {
        savedBatches.clear();
        operations.clear();
        factorRepository = mock(MaintenanceSurrenderFactorViewRepository.class);
        caseRepository = mock(MaintenanceViewRepository.class);
        doAnswer(invocation -> {
            operations.add("DELETE");
            return null;
        }).when(factorRepository).deleteByTenantIdAndMaintenanceId(any(), any());
        when(factorRepository.saveAll(any())).thenAnswer(invocation -> {
            operations.add("SAVE");
            savedBatches.add(new ArrayList<>(invocation.getArgument(0)));
            return invocation.getArgument(0);
        });
        factorHandler = new MaintenanceSurrenderFactorProjectionEventHandler(factorRepository);
        caseHandler = new MaintenanceProjectionEventHandler(
                caseRepository, Mappers.getMapper(MaintenanceViewMapper.class));
    }

    @Test
    void shouldFreezeEveryFactorIntoSeparateRowInOriginalOrder() {
        factorHandler.on(surrenderEvent(sixFactorBreakdown()));

        List<MaintenanceSurrenderFactorView> rows = savedRows();
        assertEquals(6, rows.size(), "六因子必须逐行落库，既不合并也不丢项");
        for (int order = 0; order < rows.size(); order++) {
            MaintenanceSurrenderFactorView row = rows.get(order);
            assertEquals(order, row.getFactorOrder().intValue(), "因子序号必须保持对端回执的原始顺序");
            assertEquals(ID.id(), row.getMaintenanceId());
            assertEquals(TENANT, row.getTenantId());
            assertEquals(RECORDED_AT, row.getCreateTime());
            assertEquals(RECORDED_AT, row.getUpdateTime());
            assertNotNull(row.getFactorType(), "因子类型码必须落库（防腐透传，未知码也照存）");
            assertNotNull(row.getDirection(), "金额方向码必须落库");
        }
        assertEquals(List.of("CASH_VALUE", "UNEARNED_PREMIUM", "DIVIDEND_ACCOUNT_VALUE",
                        "LOAN_PRINCIPAL_INTEREST", "OVERDUE_PREMIUM", "HANDLING_FEE"),
                rows.stream().map(MaintenanceSurrenderFactorView::getFactorType).toList());
        assertEquals(0, rows.getFirst().getAmount().compareTo(new BigDecimal("120000.00")),
                "因子金额必须原样落库（不与权威口径重算）");
        assertEquals("surrender.factor.cashValue", rows.getFirst().getExplainKey());
    }

    @Test
    void shouldReproduceAuthoritativeNetRefundFromPersistedRows() {
        factorHandler.on(surrenderEvent(sixFactorBreakdown()));
        MaintenanceView caseView = new MaintenanceView();
        when(caseRepository.findByMaintenanceIdAndTenantId(ID.id(), TENANT))
                .thenReturn(Optional.of(caseView));

        // 与 factorHandler 同源驱动：净额由 Product 域下发，案件读模型的退费额取自权威结果
        caseHandler.on(new MaintenancePremiumCalculatedEvent(
                ID, BigDecimal.ZERO, AUTHORITATIVE_NET, "退保价值策略计算", RECORDED_AT, "maintenance-service", TENANT));

        BigDecimal recomputed = savedRows().stream()
                .map(row -> "DEBIT".equals(row.getDirection())
                        ? row.getAmount().negate() : row.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, recomputed.compareTo(AUTHORITATIVE_NET), "ΣCREDIT − ΣDEBIT 必须还原权威净额");
        assertEquals(0, caseView.getRefundAmount().compareTo(AUTHORITATIVE_NET),
                "案件读模型 refund_amount 必须与因子明细复算的净额一致（两份产物对账）");
    }

    @Test
    void shouldKeepSurrenderStrategyVersionAndContentHashOnCaseView() {
        MaintenanceView caseView = new MaintenanceView();
        when(caseRepository.findByMaintenanceIdAndTenantId(ID.id(), TENANT))
                .thenReturn(Optional.of(caseView));

        caseHandler.on(surrenderEvent(sixFactorBreakdown()));

        assertEquals("SURRENDER_STD_2026", caseView.getSurrenderPolicyCode());
        assertEquals("v3", caseView.getSurrenderPolicyVersion());
        assertEquals("c".repeat(64), caseView.getSurrenderPolicyContentHash());
        assertEquals(3, caseView.getSurrenderPolicyYear().intValue());
        assertEquals("CASH_VALUE_REFUND", caseView.getSurrenderRefundType());
        assertFalse(caseView.getWithinCoolingOff(), "犹豫期标志必须随策略证据落库");
    }

    @Test
    void shouldRebuildRowsIdempotentlyOnReplay() {
        MaintenanceSurrenderValueRecordedEvent event = surrenderEvent(sixFactorBreakdown());

        factorHandler.on(event);
        List<String> firstIds = savedRows().stream()
                .map(MaintenanceSurrenderFactorView::getFactorViewId).toList();
        factorHandler.on(event);
        List<String> secondIds = savedRows().stream()
                .map(MaintenanceSurrenderFactorView::getFactorViewId).toList();

        assertEquals(firstIds, secondIds, "同一事件重放的行ID必须稳定，否则重投影会留下重复行");
        // 逐轮断言「先删后插」的交错顺序——若第二轮的 delete 与第一轮的 saveAll 顺序颠倒（同一批上先插后删），
        // 重投影就会把刚写的行删光。序列精确相等，不依赖 mock 框架的核验游标语义。
        assertEquals(List.of("DELETE", "SAVE", "DELETE", "SAVE"), operations,
                "每轮投影必须各是「先删后插」；顺序颠倒会把刚写的行删光");
    }

    @Test
    void shouldTreatNullFactorsAsEmptyBreakdownWithoutFailing() {
        factorHandler.on(surrenderEvent(null));

        assertTrue(savedRows().isEmpty(),
                "分量引入前的历史事件 factors=null 必须按「无明细」处理，不得整批失败");
        verify(factorRepository).deleteByTenantIdAndMaintenanceId(TENANT, ID.id());
    }

    /** 最近一批落库的因子行。 */
    private List<MaintenanceSurrenderFactorView> savedRows() {
        assertFalse(savedBatches.isEmpty(), "投影未落库任何批次");
        return List.copyOf(savedBatches.getLast());
    }

    private MaintenanceSurrenderValueRecordedEvent surrenderEvent(List<SurrenderFactorFact> factors) {
        return new MaintenanceSurrenderValueRecordedEvent(
                ID, "adj-1", "SURRENDER_STD_2026", "v3", "c".repeat(64), 3, 15,
                "CASH_VALUE_REFUND", false, new BigDecimal("0.35"),
                BigDecimal.ZERO, new BigDecimal("0.10"), RECORDED_AT, "maintenance-service", TENANT, factors);
    }

    /**
     * 六因子口径：三笔 CREDIT（现金价值/未满期保费/红利账户价值）减三笔 DEBIT（贷款本息/欠费/手续费）
     * = 124300.50 − 21505.00 = 102795.50。
     */
    private List<SurrenderFactorFact> sixFactorBreakdown() {
        return List.of(
                new SurrenderFactorFact("CASH_VALUE", "CREDIT",
                        new BigDecimal("120000.00"), "surrender.factor.cashValue"),
                new SurrenderFactorFact("UNEARNED_PREMIUM", "CREDIT",
                        new BigDecimal("3500.50"), "surrender.factor.unearnedPremium"),
                new SurrenderFactorFact("DIVIDEND_ACCOUNT_VALUE", "CREDIT",
                        new BigDecimal("800.00"), "surrender.factor.dividendAccount"),
                new SurrenderFactorFact("LOAN_PRINCIPAL_INTEREST", "DEBIT",
                        new BigDecimal("20000.00"), "surrender.factor.loanPrincipalInterest"),
                new SurrenderFactorFact("OVERDUE_PREMIUM", "DEBIT",
                        new BigDecimal("1200.00"), "surrender.factor.overduePremium"),
                new SurrenderFactorFact("HANDLING_FEE", "DEBIT",
                        new BigDecimal("305.00"), "surrender.factor.handlingFee"));
    }
}
