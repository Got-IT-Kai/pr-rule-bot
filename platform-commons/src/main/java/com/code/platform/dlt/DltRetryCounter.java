package com.code.platform.dlt;

public interface DltRetryCounter {

    int incrementAndGet(String eventId);

    void reset(String eventId);
}