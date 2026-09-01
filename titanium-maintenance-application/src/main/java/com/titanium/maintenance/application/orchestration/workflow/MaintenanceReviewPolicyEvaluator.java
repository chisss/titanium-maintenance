package com.titanium.maintenance.application.orchestration.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.titanium.maintenance.application.command.casecreation.MaintenanceAutomaticReviewInput;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceReviewGate;
import com.titanium.maintenance.common.enums.workflow.MaintenanceReviewMode;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowTaskStatus;
import com.titanium.maintenance.configuration.MaintenanceItemConfiguration;
import com.titanium.maintenance.configuration.control.MaintenanceChannelCapability;
import com.titanium.maintenance.query.view.MaintenanceCaseItemView;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.query.view.MaintenanceWorkflowTaskView;
import com.titanium.maintenance.valueobject.workflow.MaintenanceReviewGateEvidence;

/** 在 Application 解析冻结审核策略，任何证据缺失都保守转人工。 */
@Component
public class MaintenanceReviewPolicyEvaluator {

    private static final String AUTOMATIC_APPROVAL_COMMENT = "七类自动审核门禁全部通过";

    public Evaluation evaluate(
            MaintenanceView caseView,
            MaintenanceWorkflowTaskView taskView,
            MaintenanceCaseItemView itemView,
            MaintenanceItemConfiguration configuration,
            MaintenanceAutomaticReviewInput input) {
        List<String> reasons = new ArrayList<>();
        String configurationHash = configuration.getContentHash();
        String policyCode = configuration.getDefinition().controls().approvalPolicyCode();

        boolean channelPassed = caseView.getSource() == MaintenanceChannel.API
                && input.source() == MaintenanceChannel.API
                && configuration.getDefinition().controls().channelCapabilities().stream()
                        .anyMatch(this::automaticApiCapability);
        addReason(reasons, channelPassed, "CHANNEL_NOT_ELIGIBLE");

        boolean productPassed = hasHash(itemView.getOfferingContentHash())
                && hasText(itemView.getOfferingId())
                && hasText(itemView.getOfferingVersion());
        addReason(reasons, productPassed, "PRODUCT_EVIDENCE_INCOMPLETE");

        boolean itemPassed = Objects.equals(itemView.getItemCode(), taskView.getItemCode())
                && Objects.equals(itemView.getItemCode(), configuration.getDefinition().itemCode())
                && Objects.equals(itemView.getConfigurationVersion(), configuration.getDefinition().version())
                && hasHash(configurationHash)
                && Objects.equals(configurationHash, itemView.getConfigurationContentHash());
        addReason(reasons, itemPassed, "ITEM_CONFIGURATION_MISMATCH");

        boolean identityPassed = Boolean.TRUE.equals(input.identityVerified())
                && hasHash(input.identityEvidenceHash());
        addReason(reasons, identityPassed, "IDENTITY_EVIDENCE_INCOMPLETE");

        Set<String> requiredMaterials = configuration.getDefinition().controls().materialRequirements().stream()
                .map(requirement -> requirement.materialCode())
                .collect(Collectors.toUnmodifiableSet());
        boolean materialsPassed = input.satisfiedMaterialCodes().containsAll(requiredMaterials)
                && (requiredMaterials.isEmpty() || hasHash(input.materialEvidenceHash()));
        addReason(reasons, materialsPassed, "MATERIAL_EVIDENCE_INCOMPLETE");

        boolean amountPassed = Boolean.TRUE.equals(input.amountWithinLimit())
                && hasHash(input.amountEvidenceHash());
        addReason(reasons, amountPassed, "AMOUNT_EVIDENCE_INCOMPLETE");

        boolean riskPassed = Boolean.TRUE.equals(input.riskAccepted())
                && hasHash(input.riskEvidenceHash());
        addReason(reasons, riskPassed, "RISK_EVIDENCE_INCOMPLETE");

        addReason(reasons, hasText(policyCode), "APPROVAL_POLICY_MISSING");
        addReason(reasons, hasText(input.policyVersion()), "APPROVAL_POLICY_VERSION_MISSING");
        addReason(reasons, reviewerSeparated(caseView, input.operatorId()), "REVIEWER_NOT_SEPARATED");
        addReason(reasons, taskView.getStepType() == MaintenanceStepType.REVIEW, "TASK_NOT_REVIEW");
        addReason(reasons, automaticTaskAvailable(taskView, input), "TASK_REQUIRES_MANUAL_HANDLING");

        if (!reasons.isEmpty()) {
            return Evaluation.manualRequired(policyCode, input.policyVersion(), reasons);
        }
        String materialHash = requiredMaterials.isEmpty()
                ? configurationHash
                : input.materialEvidenceHash();
        return Evaluation.approved(policyCode, input.policyVersion(), List.of(
                gate(MaintenanceReviewGate.CHANNEL, configurationHash, "API_AUTO_APPROVAL_ALLOWED"),
                gate(MaintenanceReviewGate.PRODUCT, itemView.getOfferingContentHash(), "OFFERING_AUTHORIZED"),
                gate(MaintenanceReviewGate.ITEM, configurationHash, "FROZEN_CONFIGURATION_MATCHED"),
                gate(MaintenanceReviewGate.IDENTITY, input.identityEvidenceHash(), "IDENTITY_VERIFIED"),
                gate(MaintenanceReviewGate.MATERIAL, materialHash, "MATERIALS_COMPLETE"),
                gate(MaintenanceReviewGate.AMOUNT, input.amountEvidenceHash(), "AMOUNT_WITHIN_LIMIT"),
                gate(MaintenanceReviewGate.RISK, input.riskEvidenceHash(), "RISK_ACCEPTED")));
    }

    private boolean automaticApiCapability(MaintenanceChannelCapability capability) {
        return capability.channel() == MaintenanceChannel.API && capability.autoApprovalAllowed();
    }

    private boolean automaticTaskAvailable(
            MaintenanceWorkflowTaskView taskView,
            MaintenanceAutomaticReviewInput input) {
        return (taskView.getStatus() == MaintenanceWorkflowTaskStatus.READY
                && taskView.getAssignedTo() == null)
                || (taskView.getStatus() == MaintenanceWorkflowTaskStatus.COMPLETED
                        && taskView.getReviewMode() == MaintenanceReviewMode.AUTOMATIC
                        && Objects.equals(taskView.getLastOperationId(), input.operationId()));
    }

    private boolean reviewerSeparated(MaintenanceView caseView, String operatorId) {
        return hasText(caseView.getCreatedBy())
                && hasText(operatorId)
                && !caseView.getCreatedBy().equals(operatorId.trim());
    }

    private MaintenanceReviewGateEvidence gate(
            MaintenanceReviewGate gate,
            String evidenceHash,
            String detailCode) {
        return new MaintenanceReviewGateEvidence(gate, true, evidenceHash, detailCode);
    }

    private void addReason(List<String> reasons, boolean passed, String reason) {
        if (!passed) {
            reasons.add(reason);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean hasHash(String value) {
        return value != null && value.matches("[0-9a-fA-F]{64}");
    }

    /** 策略解析结果；只有 approved 才包含可发送给 Domain 的七门禁证据。 */
    public record Evaluation(
            boolean approved,
            String policyCode,
            String policyVersion,
            List<MaintenanceReviewGateEvidence> gates,
            List<String> reasons) {

        public Evaluation {
            gates = gates == null ? List.of() : List.copyOf(gates);
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }

        public static Evaluation approved(
                String policyCode,
                String policyVersion,
                List<MaintenanceReviewGateEvidence> gates) {
            return new Evaluation(true, policyCode, policyVersion, gates, List.of());
        }

        public static Evaluation manualRequired(
                String policyCode,
                String policyVersion,
                List<String> reasons) {
            return new Evaluation(false, policyCode, policyVersion, List.of(), reasons);
        }

        public String approvalComment() {
            return AUTOMATIC_APPROVAL_COMMENT;
        }
    }
}
