package com.code.events.webhook;

public enum WebhookAction {
    OPENED,
    SYNCHRONIZE,
    REOPENED,
    CLOSED,
    EDITED;

    public boolean triggersReview() {
        return this == OPENED || this == SYNCHRONIZE || this == REOPENED;
    }
}
