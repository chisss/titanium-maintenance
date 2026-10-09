--liquibase formatted sql
--changeset weisun:maintenance-field-change-backfill-g05-06-202609301530

-- 字段级差异行「跨案冗余键 + 生效口径」存量回填（g05-06，配套 DDL：maintenance_surrender_factor_view_g05_06_*）。
--
-- 背景：t_maintenance_field_change_view 的 6 个新列在该表**已有行**上全部为 NULL。
--       这些列自本批起由投影写侧维护（MaintenanceCaseProjectionEventHandler#applyCaseFacts 随案件读模型
--       整体刷新），但**历史行不会再收到事件**——不主动回填就永久空值，字段级行无法自描述、无法跨案检索。
--
-- 【只读统计：执行前请先跑这段，确认存量面】（不改数据，未纳入 changeset）
--   -- ① 待回填的行数与涉及案件数（分母）
--   SELECT COUNT(*) AS field_rows, COUNT(DISTINCT maintenance_id) AS cases
--     FROM t_maintenance_field_change_view WHERE policy_number IS NULL;
--   -- ② 有多少行能对上案件读模型（可回填的上界）；对不上的行说明案件源行缺失，须人工核
--   SELECT COUNT(*) AS joinable
--     FROM t_maintenance_field_change_view f
--     JOIN t_maintenance_view m ON m.tenant_id = f.tenant_id AND m.maintenance_id = f.maintenance_id
--    WHERE f.policy_number IS NULL;
--   -- ③ 批单号可回填的行数（同一「保全项」下批单号唯一者才回填，见下）
--   SELECT COUNT(*) AS endorsement_joinable
--     FROM t_maintenance_field_change_view f
--     JOIN (SELECT tenant_id, maintenance_id, item_code, MIN(policy_endorsement_no) AS no
--             FROM t_maintenance_workflow_task_view
--            WHERE policy_endorsement_no IS NOT NULL AND policy_endorsement_no <> ''
--            GROUP BY tenant_id, maintenance_id, item_code
--           HAVING COUNT(DISTINCT policy_endorsement_no) = 1) e
--       ON e.tenant_id = f.tenant_id AND e.maintenance_id = f.maintenance_id AND e.item_code = f.item_code
--    WHERE f.endorsement_no IS NULL;
--
-- 【回填范围与不回填项】
--   ① 5 列取自案件读模型 t_maintenance_view（policy_id / policy_number / specific_effective_date /
--      business_effective_at / balance_direction→fee_direction）：与投影 write 侧同源同义，逐列直搬。
--   ② endorsement_no（批单号）取自 t_maintenance_workflow_task_view.policy_endorsement_no（生效回执）。
--      🔴 只在「同一 (案件, 保全项) 下批单号**唯一**」时回填 —— 与查询侧 soleEndorsementNo() 同判据：
--         多号说明该保全项被分次施加，案件级/项级都不存在唯一值，**不静默择一**，留 NULL 由行级如实呈现。
--      （SQL 侧无「MIN 取首」以外的择一：HAVING COUNT(DISTINCT)=1 已把多号组整个排除，MIN 只用于取值。）
--
-- 【幂等】两个 UPDATE 均以 `IS NULL` 为闸门，重复执行不再命中已回填行。
--
-- 【真机实测（2026-09-30，克隆库执行；真实库规模 151 案件 / 37 字段行 / 629 任务行）】
--   ① 结果：37/37 行回填到 policy_id/policy_number（0 残留），21/37 行回填到批单号 —— 与上方只读统计
--      ①（37 行 / 35 案件）③（21 行）逐数吻合，无「统计说能填、执行却填不上」的落差。
--   ② 幂等：重复执行后 `CHECKSUM TABLE` 与行数（37）均不变。
--   ③ 🔴 fee_direction 回填后仍为 NULL 的 37 行**不是漏填**：取值源 t_maintenance_view.balance_direction
--      在存量 151 个案件行中有 121 行为 NULL（该列由 g05 期投影才开始写入）⇒ 源空则目标空，如实透传。
--      判据：本回填是**搬运**不是**推断**；要让历史行也有费用方向，须由业务侧补数据，不得在此臆造默认值。
--   ④ 🔴 存量数据中「同一 (案件, 保全项) 下批单号多值」的分组数为 **0** ⇒ 下方 HAVING 的「多值排除」分支
--      在真机数据上**无实例、未被覆盖**（21 个可填行全部来自唯一号分组）。该分支的承重由查询侧
--      `MaintenanceHistoryQueryServiceImpl#soleEndorsementNo` 的单测守住（多号 ⇒ 案件级留空），
--      不要因「真机跑过没报错」而认为它已被验证。
--
-- 🔴 为何先落临时表再 UPDATE：MySQL 不允许在 UPDATE 的子查询中引用被更新的表（ERROR 1093），
--    而本回填的取值来源恰含目标表自身（幂等闸门 f.policy_number IS NULL）。
--    中转临时表把「算值」与「写值」彻底分离，与目标表无自引用，结果确定（与
--    maintenance_view_backfill_no_202609201148_weisun_dml.sql 同范式）。
--
-- 🔴 本 changeset 不提供 rollback：把已回填值改回 NULL 等于让缺陷复现，且无法区分「回填值」与「原生值」。
--    如需撤销，请按事前备份人工处置。

CREATE TEMPORARY TABLE tmp_field_change_case_facts AS
SELECT f.field_change_id,
       m.policy_id,
       m.policy_number,
       m.specific_effective_date,
       m.business_effective_at,
       m.balance_direction
FROM t_maintenance_field_change_view f
JOIN t_maintenance_view m
  ON m.tenant_id = f.tenant_id
 AND m.maintenance_id = f.maintenance_id
WHERE f.policy_number IS NULL;

UPDATE t_maintenance_field_change_view f
JOIN tmp_field_change_case_facts t ON t.field_change_id = f.field_change_id
SET f.policy_id = t.policy_id,
    f.policy_number = t.policy_number,
    f.specific_effective_date = t.specific_effective_date,
    f.business_effective_at = t.business_effective_at,
    f.fee_direction = t.balance_direction;

DROP TEMPORARY TABLE tmp_field_change_case_facts;

CREATE TEMPORARY TABLE tmp_field_change_endorsement AS
SELECT f.field_change_id,
       e.endorsement_no
FROM t_maintenance_field_change_view f
JOIN (SELECT tenant_id, maintenance_id, item_code,
             MIN(policy_endorsement_no) AS endorsement_no
        FROM t_maintenance_workflow_task_view
       WHERE policy_endorsement_no IS NOT NULL
         AND policy_endorsement_no <> ''
       GROUP BY tenant_id, maintenance_id, item_code
      HAVING COUNT(DISTINCT policy_endorsement_no) = 1) e
  ON e.tenant_id = f.tenant_id
 AND e.maintenance_id = f.maintenance_id
 AND e.item_code = f.item_code
WHERE f.endorsement_no IS NULL;

UPDATE t_maintenance_field_change_view f
JOIN tmp_field_change_endorsement t ON t.field_change_id = f.field_change_id
SET f.endorsement_no = t.endorsement_no;

DROP TEMPORARY TABLE tmp_field_change_endorsement;
