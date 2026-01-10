package com.code.review.domain.model;

import java.util.Objects;

public record FileReview(
        String filename,
        String review
) {
    public FileReview {
        filename = Objects.requireNonNullElse(filename, "unknown-file");
        review = Objects.requireNonNullElse(review, "");
    }

    public String toContextualReview() {
        return "[FILE: %s]\n%s".formatted(filename, review);
    }
}
