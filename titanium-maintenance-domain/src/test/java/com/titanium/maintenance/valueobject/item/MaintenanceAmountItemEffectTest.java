package com.titanium.maintenance.valueobject.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.titanium.maintenance.common.enums.config.MaintenanceEffectPayloadKind;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

/**
 * AC-07 金额型保全项生效通道的目录级守卫。
 * <p>
 * 缺口 G04 的实质缺陷是「金额型案件被当成字段型案件生效」——生效环节按字段回写路径处理，无字段可写即静默走过。
 * 本类把「哪些项走哪条通道」固化为可机械断言的事实，并守住三条容易漂移的边界：
 * </p>
 * <ol>
 *   <li><b>路由不回落</b>：7 类金额型保全项一律不得回落 {@link MaintenanceEffectPayloadKind#FIELD}；</li>
 *   <li><b>目录完整</b>：任一 {@link MaintenanceType} 都必须能在旧主类型映射中解析（新增类型漏映射即失败）；</li>
 *   <li><b>资金事实前置</b>：4 类走本域金额通道的项必须同时在聚合的资金结算名单内——漏登记会让生效时
 *       「差额是否已登记」这一闸口形同虚设。</li>
 * </ol>
 */
class MaintenanceAmountItemEffectTest {

    /** AC-07 中经本域金额通道生效的 4 类（差额作为并列生效载荷递给保单域）。 */
    private static final Set<MaintenanceType> AMOUNT_CHANNEL_ITEMS = EnumSet.of(
            MaintenanceType.ADDITIONAL_PAYMENT,
            MaintenanceType.REDUCTION_PAYMENT,
            MaintenanceType.TOP_UP,
            MaintenanceType.PARTIAL_WITHDRAWAL);

    /** AC-07 中另有专用跨域出口的 3 类（贷款放款/贷款还款/减额缴清），不经本域金额通道。 */
    private static final Set<MaintenanceType> DEDICATED_EXIT_ITEMS = EnumSet.of(
            MaintenanceType.POLICY_LOAN,
            MaintenanceType.LOAN_REPAYMENT,
            MaintenanceType.REDUCED_PAID_UP);

    @ParameterizedTest
    @EnumSource(value = MaintenanceType.class, names = {
            "ADDITIONAL_PAYMENT", "REDUCTION_PAYMENT", "TOP_UP", "PARTIAL_WITHDRAWAL"})
    void shouldRouteAmountItemToAmountChannel(MaintenanceType type) {
        MaintenanceItemCode code = MaintenanceItemCode.of(type.getCode());

        assertEquals(MaintenanceEffectPayloadKind.AMOUNT, code.effectPayloadKind(),
                "金额型保全项不得回落到字段通道: " + type);
        assertTrue(code.amountEffect(), "金额型保全项必须声明金额载荷: " + type);
        assertEquals(type, code.legacyMaintenanceType(), "保全项编码与主类型必须双向一致");
    }

    @ParameterizedTest
    @EnumSource(value = MaintenanceType.class, names = {
            "POLICY_LOAN", "LOAN_REPAYMENT", "REDUCED_PAID_UP"})
    void shouldRouteDedicatedExitItemToItsOwnChannel(MaintenanceType type) {
        MaintenanceItemCode code = MaintenanceItemCode.of(type.getCode());

        assertEquals(MaintenanceEffectPayloadKind.DEDICATED_EXIT, code.effectPayloadKind(),
                "专用出口项不得回落到字段通道: " + type);
        assertFalse(code.amountEffect(), "专用出口项的载荷另有出处，不得混入金额通道: " + type);
    }

    /** 未登记的普通字段型保全项保持既有行为：回落字段通道（目录只声明例外，不改默认语义）。 */
    @Test
    void shouldFallBackToFieldChannelForUnregisteredItem() {
        MaintenanceItemCode code = MaintenanceItemCode.of(MaintenanceType.POLICY_HOLDER_CHANGE.getCode());

        assertEquals(MaintenanceEffectPayloadKind.FIELD, code.effectPayloadKind());
        assertFalse(code.amountEffect());
        assertFalse(MaintenanceItemCode.amountTypes().contains(MaintenanceType.POLICY_HOLDER_CHANGE));
        assertFalse(MaintenanceItemCode.dedicatedExitTypes().contains(MaintenanceType.POLICY_HOLDER_CHANGE));
    }

    /** 两条通道互斥：一项既走金额载荷又走专用出口，会让生效环节把同一笔变更施加两次。 */
    @Test
    void shouldKeepTheTwoChannelsDisjoint() {
        Set<MaintenanceType> amountTypes = MaintenanceItemCode.amountTypes();
        Set<MaintenanceType> exitTypes = MaintenanceItemCode.dedicatedExitTypes();

        assertFalse(amountTypes.isEmpty(), "金额通道目录不得为空（空集会让本类断言恒真）");
        assertFalse(exitTypes.isEmpty(), "专用出口目录不得为空");
        assertTrue(Collections.disjoint(amountTypes, exitTypes),
                "同一保全项不得同时登记为金额型与专用出口型");
        assertTrue(amountTypes.containsAll(AMOUNT_CHANNEL_ITEMS), "AC-07 的 4 类金额项必须在金额通道目录内");
        assertTrue(exitTypes.containsAll(DEDICATED_EXIT_ITEMS), "AC-07 的 3 类专用出口项必须在专用出口目录内");
    }

    /** 目录键空间完整：任一主类型都必须能解析出旧主类型映射，解析不出即说明新增类型漏登记。 */
    @Test
    void shouldResolveLegacyTypeForEveryMaintenanceType() {
        List<MaintenanceType> resolved = List.of(MaintenanceType.values()).stream()
                .filter(type -> MaintenanceItemCode.of(type.getCode()).legacyMaintenanceType() == type)
                .toList();

        assertEquals(MaintenanceType.values().length, resolved.size(),
                "存在无法解析旧主类型的保全项编码，新增类型须同批登记");
        assertTrue(resolved.size() > 0, "解析结果不得为空（空集会让上一条断言恒真）");
    }

    /**
     * 资金事实闸口的对齐守卫：金额通道的 4 类必须同时在聚合的「需资金结算」名单内。
     * <p>
     * 🔴 两侧各取真值再互比——一侧取自目录常量，一侧从聚合源码里取出 switch 分支实参；
     * <b>不</b>在断言里内联一份抄写的清单（抄写件与实现同时漂移时判据会静默失效）。
     * </p>
     */
    @Test
    void shouldCoverAmountItemsByPremiumSettlementList() throws IOException {
        Set<String> settled = premiumSettlementCases();

        assertFalse(settled.isEmpty(), "未从聚合源码解析出任何结算名单分支（解析器失效会让本判据恒真）");
        for (MaintenanceType type : MaintenanceItemCode.amountTypes()) {
            assertTrue(settled.contains(type.name()),
                    "金额型保全项须同时在资金结算名单内，否则「差额是否已登记」的闸口对它不生效: " + type);
        }
    }

    /** 从聚合源码的 {@code requiresPremiumSettlement()} 方法体里取出 switch 分支实参（到 default 分支为止）。 */
    private static Set<String> premiumSettlementCases() throws IOException {
        Path source = Path.of("src/main/java/com/titanium/maintenance/aggregate/Maintenance.java");
        String text = Files.readString(source, StandardCharsets.UTF_8);
        int start = text.indexOf("private boolean requiresPremiumSettlement()");
        assertTrue(start > 0, "未找到 requiresPremiumSettlement()（方法改名后须同步本判据）");
        int end = text.indexOf("default ->", start);
        assertTrue(end > start, "未找到 requiresPremiumSettlement() 的 switch 分支");
        Set<String> knownNames = new HashSet<>();
        for (MaintenanceType type : MaintenanceType.values()) {
            knownNames.add(type.name());
        }
        Set<String> resolved = new HashSet<>();
        Matcher matcher = Pattern.compile("\\b([A-Z][A-Z0-9_]{3,})\\b").matcher(text.substring(start, end));
        while (matcher.find()) {
            if (knownNames.contains(matcher.group(1))) {
                resolved.add(matcher.group(1));
            }
        }
        return resolved;
    }
}
