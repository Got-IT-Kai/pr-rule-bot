package com.code.context.domain.model;

public enum CollectionStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    SKIPPED,  // Diff validation passed but review not needed (e.g., binary files, rename-only)
    FAILED
}
