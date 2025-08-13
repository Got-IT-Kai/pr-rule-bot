package com.code.testinfra;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import reactor.blockhound.BlockHound;

import java.util.concurrent.atomic.AtomicBoolean;

public class BlockHoundExtension implements BeforeAllCallback {
    private static final AtomicBoolean installed = new AtomicBoolean(false);

    @Override
    public void beforeAll(ExtensionContext context) {
        boolean isDisabled = "false".equals(System.getProperty("blockhound.enabled"));

        if (!isDisabled && installed.compareAndSet(false, true)) {
            BlockHound.install();
        }
    }
}