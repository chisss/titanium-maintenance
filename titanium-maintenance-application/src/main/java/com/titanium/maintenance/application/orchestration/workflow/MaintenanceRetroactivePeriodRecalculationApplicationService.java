package com.titanium.maintenance.application.orchestration.workflow;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.titanium.maintenance.application.command.retroactive.MaintenanceRetroactivePeriodRecalculationInput;
import com.titanium.maintenance.application.model.retroactive.MaintenanceRetroactivePeriodRecalculationResult;
import com.titanium.maintenance.command.CompleteMaintenanceRetroactivePeriodRecalculationCommand;
import com.titanium.maintenance.command.FailMaintenanceRetroactivePeriodRecalculationCommand;
import com.titanium.maintenance.command.RecordMaintenanceRetroactiveProductRecalculationCommand;
import com.titanium.maintenance.command.StartMaintenanceRetroactivePeriodRecalculationCommand;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.config.MaintenanceFeeMode;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactAnalysisStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactDomain;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactivePeriodRecalculationStatus;
import com.titanium.maintenance.common.exception.MaintenanceNotFoundException;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.port.billing.BillingRetroactivePeriodAdjustmentPort;
import com.titanium.maintenance.port.billing.BillingRetroactivePeriodAdjustmentPort.AdjustmentFact;
import com.titanium.maintenance.port.billing.BillingRetroactivePeriodAdjustmentPort.AdjustmentRequest;
import com.titanium.maintenance.port.maintenance.MaintenanceRetroactiveImpactSourcePort;
import com.titanium.maintenance.port.product.ProductRetroactivePeriodRecalculationPort;
import com.titanium.maintenance.port.product.ProductRetroactivePeriodRecalculationPort.AffectedPeriod;
import com.titanium.maintenance.port.product.ProductRetroactivePeriodRecalculationPort.RecalculationFact;
import com.titanium.maintenance.port.product.ProductRetroactivePeriodRecalculationPort.RecalculationRequest;
import com.titanium.maintenance.query.repository.MaintenanceCaseItemViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceRetroactiveImpactItemViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceRetroactivePeriodAdjustmentViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.view.MaintenanceRetroactiveImpactItemView;
import com.titanium.maintenance.query.view.MaintenanceRetroactivePeriodAdjustmentView;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveBillingAdjustmentEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveProductPeriodDifference;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveProductRecalculationEvidence;

/** 编排 Product 期间重算、检查点保存与 Billing 会计期间调整。 */
@Service
public class MaintenanceRetroactivePeriodRecalculationApplicationService {

    private static final String PRODUCT_FAILURE_CODE = "RETROACTIVE_PRODUCT_RECALCULATION_FAILED";
    private static final String BILLING_FAILURE_CODE = "RETROACTIVE_BILLING_ADJUSTMENT_FAILED";

    // 无费用保全（无需产品重算/计费调整）追溯重算的落库证据常量（红线 20：落库/跨域业务描述禁止写死字符串）
    private static final String NO_FEE_MARKER                = "NO_FEE";
    private static final String NO_FEE_ORIGINAL_ID_PREFIX    = "no-fee-original-";
    private static final String NO_FEE_REPLACEMENT_ID_PREFIX = "no-fee-replacement-";
    private static final String HASH_SALT_ORIGINAL           = "ORIGINAL";
    private static final String HASH_SALT_REPLACEMENT        = "REPLACEMENT";
    private static final String NO_FEE_PRODUCT_VERSION       = "PERIOD_V1";
    private static final String NO_FEE_PRODUCT_ID_PREFIX     = "no-fee-product-";
    private static final String NO_FEE_DIRECTION             = "NONE";
    private static final String NO_FEE_ZERO_AMOUNT           = "0";
    private static final String NO_FEE_CURRENCY              = "CNY";
    private static final String NO_FEE_BILLING_MARKER        = "NO_BILLING_REQUIRED";
    private static final String NO_FEE_BILLING_ID_PREFIX     = "no-fee-billing-";
    private static final String NO_FEE_BILLING_STATUS        = "NOT_REQUIRED";

    private final CommandGateway commandGateway;
    private final MaintenanceViewRepository maintenanceViewRepository;
    private final MaintenanceRetroactiveImpactItemViewRepository impactItemViewRepository;
    private final MaintenanceRetroactivePeriodAdjustmentViewRepository periodAdjustmentViewRepository;
    private final MaintenanceCaseItemViewRepository caseItemViewRepository;
    private final MaintenanceItemConfigurationRepository configurationRepository;
    private final ProductRetroactivePeriodRecalculationPort productPort;
    private final BillingRetroactivePeriodAdjustmentPort billingPort;

    @Autowired
    public MaintenanceRetroactivePeriodRecalculationApplicationService(
            CommandGateway commandGateway,
            MaintenanceViewRepository maintenanceViewRepository,
            MaintenanceRetroactiveImpactItemViewRepository impactItemViewRepository,
            MaintenanceRetroactivePeriodAdjustmentViewRepository periodAdjustmentViewRepository,
            MaintenanceCaseItemViewRepository caseItemViewRepository,
            MaintenanceItemConfigurationRepository configurationRepository,
            ProductRetroactivePeriodRecalculationPort productPort,
            BillingRetroactivePeriodAdjustmentPort billingPort) {
        this.commandGateway = commandGateway;
        this.maintenanceViewRepository = maintenanceViewRepository;
        this.impactItemViewRepository = impactItemViewRepository;
        this.periodAdjustmentViewRepository = periodAdjustmentViewRepository;
        this.caseItemViewRepository = caseItemViewRepository;
        this.configurationRepository = configurationRepository;
        this.productPort = productPort;
        this.billingPort = billingPort;
    }

    public MaintenanceRetroactivePeriodRecalculationResult recalculate(
            MaintenanceRetroactivePeriodRecalculationInput input) {
        validate(input);
        MaintenanceView view = maintenanceViewRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        input.maintenanceId(), input.tenantId())
                .orElseThrow(MaintenanceNotFoundException::new);
        if (caseItemViewRepository != null && configurationRepository != null
                && isNoFeeCase(view, input.tenantId())) {
            return recalculateNoFee(view, input);
        }
        requireReady(view);
        if (sameOperation(view, input.operationId()) && terminal(view.getRetroactivePeriodRecalculationStatus())) {
            return existingResult(view);
        }

        List<AffectedPeriod> periods = affectedPeriods(view, input.tenantId());
        String recalculationId = sameOperation(view, input.operationId())
                ? view.getRetroactivePeriodRecalculationId() : recalculationId(input);
        int recalculationVersion = sameOperation(view, input.operationId())
                ? currentVersion(view) : currentVersion(view) + 1;
        String productRequestId = requestId("mpr", input);
        RecalculationRequest productRequest = new RecalculationRequest(
                input.tenantId(), input.maintenanceId(), view.getPolicyId(),
                view.getRetroactiveImpactAnalysisId(), view.getRetroactiveImpactAnalysisVersion(),
                view.getRetroactiveImpactResultHash(), view.getOriginalCalculationId(),
                view.getReplacementCalculationId(), view.getRetroactiveImpactScopeFrom(),
                view.getRetroactiveImpactScopeTo(), periods, productRequestId);
        commandGateway.sendAndWait(new StartMaintenanceRetroactivePeriodRecalculationCommand(
                MaintenanceId.of(input.maintenanceId()), recalculationId, input.operationId(),
                productRequest.payloadHash(), view.getRetroactiveImpactAnalysisId(),
                view.getRetroactiveImpactAnalysisVersion(), view.getRetroactiveImpactResultHash(),
                LocalDateTime.now(), input.operatorId()));

        MaintenanceRetroactiveProductRecalculationEvidence productEvidence = null;
        String failureCode = PRODUCT_FAILURE_CODE;
        try {
            productEvidence = reusableProductEvidence(view, input, recalculationId);
            if (productEvidence == null) {
                productEvidence = toEvidence(productPort.recalculate(productRequest));
                commandGateway.sendAndWait(new RecordMaintenanceRetroactiveProductRecalculationCommand(
                        MaintenanceId.of(input.maintenanceId()), recalculationId, input.operationId(),
                        productEvidence, LocalDateTime.now(), input.operatorId()));
            }
            failureCode = BILLING_FAILURE_CODE;
            AdjustmentFact billingFact = billingPort.adjust(new AdjustmentRequest(
                    input.tenantId(), input.maintenanceId(), view.getPolicyId(), view.getCustomerId(),
                    view.getRetroactiveImpactAnalysisId(), view.getRetroactiveImpactAnalysisVersion(),
                    view.getRetroactiveImpactResultHash(), requestId("mbr", input), input.operatorId(),
                    productEvidence));
            MaintenanceRetroactiveBillingAdjustmentEvidence billingEvidence = toEvidence(billingFact);
            commandGateway.sendAndWait(new CompleteMaintenanceRetroactivePeriodRecalculationCommand(
                    MaintenanceId.of(input.maintenanceId()), recalculationId, input.operationId(),
                    billingEvidence, LocalDateTime.now(), input.operatorId()));
            return completedResult(
                    recalculationId, recalculationVersion, input.operationId(), view,
                    productEvidence, billingEvidence);
        } catch (RuntimeException exception) {
            String message = safeMessage(exception);
            commandGateway.sendAndWait(new FailMaintenanceRetroactivePeriodRecalculationCommand(
                    MaintenanceId.of(input.maintenanceId()), recalculationId, input.operationId(),
                    failureCode, message, LocalDateTime.now(), input.operatorId()));
            return failedResult(
                    recalculationId, recalculationVersion, input.operationId(), view,
                    productEvidence, failureCode, message);
        }
    }

    /** 无收退费步骤的追溯案件仍需形成零差额检查点，保证后续生效有完整勾稽证据。 */
    private MaintenanceRetroactivePeriodRecalculationResult recalculateNoFee(
            MaintenanceView view, MaintenanceRetroactivePeriodRecalculationInput input) {
        if (sameOperation(view, input.operationId()) && terminal(view.getRetroactivePeriodRecalculationStatus())) {
            return existingResult(view);
        }
        if (view.getRetroactiveImpactStatus() != MaintenanceRetroactiveImpactAnalysisStatus.COMPLETED
                || !hasText(view.getRetroactiveImpactAnalysisId())
                || view.getRetroactiveImpactAnalysisVersion() == null
                || !hasText(view.getRetroactiveImpactResultHash())
                || view.getRetroactiveImpactScopeFrom() == null || view.getRetroactiveImpactScopeTo() == null) {
            throw validation("case", "追溯影响分析检查点未完成");
        }
        String id = sameOperation(view, input.operationId())
                ? view.getRetroactivePeriodRecalculationId() : recalculationId(input);
        int version = sameOperation(view, input.operationId()) ? currentVersion(view) : currentVersion(view) + 1;
        String requestHash = MaintenanceRetroactiveImpactSourcePort.itemHash(
                input.tenantId(), input.maintenanceId(), input.operationId(), NO_FEE_MARKER);
        LocalDateTime now = LocalDateTime.now();
        commandGateway.sendAndWait(new StartMaintenanceRetroactivePeriodRecalculationCommand(
                MaintenanceId.of(input.maintenanceId()), id, input.operationId(), requestHash,
                view.getRetroactiveImpactAnalysisId(), view.getRetroactiveImpactAnalysisVersion(),
                view.getRetroactiveImpactResultHash(), now, input.operatorId()));
        String originalId = NO_FEE_ORIGINAL_ID_PREFIX + input.maintenanceId();
        String replacementId = NO_FEE_REPLACEMENT_ID_PREFIX + input.maintenanceId();
        String originalHash = MaintenanceRetroactiveImpactSourcePort.itemHash(originalId, HASH_SALT_ORIGINAL);
        String replacementHash = MaintenanceRetroactiveImpactSourcePort.itemHash(replacementId, HASH_SALT_REPLACEMENT);
        String productInputHash = MaintenanceRetroactiveImpactSourcePort.itemHash(requestHash, originalHash, replacementHash);
        String productResultHash = MaintenanceRetroactiveImpactSourcePort.itemHash(
                NO_FEE_PRODUCT_VERSION, productInputHash, NO_FEE_DIRECTION, NO_FEE_ZERO_AMOUNT, NO_FEE_CURRENCY);
        MaintenanceRetroactiveProductRecalculationEvidence product =
                new MaintenanceRetroactiveProductRecalculationEvidence(
                        NO_FEE_PRODUCT_ID_PREFIX + input.maintenanceId(), NO_FEE_PRODUCT_VERSION, originalId,
                        originalHash, replacementId, replacementHash, MaintenanceBalanceDirection.NONE,
                        BigDecimal.ZERO, NO_FEE_CURRENCY, productInputHash, productResultHash, now, List.of());
        commandGateway.sendAndWait(new RecordMaintenanceRetroactiveProductRecalculationCommand(
                MaintenanceId.of(input.maintenanceId()), id, input.operationId(), product, now, input.operatorId()));
        String billingResultHash = MaintenanceRetroactiveImpactSourcePort.itemHash(
                NO_FEE_BILLING_MARKER, id, productResultHash);
        MaintenanceRetroactiveBillingAdjustmentEvidence billing = new MaintenanceRetroactiveBillingAdjustmentEvidence(
                NO_FEE_BILLING_ID_PREFIX + input.maintenanceId(), NO_FEE_BILLING_STATUS, 0, 0,
                requestHash, billingResultHash, now, List.of());
        commandGateway.sendAndWait(new CompleteMaintenanceRetroactivePeriodRecalculationCommand(
                MaintenanceId.of(input.maintenanceId()), id, input.operationId(), billing, now, input.operatorId()));
        return result(id, version, input.operationId(), view,
                MaintenanceRetroactivePeriodRecalculationStatus.COMPLETED, product, billing, null, null, now);
    }

    private boolean isNoFeeCase(MaintenanceView view, String tenantId) {
        var items = caseItemViewRepository.findByTenantIdAndMaintenanceIdOrderByItemCodeAsc(
                tenantId, view.getMaintenanceId());
        return !items.isEmpty() && items.stream().allMatch(item -> item.getConfigurationId() != null
                && configurationRepository.findById(tenantId, item.getConfigurationId())
                        .map(stored -> stored.configuration().getDefinition().feeMode() == MaintenanceFeeMode.NONE)
                        .orElse(false));
    }

    /** 兼容 M5-05c 之前的直接构造测试与扩展调用方。 */
    public MaintenanceRetroactivePeriodRecalculationApplicationService(
            CommandGateway commandGateway,
            MaintenanceViewRepository maintenanceViewRepository,
            MaintenanceRetroactiveImpactItemViewRepository impactItemViewRepository,
            MaintenanceRetroactivePeriodAdjustmentViewRepository periodAdjustmentViewRepository,
            ProductRetroactivePeriodRecalculationPort productPort,
            BillingRetroactivePeriodAdjustmentPort billingPort) {
        this(commandGateway, maintenanceViewRepository, impactItemViewRepository,
                periodAdjustmentViewRepository, null, null, productPort, billingPort);
    }

    private List<AffectedPeriod> affectedPeriods(MaintenanceView view, String tenantId) {
        List<MaintenanceRetroactiveImpactItemView> items = impactItemViewRepository
                .findByTenantIdAndMaintenanceIdAndAnalysisId(
                        tenantId, view.getMaintenanceId(), view.getRetroactiveImpactAnalysisId());
        return items.stream()
                .filter(item -> item.getSourceDomain() == MaintenanceRetroactiveImpactDomain.BILLING)
                .filter(item -> item.getImpactType() == MaintenanceRetroactiveImpactType.PREMIUM_BILL
                        || item.getImpactType() == MaintenanceRetroactiveImpactType.RENEWAL)
                .sorted(Comparator.comparing(MaintenanceRetroactiveImpactItemView::getOccurredAt)
                        .thenComparing(MaintenanceRetroactiveImpactItemView::getItemId))
                .map(item -> new AffectedPeriod(
                        item.getItemId(), item.getReferenceId(), item.getOccurredAt(), item.getAmount(),
                        item.getCurrency(), item.getEvidenceHash()))
                .toList();
    }

    private MaintenanceRetroactiveProductRecalculationEvidence reusableProductEvidence(
            MaintenanceView view,
            MaintenanceRetroactivePeriodRecalculationInput input,
            String recalculationId) {
        if (!sameOperation(view, input.operationId()) || !hasText(view.getRetroactiveProductRecalculationId())) {
            return null;
        }
        List<MaintenanceRetroactiveProductPeriodDifference> periods = periodAdjustmentViewRepository
                .findByTenantIdAndMaintenanceIdAndPeriodRecalculationIdOrderByPeriodStartAscPeriodIdAsc(
                        input.tenantId(), input.maintenanceId(), recalculationId).stream()
                .map(this::toProductPeriod)
                .toList();
        return new MaintenanceRetroactiveProductRecalculationEvidence(
                view.getRetroactiveProductRecalculationId(), view.getRetroactiveProductRecalculationVersion(),
                view.getRetroactiveProductOriginalCalculationId(), view.getRetroactiveProductOriginalResultHash(),
                view.getRetroactiveProductReplacementCalculationId(),
                view.getRetroactiveProductReplacementResultHash(), view.getRetroactiveProductDirection(),
                view.getRetroactiveProductAmount(), view.getRetroactiveProductCurrency(),
                view.getRetroactiveProductInputHash(), view.getRetroactiveProductResultHash(),
                view.getRetroactiveProductCalculatedAt(), periods);
    }

    private MaintenanceRetroactiveProductPeriodDifference toProductPeriod(
            MaintenanceRetroactivePeriodAdjustmentView view) {
        return new MaintenanceRetroactiveProductPeriodDifference(
                view.getPeriodId(), view.getSourceReferenceId(), view.getPeriodStart(),
                view.getOriginalAmount(), view.getRecalculatedAmount(), view.getDirection(),
                view.getDifferenceAmount(), view.getCurrency(), view.getSourceEvidenceHash(),
                view.getProductResultHash());
    }

    private MaintenanceRetroactiveProductRecalculationEvidence toEvidence(RecalculationFact fact) {
        return new MaintenanceRetroactiveProductRecalculationEvidence(
                fact.recalculationId(), fact.recalculationVersion(), fact.originalCalculationId(),
                fact.originalResultHash(), fact.replacementCalculationId(), fact.replacementResultHash(),
                fact.direction(), fact.amount(), fact.currency(), fact.inputHash(), fact.resultHash(),
                fact.calculatedAt(), fact.periods());
    }

    private MaintenanceRetroactiveBillingAdjustmentEvidence toEvidence(AdjustmentFact fact) {
        return new MaintenanceRetroactiveBillingAdjustmentEvidence(
                fact.batchId(), fact.status(), fact.postedCount(), fact.reviewCount(),
                fact.requestHash(), fact.resultHash(), fact.adjustedAt(), fact.periods());
    }

    private MaintenanceRetroactivePeriodRecalculationResult completedResult(
            String id,
            int version,
            String operationId,
            MaintenanceView view,
            MaintenanceRetroactiveProductRecalculationEvidence product,
            MaintenanceRetroactiveBillingAdjustmentEvidence billing) {
        MaintenanceRetroactivePeriodRecalculationStatus status = billing.requiresReview()
                ? MaintenanceRetroactivePeriodRecalculationStatus.REVIEW_REQUIRED
                : MaintenanceRetroactivePeriodRecalculationStatus.COMPLETED;
        return result(id, version, operationId, view, status, product, billing, null, null, billing.adjustedAt());
    }

    private MaintenanceRetroactivePeriodRecalculationResult failedResult(
            String id,
            int version,
            String operationId,
            MaintenanceView view,
            MaintenanceRetroactiveProductRecalculationEvidence product,
            String failureCode,
            String failureMessage) {
        return result(id, version, operationId, view,
                MaintenanceRetroactivePeriodRecalculationStatus.FAILED,
                product, null, failureCode, failureMessage, LocalDateTime.now());
    }

    private MaintenanceRetroactivePeriodRecalculationResult existingResult(MaintenanceView view) {
        return new MaintenanceRetroactivePeriodRecalculationResult(
                view.getRetroactivePeriodRecalculationId(), currentVersion(view),
                view.getRetroactivePeriodRecalculationOperationId(),
                view.getRetroactivePeriodRecalculationStatus(), view.getRetroactivePeriodAnalysisId(),
                value(view.getRetroactivePeriodAnalysisVersion()), view.getRetroactivePeriodAnalysisResultHash(),
                view.getRetroactiveProductRecalculationId(), view.getRetroactiveProductDirection(),
                view.getRetroactiveProductAmount(), view.getRetroactiveProductCurrency(),
                value(view.getRetroactivePeriodCount()), view.getRetroactiveBillingBatchId(),
                view.getRetroactiveBillingStatus(), value(view.getRetroactiveBillingPostedCount()),
                value(view.getRetroactiveBillingReviewCount()), view.getRetroactivePeriodFailureCode(),
                view.getRetroactivePeriodFailureMessage(), view.getRetroactivePeriodCompletedAt());
    }

    private MaintenanceRetroactivePeriodRecalculationResult result(
            String id,
            int version,
            String operationId,
            MaintenanceView view,
            MaintenanceRetroactivePeriodRecalculationStatus status,
            MaintenanceRetroactiveProductRecalculationEvidence product,
            MaintenanceRetroactiveBillingAdjustmentEvidence billing,
            String failureCode,
            String failureMessage,
            LocalDateTime completedAt) {
        return new MaintenanceRetroactivePeriodRecalculationResult(
                id, version, operationId, status, view.getRetroactiveImpactAnalysisId(),
                view.getRetroactiveImpactAnalysisVersion(), view.getRetroactiveImpactResultHash(),
                product == null ? null : product.recalculationId(),
                product == null ? null : product.direction(), product == null ? null : product.amount(),
                product == null ? null : product.currency(), product == null ? 0 : product.periods().size(),
                billing == null ? null : billing.batchId(), billing == null ? null : billing.status(),
                billing == null ? 0 : billing.postedCount(), billing == null ? 0 : billing.reviewCount(),
                failureCode, failureMessage, completedAt);
    }

    private void requireReady(MaintenanceView view) {
        if (view.getEffectiveTimeType() != EffectiveTimeType.RETROACTIVE
                || view.getRetroactiveImpactStatus() != MaintenanceRetroactiveImpactAnalysisStatus.COMPLETED
                || !hasText(view.getRetroactiveImpactAnalysisId())
                || view.getRetroactiveImpactAnalysisVersion() == null
                || !hasText(view.getRetroactiveImpactResultHash())
                || view.getRetroactiveImpactScopeFrom() == null || view.getRetroactiveImpactScopeTo() == null
                || !hasText(view.getPolicyId()) || !hasText(view.getCustomerId())
                || view.isPremiumCalculationCheckpointConflict()
                || !hasText(view.getOriginalCalculationId()) || !hasText(view.getReplacementCalculationId())) {
            throw validation("case", "追溯影响分析或费用计算检查点未完成");
        }
    }

    private void validate(MaintenanceRetroactivePeriodRecalculationInput input) {
        if (input == null || !hasText(input.maintenanceId()) || !hasText(input.operationId())
                || !hasText(input.operatorId()) || !hasText(input.tenantId())) {
            throw validation("input", "案件、操作、操作员和租户标识不能为空");
        }
    }

    private boolean sameOperation(MaintenanceView view, String operationId) {
        return operationId.equals(view.getRetroactivePeriodRecalculationOperationId())
                && hasText(view.getRetroactivePeriodRecalculationId());
    }

    private boolean terminal(MaintenanceRetroactivePeriodRecalculationStatus status) {
        return status == MaintenanceRetroactivePeriodRecalculationStatus.COMPLETED
                || status == MaintenanceRetroactivePeriodRecalculationStatus.REVIEW_REQUIRED;
    }

    private int currentVersion(MaintenanceView view) {
        return value(view.getRetroactivePeriodRecalculationVersion());
    }

    private int value(Integer number) {
        return number == null ? 0 : number;
    }

    private String recalculationId(MaintenanceRetroactivePeriodRecalculationInput input) {
        return "rpr-" + digest(input).substring(0, 32);
    }

    private String requestId(String prefix, MaintenanceRetroactivePeriodRecalculationInput input) {
        return prefix + '-' + digest(input).substring(0, 32);
    }

    private String digest(MaintenanceRetroactivePeriodRecalculationInput input) {
        return MaintenanceRetroactiveImpactSourcePort.itemHash(
                input.tenantId(), input.maintenanceId(), input.operationId());
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        String safe = message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
        return safe.length() <= 500 ? safe : safe.substring(0, 500);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private MaintenanceValidationException validation(String field, String message) {
        return new MaintenanceValidationException(
                "MaintenanceRetroactivePeriodRecalculationApplicationService", field, message);
    }
}
