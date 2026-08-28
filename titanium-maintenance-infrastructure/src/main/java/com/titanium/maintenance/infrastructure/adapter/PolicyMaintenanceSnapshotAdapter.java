package com.titanium.maintenance.infrastructure.adapter;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.titanium.maintenance.common.enums.PolicyMaintenanceSnapshotFailureReason;
import com.titanium.maintenance.common.enums.change.PolicyFieldDataType;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.common.exception.PolicyMaintenanceSnapshotException;
import com.titanium.maintenance.infrastructure.client.PolicyServiceClient;
import com.titanium.maintenance.port.PolicyMaintenanceSnapshotPort;
import com.titanium.maintenance.valueobject.CustomerId;
import com.titanium.maintenance.valueobject.PolicyId;
import com.titanium.maintenance.valueobject.casecreation.PolicyMaintenanceSnapshot;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldValue;
import com.titanium.maintenance.valueobject.change.MaintenanceSnapshotReference;
import com.titanium.metadata.enums.BaseEnum;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.response.PolicyMaintenanceSnapshotResponse;
import com.titanium.policy.api.response.PolicySnapshotFieldValueResponse;

import feign.FeignException;
import lombok.RequiredArgsConstructor;

/** 通过 Policy 正式 API 冻结保全建案基准的真实基础设施适配器。 */
@Component
@RequiredArgsConstructor
public class PolicyMaintenanceSnapshotAdapter implements PolicyMaintenanceSnapshotPort {

    private static final int MAX_CAPTURE_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MILLIS = 100L;

    private static final Pattern FIELD_CODE = Pattern.compile("[a-z][A-Za-z0-9]*(\\.[a-z][A-Za-z0-9]*)+");
    private static final Pattern OBJECT_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    private final PolicyServiceClient policyServiceClient;

    @Override
    public PolicyMaintenanceSnapshot capture(PolicyMaintenanceSnapshotRequest request) {
        PolicyMaintenanceSnapshotException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_CAPTURE_ATTEMPTS; attempt++) {
            try {
                return captureOnce(request);
            } catch (PolicyMaintenanceSnapshotException exception) {
                lastFailure = exception;
                if (exception.getReason() != PolicyMaintenanceSnapshotFailureReason.UNAVAILABLE
                        || attempt == MAX_CAPTURE_ATTEMPTS) {
                    throw exception;
                }
                waitBeforeRetry(attempt);
            }
        }
        throw lastFailure;
    }

    private PolicyMaintenanceSnapshot captureOnce(PolicyMaintenanceSnapshotRequest request) {
        try {
            ApiResponse<PolicyMaintenanceSnapshotResponse> response = policyServiceClient
                    .getMaintenanceSnapshot(request.policyId(), request.tenantId());
            if (response == null) {
                throw failure(PolicyMaintenanceSnapshotFailureReason.UNAVAILABLE, "Policy建案快照服务未返回响应");
            }
            if (!response.isSuccess()) {
                throw responseFailure(response.getCode());
            }
            return toSnapshot(request, response.getData());
        } catch (PolicyMaintenanceSnapshotException exception) {
            throw exception;
        } catch (FeignException.NotFound exception) {
            throw failure(PolicyMaintenanceSnapshotFailureReason.NOT_FOUND, "Policy建案快照不存在", exception);
        } catch (FeignException exception) {
            throw failure(PolicyMaintenanceSnapshotFailureReason.UNAVAILABLE, "Policy建案快照服务调用失败", exception);
        } catch (MaintenanceValidationException | IllegalArgumentException exception) {
            throw failure(PolicyMaintenanceSnapshotFailureReason.CONTRACT_INVALID,
                    "Policy建案快照契约校验失败", exception);
        } catch (RuntimeException exception) {
            throw failure(PolicyMaintenanceSnapshotFailureReason.UNAVAILABLE,
                    "Policy建案快照服务不可用", exception);
        }
    }

    private void waitBeforeRetry(int attempt) {
        try {
            Thread.sleep(RETRY_DELAY_MILLIS * attempt);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure(PolicyMaintenanceSnapshotFailureReason.UNAVAILABLE,
                    "Policy建案快照重试被中断", exception);
        }
    }

    private PolicyMaintenanceSnapshot toSnapshot(
            PolicyMaintenanceSnapshotRequest request,
            PolicyMaintenanceSnapshotResponse response) {
        if (response == null) {
            throw failure(PolicyMaintenanceSnapshotFailureReason.CONTRACT_INVALID, "Policy建案快照响应体为空");
        }
        if (!Objects.equals(request.tenantId(), response.tenantId())) {
            throw failure(PolicyMaintenanceSnapshotFailureReason.TENANT_MISMATCH, "Policy建案快照租户回显不一致");
        }
        if (!Objects.equals(request.policyId(), response.policyId())) {
            throw failure(PolicyMaintenanceSnapshotFailureReason.CONTRACT_INVALID, "Policy建案快照保单标识回显不一致");
        }
        requireVersions(response);
        MaintenanceSnapshotReference reference = new MaintenanceSnapshotReference(
                response.snapshotStorageKey(), response.snapshotContentHash(), response.policyVersion(),
                response.capturedAt());
        return new PolicyMaintenanceSnapshot(
                response.tenantId(), new PolicyId(response.policyId()), response.policyNumber(),
                new CustomerId(response.customerId()), response.productId(), response.productVersion(),
                response.planVersion(), response.policyStatus(), response.policyVersion(),
                response.businessEffectiveAt(), response.nextBillingDateAt(), response.nextPolicyAnniversaryAt(),
                reference, fieldValues(response.fieldValues()));
    }

    private void requireVersions(PolicyMaintenanceSnapshotResponse response) {
        if (response.policyVersion() == null || response.policyVersion() < 0
                || !hasText(response.productVersion()) || !hasText(response.planVersion())) {
            throw failure(PolicyMaintenanceSnapshotFailureReason.VERSION_MISSING,
                    "Policy建案快照缺少业务基准、产品或定价计划版本");
        }
    }

    private PolicyMaintenanceSnapshotException responseFailure(String errorCode) {
        if (errorCode != null && errorCode.contains("VERSION_MISSING")) {
            return failure(PolicyMaintenanceSnapshotFailureReason.VERSION_MISSING, "Policy建案快照版本缺失");
        }
        if (errorCode != null && errorCode.contains("CONTRACT_INVALID")) {
            return failure(PolicyMaintenanceSnapshotFailureReason.CONTRACT_INVALID, "Policy建案快照契约无效");
        }
        if (errorCode != null && (errorCode.contains("NOT_EXIST") || errorCode.contains("NOT_FOUND"))) {
            return failure(PolicyMaintenanceSnapshotFailureReason.NOT_FOUND, "Policy建案快照不存在");
        }
        return failure(PolicyMaintenanceSnapshotFailureReason.UNAVAILABLE, "Policy建案快照服务返回失败");
    }

    private Map<String, MaintenanceFieldValue> fieldValues(
            Map<String, PolicySnapshotFieldValueResponse> remoteFields) {
        if (remoteFields == null || remoteFields.isEmpty()) {
            throw failure(PolicyMaintenanceSnapshotFailureReason.CONTRACT_INVALID,
                    "Policy建案快照缺少结构化字段值");
        }
        TreeMap<String, MaintenanceFieldValue> fields = new TreeMap<>();
        remoteFields.forEach((remoteKey, fieldValue) -> {
            String fieldCode = logicalFieldCode(remoteKey, fieldValue);
            if (fieldCode == null || !FIELD_CODE.matcher(fieldCode).matches() || fieldValue == null) {
                throw failure(PolicyMaintenanceSnapshotFailureReason.CONTRACT_INVALID,
                        "Policy建案快照字段编码或字段值无效");
            }
            if (!hasText(fieldValue.dataType())) {
                throw failure(PolicyMaintenanceSnapshotFailureReason.CONTRACT_INVALID,
                        "Policy建案快照字段类型不能为空");
            }
            PolicyFieldDataType dataType = BaseEnum.fromCode(PolicyFieldDataType.class, fieldValue.dataType());
            if (dataType == null) {
                throw failure(PolicyMaintenanceSnapshotFailureReason.CONTRACT_INVALID,
                        "Policy建案快照字段类型不受支持: " + fieldValue.dataType());
            }
            String snapshotKey = snapshotKey(fieldCode, fieldValue.objectId());
            if (fields.putIfAbsent(
                    snapshotKey, new MaintenanceFieldValue(dataType, fieldValue.canonicalValue())) != null) {
                throw failure(PolicyMaintenanceSnapshotFailureReason.CONTRACT_INVALID,
                        "Policy建案快照包含重复字段对象: " + snapshotKey);
            }
        });
        return Map.copyOf(fields);
    }

    private String logicalFieldCode(String remoteKey, PolicySnapshotFieldValueResponse fieldValue) {
        if (remoteKey == null || fieldValue == null || fieldValue.objectId() == null) {
            return remoteKey;
        }
        String prefix = fieldValue.objectId().trim() + ":";
        return remoteKey.startsWith(prefix) ? remoteKey.substring(prefix.length()) : remoteKey;
    }

    private String snapshotKey(String fieldCode, String objectId) {
        if (objectId == null) {
            return fieldCode;
        }
        String normalizedObjectId = objectId.trim();
        if (!OBJECT_ID.matcher(normalizedObjectId).matches()) {
            throw failure(PolicyMaintenanceSnapshotFailureReason.CONTRACT_INVALID,
                    "Policy建案快照集合字段对象标识无效");
        }
        return normalizedObjectId + ":" + fieldCode;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private PolicyMaintenanceSnapshotException failure(
            PolicyMaintenanceSnapshotFailureReason reason,
            String message) {
        return new PolicyMaintenanceSnapshotException(reason, message);
    }

    private PolicyMaintenanceSnapshotException failure(
            PolicyMaintenanceSnapshotFailureReason reason,
            String message,
            Throwable cause) {
        return new PolicyMaintenanceSnapshotException(reason, message, cause);
    }
}
