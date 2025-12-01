package com.code.review.domain.model;

public record FileChange(
        String filename,
        String status,
        int additions,
        int deletions,
        String patch
) {
    public int totalChanges() {
        return additions + deletions;
    }

    public boolean isAdded() {
        return "added".equals(status);
    }

    public boolean isModified() {
        return "modified".equals(status);
    }

    public boolean isDeleted() {
        return "removed".equals(status);
    }
}