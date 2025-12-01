package com.code.platform.correlation;

import java.util.UUID;

public final class CorrelationId {

    private CorrelationId() {
    }

    public static String generate() {
        return UUID.randomUUID().toString();
    }

    public static boolean isValid(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            return false;
        }

        try {
            UUID.fromString(correlationId);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
