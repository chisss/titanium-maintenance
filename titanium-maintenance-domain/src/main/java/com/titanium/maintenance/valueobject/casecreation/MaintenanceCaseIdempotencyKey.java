package com.titanium.maintenance.valueobject.casecreation;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.valueobject.MaintenanceId;

/** 租户和受理来源隔离的保全建案幂等键。 */
public record MaintenanceCaseIdempotencyKey(
        String tenantId,
        MaintenanceChannel source,
        String clientRequestKey) {

    private static final int MAX_REQUEST_KEY_LENGTH = 128;
    private static final String ID_NAMESPACE = "titanium:maintenance:case:v2";

    public MaintenanceCaseIdempotencyKey {
        if (tenantId == null || tenantId.isBlank()) {
            throw new MaintenanceValidationException(
                    "MaintenanceCaseIdempotencyKey", "tenantId", "租户标识不能为空");
        }
        if (source == null) {
            throw new MaintenanceValidationException(
                    "MaintenanceCaseIdempotencyKey", "source", "保全受理来源不能为空");
        }
        if (clientRequestKey == null || clientRequestKey.isBlank()) {
            throw new MaintenanceValidationException(
                    "MaintenanceCaseIdempotencyKey", "clientRequestKey", "客户端请求键不能为空");
        }
        tenantId = tenantId.trim();
        clientRequestKey = clientRequestKey.trim();
        if (clientRequestKey.length() > MAX_REQUEST_KEY_LENGTH) {
            throw new MaintenanceValidationException(
                    "MaintenanceCaseIdempotencyKey", "clientRequestKey", "客户端请求键不能超过128个字符");
        }
    }

    /** 从幂等范围稳定派生案件标识，原始请求键不会出现在标识中。 */
    public MaintenanceId maintenanceId() {
        MessageDigest digest = sha256();
        update(digest, ID_NAMESPACE);
        update(digest, tenantId);
        update(digest, source.getCode());
        update(digest, clientRequestKey);
        byte[] hash = digest.digest();
        hash[6] = (byte) ((hash[6] & 0x0f) | 0x50);
        hash[8] = (byte) ((hash[8] & 0x3f) | 0x80);
        ByteBuffer buffer = ByteBuffer.wrap(hash);
        return MaintenanceId.of(new UUID(buffer.getLong(), buffer.getLong()).toString());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持SHA-256", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }
}
