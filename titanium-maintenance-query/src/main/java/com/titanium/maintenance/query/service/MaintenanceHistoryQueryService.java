package com.titanium.maintenance.query.service;

import java.util.List;

import com.titanium.maintenance.query.query.FindMaintenanceHistoryQuery;
import com.titanium.maintenance.query.result.MaintenanceHistoryQueryResult;

/**
 * 跨案保全历史查询服务（CQRS 读侧，G05/AC-07）。
 *
 * <p>
 * 复杂条件以 JPA {@code Specification} 内聚在本层（项目 CLAUDE.md §3.4.9 ④：application 读入口只表达
 * 「要查什么」，不承载查询实现细节）。返回稳定 DTO 契约
 * （{@link MaintenanceHistoryQueryResult}），禁止直接返回读模型实体。
 * </p>
 * <p>
 * <b>派发方式</b>：与同域 {@code MaintenanceCaseQueryService} 一致，由 application 读门面**直连本接口**，
 * 不经 Axon QueryBus——本域全仓无 {@code QueryGateway} 使用点，{@code handler/query} 下
 * {@code MaintenanceQueryHandler} 的三个 {@code @QueryHandler} 是历史遗留、零派发点。新增读路径沿用
 * 直连范式，勿再新增无人派发的 {@code @QueryHandler}（那只会得到「注册了但永远不被调用」的等价死代码）。
 * </p>
 */
public interface MaintenanceHistoryQueryService {

    /**
     * 按保单检索跨案保全历史（案件级 + 字段级）。
     *
     * @param query 查询条件（保单为检索主键）
     * @return 命中的案件历史，按创建时间倒序（同一秒创建以案件ID为第二排序键，顺序确定）
     */
    List<MaintenanceHistoryQueryResult> findHistory(FindMaintenanceHistoryQuery query);
}
