package com.code.platform.idempotency;

public interface IdempotencyStore {

    boolean tryStart(String eventId);

    void markProcessed(String eventId);
}
