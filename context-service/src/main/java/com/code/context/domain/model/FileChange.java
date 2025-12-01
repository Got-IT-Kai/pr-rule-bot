package com.code.context.domain.model;

public record FileChange(
        String filename,
        String status,
        int additions,
        int deletions,
        String patch
) {
}