package com.titanium.maintenance.web.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.EffectCompensationQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.EffectScheduleQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.FieldChangeQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.ItemQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.RetroactiveImpactAnalysisQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.RetroactiveImpactItemQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.RetroactivePeriodAdjustmentQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.RetroactivePeriodRecalculationQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.RetroactivePeriodResolutionQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.SnapshotReferenceQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.SnapshotSetQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowAppliedFieldEvidenceQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowAssignmentQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowBillingPostingEvidenceQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowConditionEvidenceQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowEffectEvidenceQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowEffectRequestEvidenceQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowFailureQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowFundSettlementEvidenceQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowOperationQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowPolicyApplicationEvidenceQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowPremiumQuoteEvidenceQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowReviewEvidenceQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowReviewGateQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowTaskQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowUnderwritingEvidenceQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCasePageQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCasePageQueryResult.MaintenanceCaseSummaryQueryResult;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.EffectCompensationVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.EffectScheduleVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.FieldChangeVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.ItemVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.RetroactiveImpactAnalysisVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.RetroactiveImpactItemVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.RetroactivePeriodAdjustmentVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.RetroactivePeriodRecalculationVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.RetroactivePeriodResolutionVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.SnapshotReferenceVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.SnapshotSetVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.WorkflowAppliedFieldEvidenceVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.WorkflowAssignmentVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.WorkflowBillingPostingEvidenceVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.WorkflowConditionEvidenceVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.WorkflowEffectEvidenceVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.WorkflowEffectRequestEvidenceVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.WorkflowFailureVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.WorkflowFundSettlementEvidenceVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.WorkflowOperationVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.WorkflowPolicyApplicationEvidenceVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.WorkflowPremiumQuoteEvidenceVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.WorkflowReviewEvidenceVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.WorkflowReviewGateVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.WorkflowTaskVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.WorkflowUnderwritingEvidenceVO;
import com.titanium.maintenance.web.response.MaintenanceCasePageVO;
import com.titanium.maintenance.web.response.MaintenanceCasePageVO.MaintenanceCaseSummaryVO;

/**
 * 独立保全案件 Query 结果到 Web VO 的协议映射（MapStruct 声明式）
 * <p>
 * 全部为 Query 结果 record → Web 响应 record 的纯结构构造映射，同名组件自动映射；唯一命名差异为
 * {@code maintenanceId → caseId}。嵌套记录（证据、快照、追溯期等）由 MapStruct 按本接口声明的
 * 子映射方法递归组装，空值逐级穿透（与原手工 null 判断语义一致）。
 * </p>
 * <p>
 * 源端 {@code WorkflowPolicyApplicationEvidenceQueryResult} 的 stateAction/statusBefore/statusAfter
 * 属 Query 内部字段，不进 Web 契约，按 {@code unmappedSourcePolicy = IGNORE} 忽略。
 * </p>
 */
@Mapper(componentModel = "spring", unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface MaintenanceCaseQueryWebMapper {

    /** 分页查询结果 → 分页响应 */
    MaintenanceCasePageVO toPageVO(MaintenanceCasePageQueryResult result);

    /** 分页单行 → 分页单行 VO（caseId ← maintenanceId） */
    @Mapping(target = "caseId", source = "maintenanceId")
    MaintenanceCaseSummaryVO toSummaryVO(MaintenanceCaseSummaryQueryResult item);

    /** 案件详情查询结果 → 详情响应（caseId ← maintenanceId） */
    @Mapping(target = "caseId", source = "maintenanceId")
    MaintenanceCaseDetailVO toDetailVO(MaintenanceCaseDetailQueryResult result);

    /** 生效补偿证据 → VO */
    EffectCompensationVO toEffectCompensationVO(EffectCompensationQueryResult compensation);

    /** 未来生效计划 → VO */
    EffectScheduleVO toEffectScheduleVO(EffectScheduleQueryResult schedule);

    /** 追溯影响分析 → VO */
    RetroactiveImpactAnalysisVO toRetroactiveImpactAnalysisVO(RetroactiveImpactAnalysisQueryResult analysis);

    /** 追溯影响明细项 → VO */
    RetroactiveImpactItemVO toRetroactiveImpactItemVO(RetroactiveImpactItemQueryResult item);

    /** 追溯期重算 → VO */
    RetroactivePeriodRecalculationVO toRetroactivePeriodRecalculationVO(
            RetroactivePeriodRecalculationQueryResult recalculation);

    /** 追溯期决议 → VO */
    RetroactivePeriodResolutionVO toRetroactivePeriodResolutionVO(
            RetroactivePeriodResolutionQueryResult resolution);

    /** 追溯期调整行 → VO */
    RetroactivePeriodAdjustmentVO toRetroactivePeriodAdjustmentVO(
            RetroactivePeriodAdjustmentQueryResult period);

    /** 流程任务 → VO */
    WorkflowTaskVO toWorkflowTaskVO(WorkflowTaskQueryResult task);

    /** 任务指派 → VO */
    WorkflowAssignmentVO toWorkflowAssignmentVO(WorkflowAssignmentQueryResult assignment);

    /** 任务失败信息 → VO */
    WorkflowFailureVO toWorkflowFailureVO(WorkflowFailureQueryResult failure);

    /** 条件步骤证据 → VO */
    WorkflowConditionEvidenceVO toWorkflowConditionEvidenceVO(WorkflowConditionEvidenceQueryResult evidence);

    /** 审核证据 → VO */
    WorkflowReviewEvidenceVO toWorkflowReviewEvidenceVO(WorkflowReviewEvidenceQueryResult evidence);

    /** 审核门禁 → VO */
    WorkflowReviewGateVO toWorkflowReviewGateVO(WorkflowReviewGateQueryResult gate);

    /** 核保证据 → VO */
    WorkflowUnderwritingEvidenceVO toWorkflowUnderwritingEvidenceVO(
            WorkflowUnderwritingEvidenceQueryResult evidence);

    /** 报价证据 → VO */
    WorkflowPremiumQuoteEvidenceVO toWorkflowPremiumQuoteEvidenceVO(
            WorkflowPremiumQuoteEvidenceQueryResult evidence);

    /** 入账证据 → VO */
    WorkflowBillingPostingEvidenceVO toWorkflowBillingPostingEvidenceVO(
            WorkflowBillingPostingEvidenceQueryResult evidence);

    /** 资金结算证据 → VO */
    WorkflowFundSettlementEvidenceVO toWorkflowFundSettlementEvidenceVO(
            WorkflowFundSettlementEvidenceQueryResult evidence);

    /** 生效证据 → VO */
    WorkflowEffectEvidenceVO toWorkflowEffectEvidenceVO(WorkflowEffectEvidenceQueryResult evidence);

    /** 生效请求证据 → VO */
    WorkflowEffectRequestEvidenceVO toWorkflowEffectRequestEvidenceVO(
            WorkflowEffectRequestEvidenceQueryResult request);

    /** Policy 应用证据 → VO（stateAction/statusBefore/statusAfter 不进 Web 契约） */
    WorkflowPolicyApplicationEvidenceVO toWorkflowPolicyApplicationEvidenceVO(
            WorkflowPolicyApplicationEvidenceQueryResult application);

    /** 已应用字段证据 → VO */
    WorkflowAppliedFieldEvidenceVO toWorkflowAppliedFieldEvidenceVO(
            WorkflowAppliedFieldEvidenceQueryResult field);

    /** 最近操作 → VO */
    WorkflowOperationVO toWorkflowOperationVO(WorkflowOperationQueryResult operation);

    /** 保全项 → VO */
    ItemVO toItemVO(ItemQueryResult item);

    /** 字段四值差异 → VO */
    FieldChangeVO toFieldChangeVO(FieldChangeQueryResult field);

    /** 三类快照集合 → VO */
    SnapshotSetVO toSnapshotSetVO(SnapshotSetQueryResult snapshots);

    /** 单个快照引用 → VO */
    SnapshotReferenceVO toSnapshotReferenceVO(SnapshotReferenceQueryResult reference);
}
