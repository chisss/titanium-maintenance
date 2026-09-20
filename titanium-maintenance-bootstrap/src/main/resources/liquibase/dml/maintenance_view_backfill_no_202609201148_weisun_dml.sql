--liquibase formatted sql
--changeset weisun:maintenance-no-backfill-1

-- 历史保全单「保全号」回填。
--
-- 背景：t_maintenance_view.maintenance_no 由 maintenance_no_202608281620_weisun_ddl.sql 于 2026-08-28 16:20 引入；
--       号码只在 MaintenanceCreatedEvent 投影时分配一次（MaintenanceProjectionEventHandler#on(MaintenanceCreatedEvent)），
--       该处理器带 `view.getMaintenanceNo() == null` 判据，建成行之后不会再补号 ——
--       引入时刻之前已存在的读模型行**永久为空号**（租户 1 共 79 行，创建时间 08-20 ~ 08-28，全部早于 16:20）。
--
-- 分配规则：与 BusinessNumberFormatter 对齐 —— 前缀 'MNT' + 业务日期(yyyyMMdd) + 7 位当日流水。
--   业务日期取该行自身的 create_time 日期；流水在 (租户, 业务日期) 内按 create_time 升序自 1 起编号，
--   并叠加该 (租户, 业务日期) 下**已有号码的行数**作为基数，保证在「只回填了一部分」的库上也不与既有号相撞。
--
-- 幂等：仅处理 maintenance_no 为空的行，重复执行不再产生新号；
--       uk_maintenance_view_no(tenant_id, maintenance_no) 唯一索引作为最终兜底。
--
-- 🔴 为何先落临时表而不是 `UPDATE ... JOIN (SELECT ... FROM t_maintenance_view)`：
--    后者要求优化器把派生表物化，否则 MySQL 抛 ERROR 1093（不能在 UPDATE 的子查询里引用被更新的表）。
--    物化与否取决于 derived_merge 的启发式，属「大概率成立」而非确定性保证。
--    中转临时表把「算号」与「写号」彻底分离，与目标表无自引用，结果确定。
--    （CREATE/DROP TEMPORARY TABLE 不触发隐式提交，故本 changeset 整体仍在一个事务内。）
--
-- 🔴 流水表 t_business_number_sequence 无需同步：JdbcBusinessNumberGenerator 只以 LocalDate.now() 取业务日期，
--    本 changeset 处理的均为**历史日期**，今后不会再向这些日期发号，两者不会相遇。
--    （若在「空号行的创建日期等于执行当天」的库上执行，则必须先同步该表 last_sequence，
--      否则当日新建保全单发号时会撞 uk_maintenance_view_no。）
--
-- 🔴 本 changeset 不提供 rollback：把它回滚成 NULL 等于让缺陷复现，且无法区分「回填号」与「原生号」。
--    如需撤销，请按 backup 表人工处置。

CREATE TEMPORARY TABLE tmp_maintenance_no_alloc AS
SELECT m.maintenance_id,
       CONCAT('MNT',
              DATE_FORMAT(m.create_time, '%Y%m%d'),
              LPAD(ROW_NUMBER() OVER (
                       PARTITION BY m.tenant_id, DATE(m.create_time)
                       ORDER BY m.create_time, m.maintenance_id
                   )
                   + (SELECT COUNT(*)
                        FROM t_maintenance_view e
                       WHERE e.tenant_id = m.tenant_id
                         AND DATE(e.create_time) = DATE(m.create_time)
                         AND e.maintenance_no IS NOT NULL
                         AND e.maintenance_no <> ''), 7, '0')) AS allocated_no
FROM t_maintenance_view m
WHERE m.maintenance_no IS NULL OR m.maintenance_no = '';

UPDATE t_maintenance_view v
JOIN tmp_maintenance_no_alloc a ON a.maintenance_id = v.maintenance_id
SET v.maintenance_no = a.allocated_no;

DROP TEMPORARY TABLE tmp_maintenance_no_alloc;
