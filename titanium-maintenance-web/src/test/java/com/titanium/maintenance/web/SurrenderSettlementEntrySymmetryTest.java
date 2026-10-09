package com.titanium.maintenance.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.titanium.common.context.RequestContext;
import com.titanium.common.context.RequestContextHolder;
import com.titanium.maintenance.api.MaintenanceApi;
import com.titanium.maintenance.api.request.SettleMaintenanceSurrenderRequest;
import com.titanium.maintenance.api.response.MaintenanceSurrenderSettlementResponse;
import com.titanium.maintenance.application.command.premium.MaintenancePremiumSettlementCommandService;
import com.titanium.maintenance.application.model.settlement.MaintenanceSurrenderSettlementInput;
import com.titanium.maintenance.application.model.settlement.MaintenanceSurrenderSettlementResult;
import com.titanium.maintenance.web.controller.MaintenanceController;
import com.titanium.maintenance.web.mapper.MaintenanceWebMapper;
import com.titanium.maintenance.web.provider.MaintenanceApiProvider;
import com.titanium.metadata.response.ApiResponse;

/**
 * G05/AC-08 退保结算「入口通道对称性」守卫。
 *
 * <p>
 * 同一能力同时存在 web 入口（{@code POST /web/v1/maintenances/{id}/surrender-settlements}）与 api 入口
 * （{@link MaintenanceApi#settleSurrender}，类基 {@code /api/v1/maintenances}）。二者必须**平行收敛**：
 * 各经映射器转出**同一个**入参、调用**同一个**应用层薄门面，且**互不依赖、不得互调**。
 * </p>
 *
 * <p>
 * 🔴 判据形态：不看「两边都调了哪个方法名」（那用源码 grep 就能假装），而是**真跑两个入口**，把交到门面的
 * **实参对象**录下来再断言 {@code equals} —— 入参是 record，相等即逐字段相等。任一侧的映射增删字段、
 * 改字段来源、或漏掉租户透传，都会当场打红。
 * </p>
 *
 * <p>
 * ⚠️ 存量说明：web 入口当前仍复用 api 层 {@link SettleMaintenanceSurrenderRequest} /
 * {@link MaintenanceSurrenderSettlementResponse}（§3.4.11 ① 的 {@code webLayerUsesDtoVoNotRequest}
 * 是全域迁移项、尚未对 maintenance 启用），故本测试**不**断言「web 不依赖 api 包」，只断言两侧不依赖
 * **对方的类**——那才是「不得互调」的机械承载点。存量状态已登记于 G05 文档 §六。
 * </p>
 */
class SurrenderSettlementEntrySymmetryTest {

    private static final String TENANT = "tenant-1";
    private static final String CASE_ID = "case-surrender-1";

    private final RecordingSettlementService settlementService = new RecordingSettlementService();
    private MaintenanceController controller;
    private MaintenanceApiProvider provider;

    @BeforeEach
    void setUp() {
        RequestContextHolder.set(RequestContext.ofTenant(TENANT));
        settlementService.reset();
        MaintenanceWebMapper mapper = Mappers.getMapper(MaintenanceWebMapper.class);
        // 首尾两参（应用服务 / 统计映射器）与本用例无关，本入口不触达
        controller = new MaintenanceController(null, settlementService, mapper, null);
        provider = new MaintenanceApiProvider(null, settlementService, mapper);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    /** 核心判据：两个入口必须把**逐字相同**的入参交给**同一个**门面方法。 */
    @Test
    void bothEntriesMustHandTheSameInputToTheSameFacadeMethod() {
        SettleMaintenanceSurrenderRequest request = request();

        ApiResponse<MaintenanceSurrenderSettlementResponse> viaApi = provider.settleSurrender(CASE_ID, request);
        ApiResponse<MaintenanceSurrenderSettlementResponse> viaWeb = controller.settleSurrender(CASE_ID, request);

        assertEquals(2, settlementService.inputs.size(), "两个入口各须调用门面 settleSurrender 恰一次");
        assertEquals(settlementService.inputs.get(0), settlementService.inputs.get(1),
                "两侧必须转出逐字相同的门面入参（record 相等 = 逐字段相等）；任一侧映射漂移即打红");
        assertEquals(List.of(CASE_ID, CASE_ID), settlementService.ids, "两侧必须把同一个案件标识原样交给门面");
        assertEquals(List.of(TENANT, TENANT), settlementService.tenants,
                "两侧必须各自从请求上下文取租户并透传，不得硬编码、不得漏传");
        assertNotNull(viaApi.getData(), "api 入口必须返回信封化数据");
        assertEquals(viaApi.getData(), viaWeb.getData(), "两侧出参由同一份门面结果映射而来，形状应逐字相同");
    }

    /**
     * 内容判据：两侧交出的入参都必须把请求的**每一个**字段原样承接。
     *
     * <p>
     * 🔴 为什么这条不能省：上面那条对称判据的两侧调的是**同一个** mapper 方法，故它对「mapper 自己漏映射
     * 某个字段」是免疫的——两侧同改则同变，注入后依然全绿（等价变换）。要钉住内容，必须把入参与**请求**
     * （本用例亲手构造的那一份）逐字段比对，而不是与「另一侧」比对。期望值一律现取自 {@code request}，
     * 不写字面量副本——否则契约一改，测量本身就过期了。
     * </p>
     */
    @Test
    void settlementInputMustCarryEveryRequestFieldVerbatim() {
        SettleMaintenanceSurrenderRequest request = request();

        provider.settleSurrender(CASE_ID, request);
        controller.settleSurrender(CASE_ID, request);

        assertEquals(2, settlementService.inputs.size(), "两个入口各须调用门面恰一次");
        for (MaintenanceSurrenderSettlementInput input : settlementService.inputs) {
            assertEquals(request.originalCalculationId(), input.originalCalculationId(), "退费计算标识必须承接");
            assertEquals(request.surrenderDate(), input.surrenderDate(), "退保日期必须承接");
            assertEquals(request.policyYear(), input.policyYear(), "保单年度必须承接");
            assertEquals(request.businessTime(), input.businessTime(), "业务时间必须承接");
            assertEquals(request.reason(), input.reason(), "退保原因必须承接");
            assertEquals(request.updatedBy(), input.updatedBy(), "操作人必须承接（审计字段）");
        }
    }

    /** 结构判据：方法级路径逐字相同、类基命名空间不同 —— 「同一能力的两个入口」的机械形态。 */
    @Test
    void bothEntriesShareTheSameMethodPathUnderDifferentNamespaces() throws NoSuchMethodException {
        PostMapping apiMethod = MaintenanceApi.class
                .getMethod("settleSurrender", String.class, SettleMaintenanceSurrenderRequest.class)
                .getAnnotation(PostMapping.class);
        PostMapping webMethod = MaintenanceController.class
                .getMethod("settleSurrender", String.class, SettleMaintenanceSurrenderRequest.class)
                .getAnnotation(PostMapping.class);

        assertNotNull(apiMethod, "api 契约缺失 settleSurrender ⇒ 出口不存在（AC-08 第一处落空）");
        assertNotNull(webMethod, "web 入口缺失 settleSurrender ⇒ 既有端点被改名或删除");
        assertEquals(1, apiMethod.value().length, "api 入口应恰有一条方法级路径");
        assertEquals(1, webMethod.value().length, "web 入口应恰有一条方法级路径");
        assertEquals("/{id}/surrender-settlements", apiMethod.value()[0], "api 入口的方法级路径不得篡改");
        assertEquals(apiMethod.value()[0], webMethod.value()[0],
                "两个入口的方法级路径必须逐字相同，否则「同一能力」不成立");
        assertEquals("/api/v1/maintenances", namespaceOf(MaintenanceApi.class), "api 入口类基");
        assertEquals("/web/v1/maintenances", namespaceOf(MaintenanceController.class), "web 入口类基");
    }

    /** 结构判据：两侧不依赖对方的类（「不得互调」的机械承载点）。 */
    @Test
    void entriesMustNotDependOnEachOther() {
        assertFalse(declaresFieldOfType(MaintenanceApiProvider.class, MaintenanceController.class),
                "api provider 不得持有 web controller —— 互调会绕过应用门面（§3.4.10）");
        assertFalse(declaresFieldOfType(MaintenanceController.class, MaintenanceApiProvider.class),
                "web controller 不得持有 api provider —— 同上");
    }

    /**
     * 类基命名空间：web 侧取 {@code @RequestMapping}，api 侧取 {@code @FeignClient} 的 {@code path}
     * （Feign 契约的类基写在注解属性上，两处取值口径不同，不可用同一段反射代码假装对称）。
     */
    private static String namespaceOf(Class<?> type) {
        if (type.isAnnotationPresent(FeignClient.class)) {
            String path = type.getAnnotation(FeignClient.class).path();
            assertFalse(path.isBlank(), type.getSimpleName() + " 的 @FeignClient.path 为空");
            return path;
        }
        RequestMapping mapping = type.getAnnotation(RequestMapping.class);
        assertNotNull(mapping, type.getSimpleName() + " 既无 @FeignClient.path 也无类级 @RequestMapping");
        assertEquals(1, mapping.value().length, "类基路径应恰有一条");
        return mapping.value()[0];
    }

    private static boolean declaresFieldOfType(Class<?> owner, Class<?> forbidden) {
        for (Field field : owner.getDeclaredFields()) {
            if (forbidden.isAssignableFrom(field.getType())) {
                return true;
            }
        }
        return false;
    }

    private static SettleMaintenanceSurrenderRequest request() {
        return new SettleMaintenanceSurrenderRequest(
                "calculation-1", LocalDate.parse("2026-09-30"), 3,
                LocalDateTime.parse("2026-09-30T10:15:00"), "客户申请退保", "operator-1");
    }

    /** 固定桩结果：只钉住两侧共用的那几个字段，足以驱动映射器（其余为 null）。 */
    private static MaintenanceSurrenderSettlementResult stubbedResult() {
        return new MaintenanceSurrenderSettlementResult(
                null, "P202608240001", "v3", "c".repeat(64), 3, 15,
                "CASH_VALUE_REFUND", true, new BigDecimal("0.35"),
                new BigDecimal("12000.00"), new BigDecimal("0.10"));
    }

    /**
     * 手写录制桩：本模块测试 classpath 只有 junit（无 mockito），故直接继承门面覆盖目标方法。
     * <p>
     * 覆盖的是 {@code settleSurrender} 而非父类委派的 orchestrator —— 判据要量的正是「两个入口交给
     * **门面**的实参」，量到 orchestrator 那一层会把门面自身的转换也算进来，归属就糊了。
     * </p>
     */
    private static final class RecordingSettlementService extends MaintenancePremiumSettlementCommandService {

        private final List<MaintenanceSurrenderSettlementInput> inputs = new ArrayList<>();
        private final List<String> ids = new ArrayList<>();
        private final List<String> tenants = new ArrayList<>();

        private RecordingSettlementService() {
            super(null);
        }

        private void reset() {
            inputs.clear();
            ids.clear();
            tenants.clear();
        }

        @Override
        public MaintenanceSurrenderSettlementResult settleSurrender(
                String maintenanceId, String tenantId, MaintenanceSurrenderSettlementInput input) {
            ids.add(maintenanceId);
            tenants.add(tenantId);
            inputs.add(input);
            return stubbedResult();
        }
    }
}
