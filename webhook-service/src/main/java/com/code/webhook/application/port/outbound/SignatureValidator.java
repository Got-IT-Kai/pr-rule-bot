package com.code.webhook.application.port.outbound;

import com.code.webhook.domain.model.WebhookValidationResult;

public interface SignatureValidator {

    WebhookValidationResult validate(byte[] payload, String signature, String secret);
}
