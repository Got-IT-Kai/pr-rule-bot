package com.code.agent.application.port.out;

public interface EventBusPort {
    void publishEvent(Object event);
}
