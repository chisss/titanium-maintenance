package com.titanium.maintenance.query.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.query.view.MaintenanceView;

/**
 * 保全案件读模型仓储
 * <p>
 * CQRS 查询侧仓储，访问读模型表 {@code t_maintenance_view}。租户隔离经 {@code tenantId} 条件下推。
 * </p>
 */
@Repository
public interface MaintenanceViewRepository
        extends JpaRepository<MaintenanceView, String>, JpaSpecificationExecutor<MaintenanceView> {

    Optional<MaintenanceView> findByMaintenanceId(String maintenanceId);

    Optional<MaintenanceView> findByMaintenanceIdAndTenantId(String maintenanceId, String tenantId);

    Optional<MaintenanceView> findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
            String maintenanceId, String tenantId);

    Optional<MaintenanceView> findByTenantIdAndSourceAndClientRequestKeyAndIndependentCaseTrue(
            String tenantId, MaintenanceChannel source, String clientRequestKey);

    List<MaintenanceView> findByPolicyId(String policyId);

    List<MaintenanceView> findByPolicyIdAndTenantId(String policyId, String tenantId);

    List<MaintenanceView> findByCustomerId(String customerId);

    List<MaintenanceView> findByCustomerIdAndTenantId(String customerId, String tenantId);

    List<MaintenanceView> findByStatus(MaintenanceStatus status);

    List<MaintenanceView> findByTenantIdAndStatus(String tenantId, MaintenanceStatus status);

    List<MaintenanceView> findByTenantIdOrderByCreateTimeDesc(String tenantId);

    List<MaintenanceView> findByPolicyIdAndStatusIn(String policyId, List<MaintenanceStatus> statuses);

    List<MaintenanceView> findByPolicyIdAndTenantIdAndStatusIn(
            String policyId, String tenantId, List<MaintenanceStatus> statuses);

    // ==================== 统计查询（管理后台看板，按租户隔离） ====================

    /**
     * 统计指定租户下保全案件总数
     *
     * @param tenantId 租户ID
     * @return 保全案件总数
     */
    @Query("SELECT COUNT(m) FROM MaintenanceView m WHERE m.tenantId = :tenantId "
            + "AND (m.independentCase = false OR m.initializationCompleted = true)")
    long countOperatorVisibleByTenantId(@Param("tenantId") String tenantId);

    /**
     * 统计指定租户下指定状态集合的保全案件数量
     *
     * @param tenantId 租户ID
     * @param statuses 状态集合
     * @return 命中数量
     */
    @Query("SELECT COUNT(m) FROM MaintenanceView m WHERE m.tenantId = :tenantId "
            + "AND m.status IN :statuses "
            + "AND (m.independentCase = false OR m.initializationCompleted = true)")
    long countOperatorVisibleByTenantIdAndStatusIn(@Param("tenantId") String tenantId,
                                                   @Param("statuses") List<MaintenanceStatus> statuses);

    /**
     * 统计指定租户下创建时间落在 [start, end) 区间的保全案件数量（今日新增口径）
     * <p>
     * 采用半开区间 {@code createTime >= start AND createTime < end}，避免 Between 双端闭合把次日零点计入。
     * </p>
     *
     * @param tenantId 租户ID
     * @param start    创建时间起（含）
     * @param end      创建时间止（不含）
     * @return 命中数量
     */
    @Query("SELECT COUNT(m) FROM MaintenanceView m WHERE m.tenantId = :tenantId "
            + "AND m.createTime >= :start AND m.createTime < :end "
            + "AND (m.independentCase = false OR m.initializationCompleted = true)")
    long countOperatorVisibleByTenantIdAndCreateTimeRange(@Param("tenantId") String tenantId,
                                                          @Param("start") LocalDateTime start,
                                                          @Param("end") LocalDateTime end);
}
