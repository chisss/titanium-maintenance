package com.titanium.maintenance.aggregate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.maintenance.command.AddMaintenanceChangeCommand;
import com.titanium.maintenance.command.ChangeMaintenanceStatusCommand;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.MaintenanceChangeType;
import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.common.exception.MaintenanceNotFoundException;
import com.titanium.maintenance.event.MaintenanceCreatedEvent;
import com.titanium.maintenance.valueobject.CustomerId;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.PolicyId;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

/**
 * 跨租户写入拒绝守护测试。
 * <p>
 * <b>威胁模型</b>：Axon 按聚合标识装载、事件流不按租户隔离；命令携带的 {@code tenantId} 来自调用方请求头
 * （{@code X-Tenant-Id}），完全可被伪造。若聚合不校验，持他租户的保全案件标识即可跨租户推进状态、
 * 追加变更项乃至触发资金结算——属不可逆的跨租户写入。
 * </p>
 * <p>
 * 本测试以两条互补的判据守护：
 * </p>
 * <ol>
 * <li><b>行为判据</b>：代表性命令以错租户或空租户派发时，聚合必须拒绝且<b>不产生任何事件</b>；
 *     同租户命令不得被误伤。</li>
 * <li><b>结构判据</b>：扫描聚合根源码，每个 {@code @CommandHandler} 命令方法的<b>首条语句</b>必须是
 *     {@code requireSameTenant(command.tenantId());}——新增处理器漏写守护即失败，无需为每条命令各写一个夹具。</li>
 * </ol>
 * <p>
 * 两类处理器不适用「首条语句」判据，但均<b>显式计数</b>以防静默跳过：
 * </p>
 * <ul>
 * <li><b>创建构造器</b>（{@code Maintenance(CreateMaintenanceCommand)}）：创建时尚无「聚合既有租户」可比对，
 *     其租户由创建事件的 {@code tenantId} 落库，后续命令回放该事件后即受守护。</li>
 * <li><b>幂等建案处理器</b>（{@code handle(CreateMaintenanceCaseCommand)}）：同一方法兼作创建路径
 *     （{@code CREATE_IF_MISSING}，此时无既有租户可比对）与同幂等键重试路径，故守卫置于重试分支内、
 *     不在首条语句；本测试改为断言该方法体<b>确实包含</b>守护语句——豁免的是位置，不是存在性。</li>
 * </ul>
 */
class CrossTenantCommandGuardTest {

    private static final String MAINTENANCE_ID = "mt-ct-001";
    private static final String TENANT_ID      = "tenant-1";
    /** 非本聚合归属的租户 */
    private static final String OTHER_TENANT_ID = "tenant-2";

    /** 聚合根源码文件（相对模块根，surefire 以模块目录为工作目录） */
    private static final List<String> AGGREGATE_SOURCES = List.of("Maintenance.java");

    private static final String GUARD_STATEMENT = "requireSameTenant(command.tenantId());";

    /** 兼作创建路径、守卫置于重试分支内的处理器签名片段 */
    private static final String IDEMPOTENT_CREATION_SIGNATURE = "(CreateMaintenanceCaseCommand";

    private FixtureConfiguration<Maintenance> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Maintenance.class);
        // 事件回放含 LocalDateTime.now() 等非确定性赋值，关闭非法状态变更检测以聚焦租户守护
        fixture.setReportIllegalStateChange(false);
    }

    // ==================== 行为判据：代表性命令的跨租户拒绝 ====================

    @Test
    @DisplayName("状态推进：错租户被拒且不产生事件")
    void shouldRejectStatusChangeFromOtherTenant() {
        assertCrossTenantRejected(new ChangeMaintenanceStatusCommand(
                MaintenanceId.of(MAINTENANCE_ID), MaintenanceStatus.PROCESSING, "开始处理", "op-1", OTHER_TENANT_ID));
    }

    @Test
    @DisplayName("变更项追加：错租户被拒且不产生事件")
    void shouldRejectChangeAdditionFromOtherTenant() {
        assertCrossTenantRejected(new AddMaintenanceChangeCommand(
                MaintenanceId.of(MAINTENANCE_ID), MaintenanceChangeType.MODIFY,
                "holder.name", "张三", "李四", "op-1", OTHER_TENANT_ID));
    }

    @Test
    @DisplayName("空租户亦被拒（失败关闭，不因缺租户而降级放行）")
    void shouldRejectBlankTenant() {
        assertCrossTenantRejected(new ChangeMaintenanceStatusCommand(
                MaintenanceId.of(MAINTENANCE_ID), MaintenanceStatus.PROCESSING, "开始处理", "op-1", null));
        assertCrossTenantRejected(new ChangeMaintenanceStatusCommand(
                MaintenanceId.of(MAINTENANCE_ID), MaintenanceStatus.PROCESSING, "开始处理", "op-1", "   "));
    }

    @Test
    @DisplayName("同租户不受影响（守护不得误伤正常路径）")
    void shouldStillAcceptSameTenant() {
        fixture.given(createdEvent())
                .when(new ChangeMaintenanceStatusCommand(
                        MaintenanceId.of(MAINTENANCE_ID), MaintenanceStatus.PROCESSING, "开始处理", "op-1", TENANT_ID))
                .expectSuccessfulHandlerExecution();
    }

    private void assertCrossTenantRejected(Object command) {
        fixture.given(createdEvent())
                .when(command)
                .expectException(MaintenanceNotFoundException.class)
                .expectNoEvents();
    }

    // ==================== 结构判据：全处理器覆盖 ====================

    @Test
    @DisplayName("聚合根的每个 @CommandHandler 首条语句必须是跨租户守护")
    void everyCommandHandlerMustGuardTenantFirst() throws IOException {
        Path aggregateDir = resolveAggregateSourceDir();
        List<String> violations = new ArrayList<>();
        int inspected = 0;
        int constructors = 0;
        int deferredGuards = 0;
        int handlers = 0;

        for (String fileName : AGGREGATE_SOURCES) {
            Path file = aggregateDir.resolve(fileName);
            assertTrue(Files.isRegularFile(file), "聚合根源码不存在: " + file);
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                if (!"@CommandHandler".equals(lines.get(i).trim())) {
                    continue;
                }
                handlers++;
                int signatureEnd = i + 1;
                while (signatureEnd < lines.size() && !lines.get(signatureEnd).trim().endsWith(") {")) {
                    signatureEnd++;
                }
                // 拼接完整签名：@CommandHandler 与签名行之间可能夹着 @CreationPolicy 等注解
                StringBuilder signatureBuilder = new StringBuilder();
                for (int line = i + 1; line <= signatureEnd; line++) {
                    signatureBuilder.append(lines.get(line).trim());
                }
                String signatureLine = signatureBuilder.toString();
                if (isCreationConstructor(signatureLine, fileName)) {
                    // 创建路径尚无「聚合既有租户」可比对，其租户由创建事件的 tenantId 落库，
                    // 后续命令回放该事件后即受守护——故构造器豁免首条判据（断言总数以防静默跳过）
                    constructors++;
                    continue;
                }
                if (signatureLine.contains(IDEMPOTENT_CREATION_SIGNATURE)) {
                    // 兼作创建路径，守卫只能在重试分支内——豁免的是位置，不是存在性
                    assertTrue(methodBodyContains(lines, signatureEnd + 1, GUARD_STATEMENT),
                            fileName + ":" + (i + 1) + " 幂等建案处理器内未找到守护语句「" + GUARD_STATEMENT + "」");
                    deferredGuards++;
                    continue;
                }
                String firstStatement = firstStatementAfter(lines, signatureEnd + 1);
                inspected++;
                if (!GUARD_STATEMENT.equals(firstStatement)) {
                    violations.add(fileName + ":" + (signatureEnd + 1) + " 首条语句为「" + firstStatement
                            + "」，应为「" + GUARD_STATEMENT + "」");
                }
            }
        }

        assertEquals(List.of(), violations,
                "以下 @CommandHandler 未把跨租户守护放在首条语句：" + String.join("；", violations));
        // 守护一旦失效（例如结构变更导致一个都没扫到）本测试必须失败，而非静默通过
        assertEquals(51, inspected, "首条守护的 @CommandHandler 数量异常，守护规则可能已失效");
        assertEquals(1, constructors, "扫描到的创建构造器数量异常（Maintenance 1）");
        assertEquals(1, deferredGuards, "扫描到的幂等建案处理器数量异常（Maintenance 1）");
        assertEquals(53, handlers, "扫描到的 @CommandHandler 总数异常（Maintenance 53）");
    }

    /** 判定该 {@code @CommandHandler} 是否标注在创建构造器上（方法名即聚合类名） */
    private boolean isCreationConstructor(String signatureLine, String fileName) {
        String typeName = fileName.substring(0, fileName.length() - ".java".length());
        return signatureLine.startsWith("public " + typeName + "(");
    }

    /** 自方法体起、至下一个方法注解为止，是否出现指定语句（用于豁免位置的处理器） */
    private boolean methodBodyContains(List<String> lines, int from, String statement) {
        for (int i = from; i < lines.size(); i++) {
            String raw = lines.get(i);
            if (raw.startsWith("    @")) {
                return false;
            }
            if (statement.equals(raw.trim())) {
                return true;
            }
        }
        return false;
    }

    /** 返回首条非空、非注释语句（去缩进） */
    private String firstStatementAfter(List<String> lines, int from) {
        for (int i = from; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) {
                continue;
            }
            return trimmed;
        }
        return fail("未找到方法体首条语句（自第 " + (from + 1) + " 行起）");
    }

    /** 自模块目录向上定位含聚合根源码的目录（surefire 与 IDE 的工作目录可能不同） */
    private Path resolveAggregateSourceDir() {
        String relative = "src/main/java/com/titanium/maintenance/aggregate";
        Path current = Paths.get("").toAbsolutePath();
        for (int depth = 0; depth < 6 && current != null; depth++) {
            Path candidate = current.resolve(relative);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("未能定位聚合根源码目录，当前目录: " + Paths.get("").toAbsolutePath());
    }

    // ==================== 夹具 ====================

    private MaintenanceCreatedEvent createdEvent() {
        return new MaintenanceCreatedEvent(
                MaintenanceId.of(MAINTENANCE_ID), PolicyId.of("policy-1"), CustomerId.of("customer-1"),
                MaintenanceType.POLICY_INFO_CHANGE, EffectiveTimeType.IMMEDIATE,
                null, "跨租户守护测试案件", LocalDateTime.now(), "operator-1", TENANT_ID);
    }
}
