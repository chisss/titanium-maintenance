--liquibase formatted sql
--changeset weisun:maintenance-premium-calculation-checkpoint-m5-11-202608271730
-- 多费用任务若产生不同计算链，案件必须阻断追溯重算而非按事件顺序覆盖。
ALTER TABLE t_maintenance_view
    MODIFY COLUMN original_calculation_id VARCHAR(128) COMMENT '原确认计算ID';
ALTER TABLE t_maintenance_view
    MODIFY COLUMN replacement_calculation_id VARCHAR(128) COMMENT '替代确认计算ID';
ALTER TABLE t_maintenance_view
    ADD COLUMN premium_calculation_checkpoint_conflict BOOLEAN NOT NULL DEFAULT FALSE
        COMMENT '费用计算检查点是否存在多任务冲突';

-- 标记历史多链报价、半成品案件检查点或与报价任务不一致的既有检查点。
UPDATE t_maintenance_view case_view
SET premium_calculation_checkpoint_conflict = TRUE
WHERE EXISTS (
    SELECT 1
    FROM t_maintenance_workflow_task_view task_view
    WHERE task_view.tenant_id = case_view.tenant_id
      AND task_view.maintenance_id = case_view.maintenance_id
      AND task_view.premium_quote_status = 'QUOTED'
      AND task_view.premium_quote_original_calculation_id IS NOT NULL
      AND task_view.premium_quote_replacement_calculation_id IS NOT NULL
)
AND (
    (case_view.original_calculation_id IS NULL) <> (case_view.replacement_calculation_id IS NULL)
    OR (
        SELECT COUNT(DISTINCT CONCAT(
            CHAR_LENGTH(task_view.premium_quote_original_calculation_id), ':',
            task_view.premium_quote_original_calculation_id,
            task_view.premium_quote_replacement_calculation_id))
        FROM t_maintenance_workflow_task_view task_view
        WHERE task_view.tenant_id = case_view.tenant_id
          AND task_view.maintenance_id = case_view.maintenance_id
          AND task_view.premium_quote_status = 'QUOTED'
          AND task_view.premium_quote_original_calculation_id IS NOT NULL
          AND task_view.premium_quote_replacement_calculation_id IS NOT NULL
    ) > 1
    OR (
        case_view.original_calculation_id IS NOT NULL
        AND case_view.replacement_calculation_id IS NOT NULL
        AND NOT EXISTS (
            SELECT 1
            FROM t_maintenance_workflow_task_view task_view
            WHERE task_view.tenant_id = case_view.tenant_id
              AND task_view.maintenance_id = case_view.maintenance_id
              AND task_view.premium_quote_status = 'QUOTED'
              AND task_view.premium_quote_original_calculation_id = case_view.original_calculation_id
              AND task_view.premium_quote_replacement_calculation_id = case_view.replacement_calculation_id
        )
    )
);

-- 旧投影已消费报价事件的在途案件，从唯一报价计算链恢复案件级重算检查点。
UPDATE t_maintenance_view case_view
SET original_calculation_id = (
        SELECT MIN(task_view.premium_quote_original_calculation_id)
        FROM t_maintenance_workflow_task_view task_view
        WHERE task_view.tenant_id = case_view.tenant_id
          AND task_view.maintenance_id = case_view.maintenance_id
          AND task_view.premium_quote_status = 'QUOTED'
    ),
    replacement_calculation_id = (
        SELECT MIN(task_view.premium_quote_replacement_calculation_id)
        FROM t_maintenance_workflow_task_view task_view
        WHERE task_view.tenant_id = case_view.tenant_id
          AND task_view.maintenance_id = case_view.maintenance_id
          AND task_view.premium_quote_status = 'QUOTED'
    )
WHERE original_calculation_id IS NULL
  AND replacement_calculation_id IS NULL
  AND premium_calculation_checkpoint_conflict = FALSE
  AND 1 = (
      SELECT COUNT(DISTINCT CONCAT(
          CHAR_LENGTH(task_view.premium_quote_original_calculation_id), ':',
          task_view.premium_quote_original_calculation_id,
          task_view.premium_quote_replacement_calculation_id))
      FROM t_maintenance_workflow_task_view task_view
      WHERE task_view.tenant_id = case_view.tenant_id
        AND task_view.maintenance_id = case_view.maintenance_id
        AND task_view.premium_quote_status = 'QUOTED'
        AND task_view.premium_quote_original_calculation_id IS NOT NULL
        AND task_view.premium_quote_replacement_calculation_id IS NOT NULL
  );

--rollback ALTER TABLE t_maintenance_view DROP COLUMN premium_calculation_checkpoint_conflict;
--rollback ALTER TABLE t_maintenance_view MODIFY COLUMN replacement_calculation_id VARCHAR(64) COMMENT '替代确认计算ID';
--rollback ALTER TABLE t_maintenance_view MODIFY COLUMN original_calculation_id VARCHAR(64) COMMENT '原确认计算ID';
