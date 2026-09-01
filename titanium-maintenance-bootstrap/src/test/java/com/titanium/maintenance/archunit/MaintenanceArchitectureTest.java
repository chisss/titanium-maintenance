package com.titanium.maintenance.archunit;


import org.junit.jupiter.api.Test;

import com.titanium.buildtools.archunit.AbstractArchitectureGuardTest;

/**
 * 保全域架构守护测试：继承共享基类，仅提供本域根包。
 * 全部 DDD 分层/命名/依赖注入规则由 {@link AbstractArchitectureGuardTest} 提供，
 * 规则一处维护、各域复用。
 * <p>
 * 保全域 5 个命令类已完成 record 化改造（含首字段 {@code @TargetAggregateIdentifier} 路由标识），
 * 「命令必须为 record」规则随基类默认启用，本子类不再覆盖禁用。
 * </p>
 * <p>
 * 保全域 api/web 两层已按《API层与Web层职责边界及协作规范》整改：契约由 {@code MaintenanceApiClient}
 * 重命名为 {@code MaintenanceApi}（命名主键=聚合根 Maintenance）；契约实现下沉 web/provider 的
 * {@code MaintenanceApiProvider}；{@code MaintenanceController} 改路径 {@code /web/v1/maintenances}
 * 且不再 implements 契约；application 门面不再依赖 api DTO（读出参改用应用层读模型 {@code MaintenanceReadModel}）。
 * 故覆盖启用以下 4 条基类默认 {@code @Disabled} 的 api/web 边界断言。
 * </p>
 * <p>
 * 注：不启用 {@code webShouldNotDependOnDomainCommandsOrAggregates}——现行 api/web 规范允许 web 依赖
 * domain command/query（主流 Axon/CQRS 做法），仅禁止碰 aggregate；该严格隔离断言与本方案冲突，保持基类默认禁用。
 * </p>
 */
class MaintenanceArchitectureTest extends AbstractArchitectureGuardTest {

    @Override
    protected String basePackage() {
        return "com.titanium.maintenance";
    }

    /**
     * 启用「application 层不得依赖 api 的 DTO」。
     * <p>
     * 保全域读门面出参已由 api 的 {@code MaintenanceResponse} 改为应用层读模型 {@code MaintenanceReadModel}，
     * DTO→Command/Response 的翻译在 web 层完成，application 门面不再依赖 api 契约。
     * </p>
     */
    @Test
    @Override
    protected void applicationMustNotDependOnApiDto() {
        super.applicationMustNotDependOnApiDto();
    }

    /**
     * 启用「api 层使用 Request/Response 而非 DTO」（2026-07-19 命名新规）。
     * <p>
     * 保全域 api 层入参 {@code CreateMaintenanceRequest}/{@code ChangeMaintenanceStatusRequest} 落 {@code api.request}，
     * 出参 {@code MaintenanceResponse}/{@code MaintenanceStatisticsResponse} 落 {@code api.response}，api 层无 DTO 后缀类型。
     * </p>
     */
    @Test
    @Override
    protected void apiLayerUsesRequestResponseNotDto() {
        super.apiLayerUsesRequestResponseNotDto();
    }

    /**
     * 启用「web 层使用 DTO/VO 而非 Request/Response」（2026-07-19 命名新规）。
     * <p>
     * 保全域 web 层前端入参已改名 {@code CreateMaintenanceDTO}/{@code ChangeMaintenanceStatusDTO} 落 {@code web.dto}，
     * 出参 {@code MaintenanceVO} 用 VO，web 层无 Request/Response 后缀类型。
     * </p>
     */
    @Test
    @Override
    protected void webLayerUsesDtoVoNotRequest() {
        super.webLayerUsesDtoVoNotRequest();
    }

    /**
     * 启用「API 契约实现（Provider）须位于 web.provider 且以 Provider 结尾」。
     * <p>
     * 保全域契约实现为 {@code MaintenanceApiProvider}，落在 web/provider。
     * </p>
     */
    @Test
    @Override
    protected void apiContractImplMustResideInProviderPackage() {
        super.apiContractImplMustResideInProviderPackage();
    }

    /**
     * 启用「Controller 不得实现 api 契约接口」。
     * <p>
     * {@code MaintenanceController} 已去掉 {@code implements MaintenanceApi}，契约实现下沉 Provider。
     * </p>
     */
    @Test
    @Override
    protected void controllerMustNotImplementApi() {
        super.controllerMustNotImplementApi();
    }

    /**
     * 启用「api 层 Feign 契约接口须以 Api 结尾（命名主键为聚合根）」。
     * <p>
     * 契约已由 {@code MaintenanceApiClient} 重命名为 {@code MaintenanceApi}。
     * </p>
     */
    @Test
    @Override
    protected void apiInterfacesMustBeNamedByAggregate() {
        super.apiInterfacesMustBeNamedByAggregate();
    }


    /**
     * 启用「port 包顶层不得平铺类」（2026-09 包结构分包规范）。
     * <p>
     * 远程 Port 已按对端域拆子包（billing/customer/maintenance/payment/policy/product/tenant），顶层清零。
     * </p>
     */
    @Test
    @Override
    protected void portShouldNotContainFlatClasses() {
        super.portShouldNotContainFlatClasses();
    }

    /**
     * 启用「infrastructure.adapter 包顶层不得平铺类」（2026-09 包结构分包规范）。
     * <p>
     * Adapter 已按对端域拆子包（billing/customer/maintenance.config|lease/payment/policy/product/retroactive/underwriting），顶层清零。
     * </p>
     */
    @Test
    @Override
    protected void adapterShouldNotContainFlatClasses() {
        super.adapterShouldNotContainFlatClasses();
    }

    /**
     * 启用「infrastructure.client 包顶层不得平铺类」（2026-09 包结构分包规范）。
     * <p>
     * Feign Client 已按对端域拆子包（billing/claim/customer/insurance/payment/policy/product），顶层清零。
     * </p>
     */
    @Test
    @Override
    protected void clientShouldNotContainFlatClasses() {
        super.clientShouldNotContainFlatClasses();
    }

    /**
     * 启用「web.dto 包顶层不得平铺类」（2026-09 批次 2 包结构分包规范）。
     * <p>
     * 前端入参 DTO 已按业务主题拆子包（casecreation/configuration/effect/field/premium/retroactive/withdrawal），顶层清零。
     * </p>
     */
    @Test
    @Override
    protected void webDtoShouldNotContainFlatClasses() {
        super.webDtoShouldNotContainFlatClasses();
    }

    /**
     * 启用「web.response 包顶层不得平铺类」（2026-09 批次 2 包结构分包规范）。
     * <p>
     * 前端出参 VO 已按业务主题拆子包（casecreation/configuration/effect/premium/retroactive/field/withdrawal/underwriting/error），顶层清零。
     * </p>
     */
    @Test
    @Override
    protected void webResponseShouldNotContainFlatClasses() {
        super.webResponseShouldNotContainFlatClasses();
    }

    /**
     * 启用「application.command 包顶层不得平铺类」（2026-09 批次 2 包结构分包规范）。
     * <p>
     * 命令门面与入参已按业务主题拆子包（casecreation/configuration/effect/field/premium/retroactive/underwriting/withdrawal），顶层清零。
     * </p>
     */
    @Test
    @Override
    protected void applicationCommandShouldNotContainFlatClasses() {
        super.applicationCommandShouldNotContainFlatClasses();
    }

    /**
     * 启用「application.model 包顶层不得平铺类」（2026-09 批次 2 包结构分包规范）。
     * <p>
     * 应用层结果模型已按业务主题拆子包（casecreation/effect/field/premium/retroactive/settlement/underwriting/withdrawal，configuration 已于批次 1 拆分），顶层清零。
     * </p>
     */
    @Test
    @Override
    protected void applicationModelShouldNotContainFlatClasses() {
        super.applicationModelShouldNotContainFlatClasses();
    }

}
